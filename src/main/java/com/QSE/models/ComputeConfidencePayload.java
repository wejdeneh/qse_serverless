package com.QSE.models;

import java.util.*;

public class ComputeConfidencePayload implements java.io.Serializable{
    public String shapeTripletSupportFile;
    public String entityExtractionFile;

    public ComputeConfidencePayload(String shapeTripletSupportFile, String classEntityCountFile) {
        this.shapeTripletSupportFile = shapeTripletSupportFile;
        this.entityExtractionFile = entityExtractionFile;
    }
}
