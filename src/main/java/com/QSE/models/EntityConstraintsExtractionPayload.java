package com.QSE.models;

import java.util.*;

public class EntityConstraintsExtractionPayload implements java.io.Serializable{
    public String filename;
    public List<String> entities;

    public EntityConstraintsExtractionPayload(String filename, List<String> entities) {
        this.filename = filename;
        this.entities = entities;
    }
}
