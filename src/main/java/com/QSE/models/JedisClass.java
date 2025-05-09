package com.QSE.models;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.*;

import java.io.Serializable;

import com.QSE.Serialization.Serialize;
import java.util.List;
import java.util.function.Function;

import org.apache.jena.sparql.function.library.print;

import redis.clients.jedis.DefaultJedisClientConfig;

public class JedisClass {
    boolean runLocal=System.getenv("REDISCACHEHOSTNAME") == null;
    boolean useSsl = true;
    JedisPool jedisPool = null;
    int callsCounter=0;
    String luaScript =
    "if redis.call('HEXISTS', KEYS[2],ARGV[1]) == 0 then\n" +
    "    local i = redis.call('INCR', KEYS[1])\n" +
    "    redis.call('HSET', KEYS[2], ARGV[1], i)\n" +
    "    redis.call('HSET', KEYS[3], i, ARGV[1])\n" +
    "    return i\n" +
    "else\n" +
    "    return redis.call('HGET', KEYS[2], ARGV[1])\n" +
    "end";

    
    byte[] hashNameETD = "ETD".getBytes();
    String counterKeyName = "counter";
    String idToValName = "StringEncoderTable";
    String valToIdName = "StringEncoderRev"; 


    public JedisClass() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10); // Maximum number of connections
        poolConfig.setMaxIdle(5); // Maximum number of idle connections
        poolConfig.setMinIdle(1); // Minimum number of idle connections
        poolConfig.setTestOnBorrow(true); // Test connection before borrowing it
            
        if (!runLocal) {
            String cacheHostname = System.getenv("REDISCACHEHOSTNAME");
            String cachekey = System.getenv("REDISCACHEKEY");
            jedisPool = new JedisPool(poolConfig, cacheHostname, 6380, 5000, cachekey, useSsl);
        } else {
            jedisPool = new JedisPool(poolConfig, "redis", 6379, 5000);
        }        
    }

    public List<byte[]> getValuesETD(List<byte[]> keys){
        return runOperation((Jedis jedis)->{
            //implement the jedis pipeline to get values from redis
            Pipeline pipeline = jedis.pipelined();
            for (int i = 0; i < keys.size(); i++){
                pipeline.hget(hashNameETD, keys.get(i));
            }
            List<Object> result = pipeline.syncAndReturnAll();
            List<byte[]> values = result.stream().map(x -> (byte[]) x).toList();
            return values;
        }, jedisPool, callsCounter);
    }

    public void setValuesETD(List<byte[]> keys, List<byte[]> values){
        runOperation((Jedis jedis)->{
            //implement the jedis pipeline to set values in redis
            Pipeline pipeline = jedis.pipelined();
            if (keys.size() != values.size()){
                throw new IllegalArgumentException("Keys and values must have the same size");
            }
            for (int i = 0; i < keys.size(); i++){
                pipeline.hset(hashNameETD, keys.get(i), values.get(i));
            }
            pipeline.sync();
            return true;
        }, jedisPool, callsCounter);
    }

    public byte[] getValueETD(byte[] key){
        return runOperation((Jedis jedis)->{
            //implement the jedis pipeline to get a value from redis
            Pipeline pipeline = jedis.pipelined();
            pipeline.hget(hashNameETD, key);
            List<Object> result = pipeline.syncAndReturnAll();
            byte[] value = (byte[]) result.get(0);
            return value;
        }, jedisPool, callsCounter);
    }

    public void setValueETD(byte[] key, byte[] value){
        //implement the jedis pipeline to set a value in redis
        runOperation((Jedis jedis)->{
            Pipeline pipeline = jedis.pipelined();
            pipeline.hset(hashNameETD, key, value);
            pipeline.sync();
            return true;}, jedisPool, callsCounter);
    }

    public boolean containsKeyETD(byte[] key){
        //implement the jedis pipeline to check if a key exists in redis
        return runOperation((Jedis jedis)->{
            Pipeline pipeline = jedis.pipelined();
            pipeline.hexists(hashNameETD, key);
            List<Object> result = pipeline.syncAndReturnAll();
            return (boolean) result.get(0);
        }, jedisPool, callsCounter);
    }

    public String decode(Integer key){
        String value = runOperation((Jedis jedis)->{
            return jedis.hget(idToValName, key.toString());
        }, jedisPool, callsCounter);
        return value;
    }

    public List<String> decodeValues(List<Integer> keys){
        List<Object> result = runOperation((Jedis jedis)->{
            Pipeline pipeline = jedis.pipelined();
            for (int i = 0; i < keys.size(); i++){
                pipeline.hget(idToValName, keys.get(i).toString());
            }
            List<Object> results = pipeline.syncAndReturnAll();
            return results;
        }, jedisPool, callsCounter);
    
        // Cast the result to List<Object> before calling stream()
        List<String> values = ((List<Object>) result).stream().map(x -> x.toString()).toList();
        return values;
    }

    public Integer evaluateValue(String key){
        Object result = runOperation((Jedis jedis)->{
            String sha = jedis.scriptLoad(luaScript);
            Object results= jedis.evalsha(sha, 3, counterKeyName, valToIdName, idToValName, key);
            //it can't be null
            if (results == null){
                throw new IllegalArgumentException("Error in encoding");
            }
            return results;
        }, jedisPool, callsCounter);
        Integer id = Integer.parseInt(result.toString());
        return id;
    }

    public List<Integer> evaluateValues(List<String> keys){
        List<Object> result = runOperation((Jedis jedis) -> {
            Pipeline pipeline = jedis.pipelined();
            String sha = jedis.scriptLoad(luaScript);
            for (String key : keys) {
                pipeline.evalsha(sha, 3, counterKeyName, valToIdName, idToValName, key);
            }
            return pipeline.syncAndReturnAll();
        }, jedisPool, callsCounter);
    
        List<Integer> values = result.stream().map(obj -> Integer.parseInt(obj.toString())).toList();
        return values;
    }
    
    public boolean isEncoded(String val) {
        while (true) {
            try {
                return runOperation(jedis -> jedis.hexists(valToIdName, val),jedisPool, callsCounter);
            } catch (Exception e) {
                System.out.println("Error in encoding");
            }
        }
    }

    public void clean() {
        runOperation((Jedis jedis) -> {
            jedis.flushAll();
            return true;
        }, jedisPool, callsCounter);
    }

    public int getCallsCounter(){
        return callsCounter;
    }


    @FunctionalInterface
    public interface JedisOperation<T> {
        T run(Jedis jedis);
    }


    public static <T> T runOperation(JedisOperation<T> operation, JedisPool jedisPool, int callsCounter) {
        Jedis jedis=null;
        callsCounter++;
        while (true) {
            try {
                jedis=jedisPool.getResource();
                T result = operation.run(jedis);
                return result;
            } catch (Exception e) {
                // if it's a broken pipe reset jedis
                e.printStackTrace();
            } finally {
                if (jedis != null) {
                    jedis.close();
                }
            }
        }
    }
}
