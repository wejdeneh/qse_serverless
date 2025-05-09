package com.QSE.models;

import java.io.Serializable;
import java.util.*;

//import kryo
import com.esotericsoftware.kryo.*;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

/**
 *
 */
public class EntityData implements Serializable{
    Set<Integer> classTypes; // O(T) number of types of this node
    public Map<Integer, PropertyData> propertyConstraintsMap; // Map from PropertyID -> PropertyData which consists of property's object types and count
    public EntityData() {
        this.classTypes = new HashSet<>();
        this.propertyConstraintsMap = new HashMap<>();
    }

    public void mergeClassTypes(Set<Integer> classTypes) {
        this.classTypes.addAll(classTypes);
    }

    public void addClassType(Integer classType) {
        this.classTypes.add(classType);
    }
    
    public Set<Integer> getClassTypes() {
        return classTypes;
    }

    //Implement toString
    public String toString() {
        return "EntityData{" +
                "classTypes=" + classTypes +
                ", propertyConstraintsMap=" + propertyConstraintsMap +
                '}';
    }

    // Called in StatsComputer.compute() method to compute support and confidence
    public Collection<Tuple2<Integer, Integer>> getPropertyConstraints() {
        List<Tuple2<Integer, Integer>> propertyConstraints = new ArrayList<>(this.propertyConstraintsMap.size() * 5 / 3);
        for (Map.Entry<Integer, PropertyData> pds : this.propertyConstraintsMap.entrySet()) {
            Tuple2<Integer, Integer> pcs;
            Integer propertyID = pds.getKey();
            PropertyData propertyData = pds.getValue();
            for (Integer classType : propertyData.objTypes) {
                pcs = new Tuple2<>(propertyID, classType);
                propertyConstraints.add(pcs);
            }
        }
        return propertyConstraints;
    }
    
    public void addPropertyConstraint(Integer propertyID, Integer classID) {
        PropertyData pd = this.propertyConstraintsMap.get(propertyID);
        //take the value from the map protobuf or create a new object
        if (pd == null) {
            pd = new PropertyData();
            this.propertyConstraintsMap.put(propertyID, pd);
        }
        pd.objTypes.add(classID);
    }
    
    public void addPropertyCardinality(Integer propertyID) {
        PropertyData pd = this.propertyConstraintsMap.get(propertyID);
        //take the value from the map protobuf or create a new object
        if (pd == null) {
            pd = new PropertyData();
            this.propertyConstraintsMap.put(propertyID, pd);
        }
        pd.count += 1;

    }
    
    /**
     * PropertyData Class
     */
    public static class PropertyData implements Serializable{
        Set<Integer> objTypes = new HashSet<>(5); // these are object types
        public int count = 0; // number of times I've seen this property for this node
    }
}
