package com.QSE.models;

public class ShapesExtractionPayload {

    public String SUPPShardName, CECShardName, CTPShardName;

    public ShapesExtractionPayload(String SUPPShardName, String CECShardName, String CTPShardName) {
        this.SUPPShardName = SUPPShardName;
        this.CECShardName = CECShardName;
        this.CTPShardName = CTPShardName;
    }

}
