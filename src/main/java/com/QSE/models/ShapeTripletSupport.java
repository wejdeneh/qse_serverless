package com.QSE.models;
import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;

public class ShapeTripletSupport implements Serializable {
    Map<Tuple3<Integer, Integer, Integer>, SupportConfidence> shapeTripletSupport;

    public ShapeTripletSupport(Map<Tuple3<Integer, Integer, Integer>, SupportConfidence> shapeTripletSupport) {
        this.shapeTripletSupport = shapeTripletSupport;
    }

    public ShapeTripletSupport() {
        this.shapeTripletSupport = new HashMap<>();
    }

    public Map<Tuple3<Integer, Integer, Integer>, SupportConfidence> getShapeTripletSupport() {
        return shapeTripletSupport;
    }

    public void setShapeTripletSupport(Map<Tuple3<Integer, Integer, Integer>, SupportConfidence> shapeTripletSupport) {
        this.shapeTripletSupport = shapeTripletSupport;
    }

    public void put(Tuple3<Integer, Integer, Integer> key, SupportConfidence value) {
        shapeTripletSupport.put(key, value);
    }

    public SupportConfidence get(Tuple3<Integer, Integer, Integer> key) {
        return shapeTripletSupport.get(key);
    }

    public boolean containsKey(Tuple3<Integer, Integer, Integer> key) {
        return shapeTripletSupport.containsKey(key);
    }

    public void remove(Tuple3<Integer, Integer, Integer> key) {
        shapeTripletSupport.remove(key);
    }

    public int size() {
        return shapeTripletSupport.size();
    }

    public boolean isEmpty() {
        return shapeTripletSupport.isEmpty();
    }

    public void clear() {
        shapeTripletSupport.clear();
    }

    public Set<Tuple3<Integer, Integer, Integer>> keySet() {
        return shapeTripletSupport.keySet();
    }

    public Set<Entry<Tuple3<Integer, Integer, Integer>, SupportConfidence>> entrySet() {
        return shapeTripletSupport.entrySet();
    }
}
