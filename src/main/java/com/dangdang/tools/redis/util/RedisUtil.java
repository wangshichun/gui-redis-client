package com.dangdang.tools.redis.util;

import redis.clients.jedis.*;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisMovedDataException;
import redis.clients.util.Hashing;

import java.util.*;

/**
 * Created by wangshichun on 2016/4/19.
 */
public class RedisUtil {
    private static ShardedJedis shardedJedis;
    private static JedisCluster jedisCluster;
    private static String jedisPassword = null;

    public static void init(List<JedisShardInfo> shardInfos, String password) {
        destroy();

        if (shardInfos.size() >= 1 && isJedisCluster(shardInfos.get(0).getHost(), shardInfos.get(0).getPort())) {
            jedisCluster = new JedisCluster(toHostAndPort(shardInfos));
        } else {
            shardedJedis = new ShardedJedis(shardInfos, Hashing.MURMUR_HASH);
        }

        if (null != password && password.trim().length() > 0) {
            jedisPassword = password;
        }
        testConnected();
    }

    public static boolean testConnected() {
        if (null != shardedJedis) {
            Boolean exists = getShard("test").exists("test");
            return exists != null;
        } else {
            return jedisCluster.exists("test") != null;
        }
    }
    private static boolean isJedisCluster(String host, int port) {
        Jedis jedis = new Jedis(host, port);
        try {
            System.out.println(jedis.clusterInfo());
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            jedis.close();
        }
    }
    private static Set<HostAndPort> toHostAndPort(List<JedisShardInfo> shardInfoList) {
        Set<HostAndPort> hostAndPortSet = new HashSet<HostAndPort>();
        for (JedisShardInfo shardInfo : shardInfoList) {
            hostAndPortSet.add(new HostAndPort(shardInfo.getHost(), shardInfo.getPort()));
        }
        return hostAndPortSet;
    }

    private static Jedis getShard(String key) {
        Jedis jedis = shardedJedis.getShard(key);
        if (jedisPassword != null) {
            jedis.auth(jedisPassword);
        }
        return jedis;
    }
    private static void destroy() {
        if (shardedJedis != null) {
            try {
                shardedJedis.close();
                shardedJedis.disconnect();
                shardedJedis = null;
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        if (jedisCluster != null) {
            try {
                jedisCluster.close();
                jedisCluster = null;
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        jedisPassword = null;
    }
    private static ShardedJedis getShardedJedis() {
        return shardedJedis;
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                destroy();
            }
        }));
    }


    // 扫描redis
    public static List<String> scan(String keyPattern, String shardStr, int db) {
        Jedis jedis;
        int shard = shardStr.isEmpty() ? 0 : Integer.valueOf(shardStr);

        if (jedisCluster != null) {
            List<String> clusterNodes = RedisUtil.clusterSlots2Instance(RedisUtil.clusterSlots(), true);
            if (shard >= clusterNodes.size()) {
                SwingUtil.alert("shard超过了总的shard数量");
                return new LinkedList<String>();
            }
            String[] arr = clusterNodes.get(shard).split(":");
            jedis = new Jedis(arr[0], Integer.parseInt(arr[1]), 3000);
        } else {
            Iterator<Jedis> iterator = getShardedJedis().getAllShards().iterator();
            jedis = iterator.next();
            int idx = 0;
            while (iterator.hasNext()) {
                idx++;
                if (idx == shard) {
                    jedis = iterator.next();
                    break;
                }
            }
            jedis.select(db);
            if (jedisPassword != null)
                jedis.auth(jedisPassword);
        }

        // 执行scan，如果有结果，则返回结果，否则，执行keys命令
//        ScanParams param = new ScanParams().match(keyPattern).count(2000);
//        List<String> scanResult = jedis.scan(ScanParams.SCAN_POINTER_START, param).getResult();
        List<String> scanResult = new LinkedList<String>();
//        if (scanResult.isEmpty() && !keyPattern.startsWith("*") && !keyPattern.startsWith("?")) {
            Set<byte[]> set = jedis.keys(keyPattern.getBytes());
            for (byte[] arr : set) {
                scanResult.add(new String(arr));
            }
//        }
        jedis.close();
        Collections.sort(scanResult);

        return scanResult;
    }

    // 删除key
    public static boolean del(String key, int db) {
        if (jedisCluster != null) {
            return jedisCluster.del(key) > 0;
        }
        Jedis jedis = getShard(key);
        jedis.select(db);
        long r = jedis.del(key.getBytes());
        jedis.close();
        return r > 0;
    }

