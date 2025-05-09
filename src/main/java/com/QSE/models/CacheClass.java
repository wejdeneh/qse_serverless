package com.QSE.models;



import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class CacheClass<T, V> {
    private final Cache<T, V> cache;
    public int hitCount = 0;
    public int missCount = 0;

    public CacheClass(int cacheSize) {
        this.cache = Caffeine.newBuilder()
                             .maximumSize(cacheSize)
                             .build();
    }

    public void put(T key, V value) {
        cache.put(key, value);
    }

    public V get(T key) {
        V ret = cache.getIfPresent(key);
        if (ret == null) {
            missCount++;
        } else {
            hitCount++;
        }
        return ret;
    }

    public boolean containsKey(T key) {
        return cache.getIfPresent(key) != null;
    }

    public void remove(T key) {
        cache.invalidate(key);
    }

    public int size() {
        return Math.toIntExact(cache.estimatedSize());
    }

    public void clear() {
        cache.invalidateAll();
    }

    public int getHitCount() {
        return hitCount;
    }

    public int getMissCount() {
        return missCount;
    }
}
