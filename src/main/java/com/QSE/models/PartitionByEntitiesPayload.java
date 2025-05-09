package com.QSE.models;
import java.util.Map;
public class PartitionByEntitiesPayload {
    public String entityCount;
    public Integer subsets;

    public PartitionByEntitiesPayload(String entityCount, Integer subsets) {
        this.entityCount = entityCount;
        this.subsets = subsets;
    }

    public String getEntityCount() {
        return entityCount;
    }

    public void setEntityCount(String entityCount) {
        this.entityCount = entityCount;
    }

    public Integer getSubsets() {
        return subsets;
    }

    public PartitionByEntitiesPayload() {
    }

    public void setSubsets(Integer subsets) {
        this.subsets = subsets;
    }
}
