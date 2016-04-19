package com.dangdang.tools.redis.event;

import com.google.gson.Gson;
import org.apache.zookeeper.ZooKeeper;
import redis.clients.jedis.JedisShardInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by wangshichun on 2015/12/8.
 */
public class ExtractRedisAddress {
    public List<JedisShardInfo> extract(String addr) {
        if (null == addr || addr.isEmpty())
            return null;
        boolean isZookeeperAddr = false;
        if (addr.startsWith("zookeeper://")) {
            addr = addr.replace("zookeeper://", "");
            isZookeeperAddr = true;
        }
        if (addr.contains(":2181") || addr.contains(":8181") || addr.contains("/")) {
            isZookeeperAddr = true;
        }
        if (isZookeeperAddr) {
            if (!addr.contains("/")) {
                addr = addr + "/redisCluster/promise/zkRedisClusterStatus";
            } else if (addr.endsWith("/redisCluster/promise")) {
                addr = addr + "/zkRedisClusterStatus";
            } else if (addr.endsWith("/redisCluster/order")) {
                addr = addr + "/zkRedisClusterStatus";
            }
        }
        List<Map> redisServers;
        if (isZookeeperAddr) {
            redisServers = transformToRedisAddr(addr);
        } else {
            redisServers = new ArrayList<Map>();
            Map<String, String> map = new HashMap<String, String>();
            map.put("name", "group0");
            map.put("master", addr);
            redisServers.add(map);
        }

        return toJedis(redisServers);
    }

    private List<Map> transformToRedisAddr(String addr) {
        // 解析Zookeeper地址
        String hostAndPort = addr.substring(0, addr.indexOf("/"));
        String path = addr.substring(addr.indexOf("/"));
        try {
            ZooKeeper zooKeeper = new ZooKeeper(hostAndPort, 5000, null);
            byte[] arr = zooKeeper.getData(path, false, null);
            String json = new String(arr);
            System.out.println(json);
            Gson gson = new Gson();
            HashMap map = gson.fromJson(json, HashMap.class);
            List<Map> groups = (List<Map>) map.get("groups");
            zooKeeper.close();
            return groups;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

//        return new ArrayList<Map>();
    }

    private List<JedisShardInfo> toJedis(List<Map> groups) {
        List<JedisShardInfo> jedisShardInfoList = new ArrayList<JedisShardInfo>();
        for (Map map : groups) {
            String[] arr = map.get("master").toString().split(":");
            if (arr.length != 2)
                continue;
            String name = map.get("name").toString();
            jedisShardInfoList.add(new JedisShardInfo(arr[0], Integer.valueOf(arr[1]), name));
        }
        if (jedisShardInfoList.isEmpty())
            throw new RuntimeException("没有解析到redis地址");

        return jedisShardInfoList;
    }
}
