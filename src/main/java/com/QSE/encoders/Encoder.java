package com.QSE.encoders;

public interface Encoder {
    
    int encode(String val);
    
    public String decode(int val);
    
}
