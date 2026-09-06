package com.gaurang.redis;

import java.util.concurrent.ConcurrentHashMap;

public class MiniRedis {
    private final ConcurrentHashMap<String, String> data = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> expiry = new ConcurrentHashMap<>();

    public void set(String key, String value) {
        data.put(key, value);
    }

    public String get(String key) {
        return data.get(key);
    }

    public boolean delete(String key) {
        return data.remove(key) != null;
    }

    public void expire(String key, long seconds){
        expiry.put(key, System.currentTimeMillis() + seconds * 1000);
    }

    public String increment(String key){
        if(!data.contains(key)){
            data.put(key, "1");
        }
        else{
            Integer temp = Integer.parseInt(data.get(key)) + 1;
            data.put(key, Integer.toString(temp));
        }
        return data.get(key);
    }

    public String ttl(String key){
        if(expiry.contains(key)){
            Long d = expiry.get(key);
            if(d < System.currentTimeMillis()){
                return "0 (expired)";
            }else{
                return Long.toString(d - System.currentTimeMillis());
            }
        }
        return "Infinite";
    }
}