package com.QSE.models;
import java.util.Set;

public class EntityExtractionPayload implements java.io.Serializable{
    public Set<String> entities;
    public String filename;

    public EntityExtractionPayload(Set<String> entities, String filename) {
        this.entities = entities;
        this.filename = filename;
    }

}
