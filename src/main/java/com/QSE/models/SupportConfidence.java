package com.QSE.models;

import java.io.Serializable;

/**
 * This class is called Support Confidence (SuppConf) Computer Class, used to get or set the support/confidence of shape
 * constraints
 */
public class SupportConfidence implements Serializable{
    Integer support = 0;
    Double confidence = 0D;
    
    public SupportConfidence() {}
    
    public SupportConfidence(Integer support) {
        this.support = support;
    }
    
    public SupportConfidence(Integer s, Double c) {
        this.support = s;
        this.confidence = c;
    }
    
    public Integer getSupport() {
        return support;
    }
    
    public Double getConfidence() {
        return confidence;
    }
    
    public void setSupport(Integer support) {
        this.support = support;
    }
    
    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public void mergeSupport(SupportConfidence sc) {
        this.support += sc.getSupport();
    }

    public String toString() {
        return "Support: " + support + " Confidence: " + confidence;
    }
    
}