    // set值
    public static String set(int db, String key, String value, String ttlValue, String exists) {
        if (jedisCluster != null) {
            String result = "";
            if (null == exists || exists.isEmpty()) {
                result = jedisCluster.set(key.getBytes(), value.getBytes());
            } else if (exists.toUpperCase().startsWith("NX")) {
                long r = jedisCluster.setnx(key.getBytes(), value.getBytes());
                result = r == 1 ? "OK" : "FAIL";
            } else if (exists.toUpperCase().startsWith("XX")) {
                if (null != ttlValue && !ttlValue.trim().isEmpty()) {
                    result = jedisCluster.setex(key.getBytes(), Integer.valueOf(ttlValue), value.getBytes());
                    return result;
                } else {
                    result = "setex命令必须有ttl";
                    return result;
                }
            }
            if (null != ttlValue && !ttlValue.trim().isEmpty()) {
                jedisCluster.expire(key.getBytes(), Integer.valueOf(ttlValue));
            }
            return result;
        } else {
            Jedis jedis = getShard(key);
            jedis.select(db);
            String result = "";
            if (null == exists || exists.isEmpty()) {
                result = jedis.set(key.getBytes(), value.getBytes());
            } else if (exists.toUpperCase().startsWith("NX")) {
                long r = jedis.setnx(key.getBytes(), value.getBytes());
                result = r == 1 ? "OK" : "FAIL";
            } else if (exists.toUpperCase().startsWith("XX")) {
                if (null != ttlValue && !ttlValue.trim().isEmpty()) {
                    result = jedis.setex(key.getBytes(), Integer.valueOf(ttlValue), value.getBytes());
                    return result;
                } else {
                    result = "setex命令必须有ttl";
                    return result;
                }
            }
            if (null != ttlValue && !ttlValue.trim().isEmpty()) {
                jedis.expire(key.getBytes(), Integer.valueOf(ttlValue));
            }
            jedis.close();
            return result;
        }
    }

    // get值
    public static byte[] get(int db, String key) {
        if (jedisCluster != null) {
            try {
                return jedisCluster.get(key.getBytes());
            } catch (JedisDataException e) {
                String value = jedisCluster.get(key);
                return value != null ? value.getBytes() : new byte[0];
            }
        }
        Jedis jedis = getShard(key);
        jedis.select(db);
        byte[] arr = jedis.get(key.getBytes());
        jedis.close();

        return arr;
    }
    // type值
    public static String type(int db, String key) {
        if (jedisCluster != null) {
            return jedisCluster.type(key);
        }
        Jedis jedis = getShard(key);
        jedis.select(db);
        String type = jedis.type(key.getBytes());
        jedis.close();

        return type;
    }
    public static Boolean exists(int db, String key) {
        if (jedisCluster != null) {
            return jedisCluster.exists(key);
        }
        Jedis jedis = getShard(key);
        jedis.select(db);
        Boolean exists = jedis.exists(key.getBytes());
        jedis.close();

        return exists;
    }
    public static Long ttl(int db, String key) {
        if (jedisCluster != null) {
            return jedisCluster.ttl(key);
        }
        Jedis jedis = getShard(key);
        jedis.select(db);
        Long exists = jedis.ttl(key.getBytes());
        jedis.close();

        return exists;
    }

    public static List<String> getAllShards() {
        List<String> list = new LinkedList<String>();
        if (!testConnected())
            return list;

        if (jedisCluster != null) {
            return RedisUtil.clusterSlots2Instance(RedisUtil.clusterSlots(), false);
        }
        Collection<Jedis> shardInfos = shardedJedis.getAllShards();
        for (Jedis jedis : shardInfos) {
            String host = jedis.getClient().getHost();
            int port = jedis.getClient().getPort();
            list.add(host + ":" + port);
        }
        return list;
    }

    public static String getShardsInfoString() {
        if (jedisCluster != null) {
            List<String> clusterNodes = RedisUtil.clusterSlots2Instance(RedisUtil.clusterSlots(), true);
            return new StringBuilder("此jedisCluster共有").append(clusterNodes.size()).append("个master: ").append(clusterNodes.toString()).toString();

//            Map<String, JedisPool> nodes = jedisCluster.getClusterNodes();
//            return new StringBuilder("此jedisCluster共有").append(nodes.size()).append("个node: ").append(nodes.keySet().toString()).toString();
        }
        Collection<Jedis> shardInfos = shardedJedis.getAllShards();
        StringBuilder stringBuilder = new StringBuilder("共有").append(shardInfos.size()).append("个shard, 当前redis: [");
        int len = stringBuilder.length();
        for (Jedis jedis : shardInfos) {
            if (stringBuilder.length() > len)
                stringBuilder.append(", ");
            stringBuilder.append("{'hostPort':'").append(jedis.getClient().getHost()).append(":").append(jedis.getClient().getPort())
                    .append("','db':'").append(jedis.getClient().getDB()).append("'}");
        }
        stringBuilder.append("]");
        return stringBuilder.toString();
    }

