package com.QSE.models;

public class EntityConstraintsExtractionReturnValue implements java.io.Serializable{
    public String CTPFilename, shapeTripletSupportFilename;

    public EntityConstraintsExtractionReturnValue() {}

    public EntityConstraintsExtractionReturnValue(String CTPFilename, String shapeTripletSupportFilename) {
        this.CTPFilename = CTPFilename;
        this.shapeTripletSupportFilename = shapeTripletSupportFilename;
    }

    public String getCTPFilename() {
        return CTPFilename;
    }

    public void setCTPFilename(String CTPFilename) {
        this.CTPFilename = CTPFilename;
    }

    public String getShapeTripletSupportFilename() {
        return shapeTripletSupportFilename;
    }

    public void setShapeTripletSupportFilename(String shapeTripletSupportFilename) {
        this.shapeTripletSupportFilename = shapeTripletSupportFilename;
    }

}
