package com.QSE.encoders;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

import com.QSE.models.JedisClass;
import java.util.*;
import com.QSE.models.CacheClass;

/**
 * This class encodes the String values into Integers and also provides decode functionality
 */
public class StringEncoder implements Serializable {
    CacheClass<String, Integer> valueToIdMap;
    CacheClass<Integer, String> idToValueMap;
    JedisClass jedisClass = new JedisClass();
    //Implement singleton pattern
    private static StringEncoder instance = null;
    Integer cacheSize=10000;

    public StringEncoder() {
        this.valueToIdMap = new CacheClass<>(cacheSize);
        this.idToValueMap = new CacheClass<>(cacheSize);
    }
    
    public Integer encode(String val) {  
        if(this.valueToIdMap.containsKey(val) && this.valueToIdMap.get(val)!=null && this.idToValueMap.containsKey(this.valueToIdMap.get(val))) {
            //Integer result = jedisClass.evaluateValue(val);
            //Integer result1 = this.valueToIdMap.get(val);
            /*if (!(result.equals(result1))){
                System.out.println("differs: "+result+" "+result1);
            }*/
            return this.valueToIdMap.get(val);
        }
        //load script
        Integer result = jedisClass.evaluateValue(val);
        if(result ==null){
            System.out.println("Error in encoding");
            return null;
        }
        Integer id = Integer.parseInt(result.toString());
        this.valueToIdMap.put(val, id);
        this.idToValueMap.put(id, val);
        return id;
    }

    public List<Integer> encode(List<String> val) {  
        if(val.size()==0) {
            return null;
        }
        List<Pair<Integer,Integer>> ids = new ArrayList<Pair<Integer,Integer>>();
        List<Pair<Integer,String>> toEncode = new ArrayList<Pair<Integer,String>>();
        for(int i=0;i<val.size();i++) {
            if(this.valueToIdMap.containsKey(val.get(i))) {
                ids.add(Pair.of(i,this.valueToIdMap.get(val.get(i))));
            }else{
                toEncode.add(Pair.of(i,val.get(i)));
            }
        }
        // execute the script in pipeline for the others
        List<String> keys= toEncode.stream().map(Pair::getRight).collect(Collectors.toList());
        List<Integer> results=jedisClass.evaluateValues(keys);
        for(int i=0;i<results.size();i++) {
            Integer result = results.get(i);
            if(result ==null){
                System.out.println("Error in encoding");
                ids.add(Pair.of(toEncode.get(i).getLeft(),null));
                continue;
            }
            Integer id = result;
            this.valueToIdMap.put(toEncode.get(i).getRight(), id);
            this.idToValueMap.put(id, toEncode.get(i).getRight());
            ids.add(Pair.of(toEncode.get(i).getLeft(),id));
        }
        //sort the ids
        ids.sort(Comparator.comparing(Pair::getLeft));
        if (val.size() != ids.size()) {
            System.out.println("Size mismatch between decoded and ids");
        }
        return ids.stream().map(Pair::getRight).collect(Collectors.toList());
    }



    public String decode(int id) {
        if(this.idToValueMap.containsKey(id) && this.valueToIdMap.containsKey(this.idToValueMap.get(id))) {
            System.out.println(id+" "+this.idToValueMap.get(id));
            return this.idToValueMap.get(id);
        }
        String val= jedisClass.decode(id);
        if(val==null) {
            System.out.println("Error in decoding");
            return null;
        }
        this.valueToIdMap.put(val, id);
        this.idToValueMap.put(id, val);
        return val;
    }

    public List<String> decode(List<Integer> ids) {
        List<Pair<Integer,Integer>> toDecode = new ArrayList<Pair<Integer,Integer>>();
        List<Pair<Integer,String>> decoded = new ArrayList<Pair<Integer,String>>();
        for(int i=0;i<ids.size();i++) {
            if(this.idToValueMap.containsKey(ids.get(i))) {
                decoded.add(Pair.of(i,this.idToValueMap.get(ids.get(i))));
            }else{
                toDecode.add(Pair.of(i,ids.get(i)));
            }
        }
        // execute the script in pipeline for the others
        List<Integer> keys= toDecode.stream().map(Pair::getRight).collect(Collectors.toList());
        List<String> results=jedisClass.decodeValues(keys);
        for(int i=0;i<results.size();i++) {
            String val = results.get(i);
            if(val==null) {
                System.out.println("Error in decoding");
                decoded.add(Pair.of(toDecode.get(i).getLeft(),null));
            }
            this.valueToIdMap.put(val, toDecode.get(i).getRight());
            this.idToValueMap.put(toDecode.get(i).getRight(), val);
            decoded.add(Pair.of(toDecode.get(i).getLeft(),val));
        }
        //sort the ids
        decoded.sort(Comparator.comparing(Pair::getLeft));
        // check the size of the vectors, they should be always the same, given N key there should be N values
        if (decoded.size() != ids.size()) {
            System.out.println("Size mismatch between decoded and ids");
        }
        return decoded.stream().map(Pair::getRight).collect(Collectors.toList());
    }
    

    
    public boolean isEncoded(String val) {
        return jedisClass.isEncoded(val);
    }

    public void printStats() {
        //compute the sum of hit and miss of the 2 caches
        int hitCount = this.valueToIdMap.getHitCount() + this.idToValueMap.getHitCount();
        int missCount = this.valueToIdMap.getMissCount() + this.idToValueMap.getMissCount();
        System.out.println("StringEncoder cache hitCount: "+hitCount);
        System.out.println("StringEncoder cache missCount: "+missCount);
        //now jedis calls
        System.out.println("Jedis calls: "+jedisClass.getCallsCounter());
    }
}
