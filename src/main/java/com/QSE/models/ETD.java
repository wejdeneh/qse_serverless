package com.QSE.models;
import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;

import com.QSE.Serialization.Serialize;
import com.QSE.activityFunctions.WriteBlobGivenName;
import com.QSE.encoders.StringEncoder;
import com.QSE.models.EntityData.PropertyData;
import com.azure.core.util.BinaryData;

import com.github.andrewoma.dexx.collection.Pair;
import org.semanticweb.yars.nx.Node;
import org.semanticweb.yars.nx.Resource;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;


import com.QSE.models.CacheClass;

public class ETD implements Serializable{
    int cacheSize = 10000;
    CacheClass<Node,EntityData> cacheReadOnly;
    Map<Node, EntityData> cacheWriteRead;
    String hashName = "ETD";
    Kryo kryo=initializeKryo();
    JedisClass jedisClass = new JedisClass();

    private Kryo initializeKryo(){
        //add a watch
        Instant time=Instant.now();
        Kryo kryo = new Kryo();
        kryo.register(EntityData.class);
        kryo.register(PropertyData.class);
        kryo.register(HashMap.class); // Register HashMap class
        kryo.register(HashSet.class); // Register ArrayList class
        kryo.register(ArrayList.class); // Register ArrayList class
        System.out.println("Kryo initialization time: "+(Instant.now().toEpochMilli()-time.toEpochMilli()));
        return kryo;
    }
    
    public ETD(Set<String> entities){
        Instant time=Instant.now();
        //convert it to set of Nodes
        this.cacheReadOnly = new CacheClass<>(cacheSize);
        this.cacheWriteRead = new HashMap<>(entities.size());
        List<Node> entityNodes = entities.stream().map(entity -> new Resource(entity)).collect(Collectors.toList());
        byte[] data = null;
        //get them from Redis
        List<byte[]> keys = entityNodes.stream().map(entity -> Serialize.serialize(entity)).collect(Collectors.toList());
        List<byte[]> results=jedisClass.getValuesETD(keys);
        for (int i = 0; i < entityNodes.size(); i++) {
            Node entityNode = entityNodes.get(i);
            data = (byte[])results.get(i);
            if (data != null) {
                cacheWriteRead.put(entityNode, kryo.readObject(new Input(data), EntityData.class));
            }
        }
        System.out.println("ETD initialization time: "+(Instant.now().toEpochMilli()-time.toEpochMilli()));
    }

    public ETD() {
        this.cacheReadOnly = new CacheClass<>(cacheSize);
        this.cacheWriteRead = new HashMap<>();
    }

    public void put(Node entity, EntityData entityData) {
        cacheWriteRead.put(entity, entityData);
    }

    public EntityData get(Node entity) {
        EntityData entityData = cacheWriteRead.get(entity);
        if (entityData == null) {
            entityData = cacheReadOnly.get(entity);
        }
        if (entityData == null) {
            entityData = getFromRedis(entity);
            if (entityData != null) {
                cacheReadOnly.put(entity, entityData);
            }
        }
        return entityData;
    }

    public EntityData getFromRedis(Node entity) {
        byte[] data = null;
        data = jedisClass.getValueETD(Serialize.serialize(entity));
        if (data == null) {
            return null;
        }
        EntityData entityData = kryo.readObject(new Input(data), EntityData.class);
        return entityData;
    }
    
    public List<EntityData> get(List<Node> entities) {
        // create pairs with index and Node
        List<Pair<Integer, EntityData>> entityDataPairs = new ArrayList<>();
        List<Pair<Integer, Node>> entityPairsFromRedis = new ArrayList<>();

        for (int i = 0; i < entities.size(); i++) {
            Node entity = entities.get(i);
            EntityData entityData = cacheWriteRead.get(entity);
            if (entityData == null) {
                entityData = cacheReadOnly.get(entity);
            }
            if (entityData == null) {
                entityPairsFromRedis.add(new Pair<>(i, entity));
            } else {
                entityDataPairs.add(new Pair<>(i, entityData));
            }
        }

        if (!entityPairsFromRedis.isEmpty()) {
            List<Node> entityNodesFromRedis = entityPairsFromRedis.stream().map(pair -> pair.component2()).collect(Collectors.toList());
            List<byte[]> keys = entityNodesFromRedis.stream().map(entity -> Serialize.serialize(entity)).collect(Collectors.toList());
            List<byte[]> results = jedisClass.getValuesETD(keys);
            for (int i = 0; i < entityNodesFromRedis.size(); i++) {
                Node entityNode = entityNodesFromRedis.get(i);
                byte[] data = results.get(i);
                if (data != null) {
                    EntityData entityData = kryo.readObject(new Input(data), EntityData.class);
                    entityDataPairs.add(new Pair<>(entityPairsFromRedis.get(i).component1(), entityData));
                    cacheReadOnly.put(entityNode, entityData);
                }
            }
        }

        // sort them by pair index and return them
        entityDataPairs.sort(Comparator.comparing(pair -> pair.component1()));

        return entityDataPairs.stream().map(pair -> pair.component2()).collect(Collectors.toList());
        
    }

    public void forEach(BiConsumer<Node, EntityData> action, Set<String> entities) {     
        Instant time=Instant.now();
        Set<Node> entityNodesSet = entities.stream().map(entity -> new Resource(entity)).collect(Collectors.toSet());
        List<Node> entityNodes = new ArrayList<>(entityNodesSet);
        List<EntityData> entityDatas = new ArrayList<>();
        for (Node entity : entityNodes) {
            entityDatas.add(get(entity));
        }
        for (int i = 0; i < entityNodes.size(); i++) {
            action.accept(entityNodes.get(i), entityDatas.get(i));
        }
        System.out.println("ETD forEach time: "+(Instant.now().toEpochMilli()-time.toEpochMilli()));
    }

    /*public Set<Node> keySet() {
        return entityDataHashMap.keySet();
    }*/

    public boolean containsKey(Node entity) {
        return jedisClass.containsKeyETD(Serialize.serialize(entity));
    }

    public void pushInRedis() {
        Instant time=Instant.now();
        cacheWriteRead=cacheWriteRead.entrySet().stream().filter(entry -> entry.getValue() != null).collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        List<byte[]> keys = cacheWriteRead.keySet().stream().map(entity -> Serialize.serialize(entity)).collect(Collectors.toList());
        List<byte[]> values = cacheWriteRead.values().stream().map(entityData -> {
            Output o=new Output(100,-1);
            kryo.writeObject(o, entityData);
            return o.toBytes();
        }).collect(Collectors.toList());
        jedisClass.setValuesETD(keys, values);
        System.out.println("ETD push time: "+(Instant.now().toEpochMilli()-time.toEpochMilli()));    
    }

    public void printStats() {
        System.out.println("ETD cacheReadOnly hitCount: "+cacheReadOnly.getHitCount());
        System.out.println("ETD cacheReadOnly missCount: "+cacheReadOnly.getMissCount());
        System.out.println("Jedis calls: "+jedisClass.callsCounter);
    }
}

