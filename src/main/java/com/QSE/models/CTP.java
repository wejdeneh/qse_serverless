package com.QSE.models;
import java.io.Serializable;
import java.util.*;
import java.util.function.Function;


public class CTP implements Serializable{
    Map<Integer, Map<Integer, Set<Integer>>> CTPMap;

    public CTP(){
        CTPMap=new HashMap<>();
    }

    public CTP(Map<Integer, Map<Integer, Set<Integer>>> CTPMap){
        this.CTPMap=CTPMap;
    }

    public Map<Integer, Map<Integer, Set<Integer>>> getCTPMap(){
        return CTPMap;
    }

    public void setCTPMap(Map<Integer, Map<Integer, Set<Integer>>> CTPMap){
        this.CTPMap=CTPMap;
    }

    public boolean containsKey(Integer key){
        return CTPMap.containsKey(key);
    }

    public Set<Integer> keySet(){
        return CTPMap.keySet();
    }

    public Map<Integer, Set<Integer>> get(Integer key){
        return CTPMap.get(key);
    }

    public void put(Integer key, Map<Integer, Set<Integer>> value){
        CTPMap.put(key,value);
    }

    public Map<Integer, Set<Integer>> computeIfAbsent(Integer key, Function<? super Integer, ? extends Map<Integer, Set<Integer>>> mappingFunction){
        return CTPMap.computeIfAbsent(key, mappingFunction);
    }
    
    public Set<Map.Entry<Integer, Map<Integer, Set<Integer>>>> entrySet(){
        return CTPMap.entrySet();
    }

}