    public static Jedis getJedis(String hostPort, int db) {
        if (hostPort == null || hostPort.indexOf(":") < 1)
            return null;
        try {
            String[] arr = hostPort.split(":");
            Jedis jedis = new Jedis(arr[0].trim(), Integer.valueOf(arr[1]));
            try {
                jedis.exists("x");
            } catch (JedisDataException e) {
                if (e instanceof JedisMovedDataException) {
                    jedis.clusterInfo();
                    return jedis;
                }
                if (jedisPassword != null && e.getMessage().contains("NOAUTH"))
                    jedis.auth(jedisPassword);
            }
            jedis.select(db);
            return jedis;
        } catch (Throwable e) {
            return null;
        }
    }

    public static String clusterInfoString(Jedis jedis) {
        try {
            if (jedis == null) {
                if (jedisCluster != null) {
                    jedis = getJedis(jedisCluster.getClusterNodes().keySet().iterator().next(), 0);
                } else {
                    jedis = shardedJedis.getAllShards().iterator().next();
                }
            }
            return jedis.clusterInfo();
        } catch (Throwable e) {
            return "";
        }
    }

    public static String clusterNodesString(Jedis jedis) {
        try {
            if (jedis == null) {
                if (jedisCluster != null) {
                    jedis = getJedis(jedisCluster.getClusterNodes().keySet().iterator().next(), 0);
                } else {
                    jedis = shardedJedis.getAllShards().iterator().next();
                }
            }
            return jedis.clusterNodes();
        } catch (Throwable e) {
            return "";
        }
    }

    public static String clusterSlotsString(Jedis jedis) {
        try {
            if (jedis == null) {
                if (jedisCluster != null) {
                    jedis = getJedis(jedisCluster.getClusterNodes().keySet().iterator().next(), 0);
                } else {
                    jedis = shardedJedis.getAllShards().iterator().next();
                }
            }
            List<Object> slots = jedis.clusterSlots();
            return clusterSlots2String(slots);
        } catch (Throwable e) {
            return "";
        }
    }

    public static List<Object> clusterSlots() {
        Jedis jedis = null;
        try {
            if (shardedJedis != null) {
                jedis = shardedJedis.getAllShards().iterator().next();
            } else {
                String[] arr = jedisCluster.getClusterNodes().keySet().iterator().next().split(":");
                jedis = new Jedis(arr[0], Integer.valueOf(arr[1]));
            }
            return jedis.clusterSlots();
        } catch (Throwable e) {
            return new LinkedList<Object>();
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    public static List<String> clusterSlots2Instance(List<Object> slots, boolean onlyMaster) {
        List<String> nodes = new LinkedList<String>();
        for (Object obj : slots) {
            List<Object> slot = (List<Object>) obj;
            List<Object> address = (List<Object>) slot.get(2);
            if (address.size() == 2) {
                String host = new String((byte[]) address.get(0));
                nodes.add(host + ":" + address.get(1));
            }
            if (slot.size() > 3 && !onlyMaster) {
                address = (List<Object>) slot.get(3);
                if (address.size() % 2 == 0) {
                    for (int i = 0; i < address.size() / 2; i++) {
                        String host = new String((byte[]) address.get(i * 2));
                        nodes.add("    " + host + ":" + address.get(i * 2 + 1));
                    }
                }
            }
        }
        return nodes;
    }

    private static String clusterSlots2String(List<Object> slots) {
        StringBuilder builder = new StringBuilder();
        for (Object obj : slots) {
            List<Object> slot = (List<Object>) obj;
            builder.append(slot.get(0));
            builder.append("\t - \t");
            builder.append(slot.get(1));
            builder.append(": ");
            List<Object> address = (List<Object>) slot.get(2);
            if (address.size() == 2) {
                builder.append(" master ");
                builder.append(new String((byte[]) address.get(0)));
                builder.append(address.get(1));
            }
            if (slot.size() > 3) {
                address = (List<Object>) slot.get(3);
                if (address.size() % 2 == 0) {
                    builder.append(" slaves ");
                    for (int i = 0; i < address.size() / 2; i++) {
                        if (i != 0)
                            builder.append(", ");
                        builder.append(new String((byte[]) address.get(i * 2)));
                        builder.append(address.get(i * 2 + 1));
                    }
                }
            }
            builder.append("\n");
        }
        return builder.toString();
    }
}
