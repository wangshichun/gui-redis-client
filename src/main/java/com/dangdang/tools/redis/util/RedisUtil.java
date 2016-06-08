package com.dangdang.tools.redis.util;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ShardedJedis;
import redis.clients.util.Hashing;

import java.util.*;

/**
 * Created by wangshichun on 2016/4/19.
 */
public class RedisUtil {
    private static ShardedJedis shardedJedis;
    private static String jedisPassword = null;

    public static void init(List<JedisShardInfo> shardInfos, String password) {
        destroy();

        shardedJedis = new ShardedJedis(shardInfos, Hashing.MURMUR_HASH);

        if (null != password && password.trim().length() > 0) {
            jedisPassword = password;
        }
        testConnected();
    }

    public static boolean testConnected() {
        if (null == shardedJedis)
            return false;

        Boolean exists = getShard("test").exists("test");
        return exists != null;
    }
    private static Jedis getShard(String key) {
        Jedis jedis = shardedJedis.getShard(key);
        if (jedisPassword != null) {
            jedis.auth(jedisPassword);
        }
        return jedis;
    }
    private static void destroy() {
        try {
            if (shardedJedis == null) {
                return;
            }
            shardedJedis.close();
            shardedJedis.disconnect();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        shardedJedis = null;
        jedisPassword = null;
    }
    public static ShardedJedis getShardedJedis() {
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
        // 确定是哪个DB
        int shard = shardStr.isEmpty() ? 0 : Integer.valueOf(shardStr);
        Iterator<Jedis> iterator = RedisUtil.getShardedJedis().getAllShards().iterator();
        Jedis jedis = iterator.next();
        int idx = 0;
        while (iterator.hasNext()) {
            idx ++;
            if (idx == shard) {
                jedis = iterator.next();
                break;
            }
        }
        jedis.select(db);
        if (jedisPassword != null)
            jedis.auth(jedisPassword);

        // 执行scan，如果有结果，则返回结果，否则，执行keys命令
        ScanParams param = new ScanParams().match(keyPattern).count(2000);
        List<String> scanResult = jedis.scan(ScanParams.SCAN_POINTER_START, param).getResult();
        if (scanResult.isEmpty() && !keyPattern.startsWith("*") && !keyPattern.startsWith("?")) {
            Set<byte[]> set = jedis.keys(keyPattern.getBytes());
            for (byte[] arr : set) {
                scanResult.add(new String(arr));
            }
        }
        jedis.close();
        Collections.sort(scanResult);

        return scanResult;
    }

    // 删除key
    public static boolean del(String key, int db) {
        Jedis jedis = getShard(key);
        jedis.select(db);
        long r = jedis.del(key.getBytes());
        jedis.close();
        return r > 0;
    }

    // set值
    public static String set(int db, String key, String value, String ttlValue, String exists) {
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

    // get值
    public static byte[] get(int db, String key) {
        Jedis jedis = getShard(key);
        jedis.select(db);
        byte[] arr = jedis.get(key.getBytes());
        jedis.close();

        return arr;
    }
    // type值
    public static String type(int db, String key) {
        Jedis jedis = getShard(key);
        jedis.select(db);
        String type = jedis.type(key.getBytes());
        jedis.close();

        return type;
    }
    public static Boolean exists(int db, String key) {
        Jedis jedis = getShard(key);
        jedis.select(db);
        Boolean exists = jedis.exists(key.getBytes());
        jedis.close();

        return exists;
    }
    public static Long ttl(int db, String key) {
        Jedis jedis = getShard(key);
        jedis.select(db);
        Long exists = jedis.ttl(key.getBytes());
        jedis.close();

        return exists;
    }

    public static String getShardsInfoString() {
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
}
