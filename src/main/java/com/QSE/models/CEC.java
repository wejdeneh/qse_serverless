package com.QSE.models;
import java.io.Serializable;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public class CEC implements Serializable{
    Map<Integer, Integer> classEntityCount;

    public CEC(Map<Integer, Integer> classEntityCount) {
        this.classEntityCount = classEntityCount;
    }

    public CEC() {
        this.classEntityCount = new HashMap<>();
    }

    public Map<Integer, Integer> getClassEntityCount() {
        return classEntityCount;
    }

    public void setClassEntityCount(Map<Integer, Integer> classEntityCount) {
        this.classEntityCount = classEntityCount;
    }

    public void put(Integer key, Integer value) {
        classEntityCount.put(key, value);
    }

    public Integer get(Integer key) {
        return classEntityCount.get(key);
    }

    public boolean containsKey(Integer key) {
        return classEntityCount.containsKey(key);
    }

    public void remove(Integer key) {
        classEntityCount.remove(key);
    }

    public int size() {
        return classEntityCount.size();
    }

    public boolean isEmpty() {
        return classEntityCount.isEmpty();
    }

    public void merge(int key, int value, BiFunction<? super Integer, ? super Integer, ? extends Integer> function) {
        this.classEntityCount.merge(key, value, function);
    }

    public void forEach(BiConsumer<? super Integer, ? super Integer> action) {
        this.classEntityCount.forEach(action);
    }

    public Set<Integer> keySet() {
        return classEntityCount.keySet();
    }
}
