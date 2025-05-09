package com.QSE.activityFunctions;

import com.microsoft.azure.functions.annotation.*;
import com.QSE.Serialization.Serialize;
import com.QSE.models.CEC;
import com.QSE.models.ComputeConfidencePayload;
import com.QSE.models.ShapeTripletSupport;
import com.QSE.models.SupportConfidence;
import com.QSE.models.Tuple3;
import com.microsoft.azure.functions.*;
import java.util.*;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;

public class ComputeConfidence {
        /**
         * Computes the confidence of the shape triplets
         */
        @FunctionName("computeConfidence")
        public String computeConfidence(
                @DurableActivityTrigger(name = "name") ComputeConfidencePayload payload,
                final ExecutionContext context) {
                        
                //Deserialize the payload
                String shapeTripletSupportFile = payload.shapeTripletSupportFile;
                String entityExtractionFile = payload.entityExtractionFile;
                byte[] shapeTripletSupportBytes=ReadBlobGivenName.readBlobGivenName(shapeTripletSupportFile);
                ShapeTripletSupport shapeTripletSupport = (ShapeTripletSupport)Serialize.deserialize(shapeTripletSupportBytes);
                byte[] classEntityCountBytes=ReadBlobGivenName.readBlobGivenName(entityExtractionFile);
                CEC classEntityCount = (CEC)Serialize.deserialize(classEntityCountBytes);
                for (Map.Entry<Tuple3<Integer, Integer, Integer>, SupportConfidence> entry : shapeTripletSupport.entrySet()) {
                        SupportConfidence value = entry.getValue();
                        double confidence = (double) value.getSupport() / classEntityCount.get(entry.getKey()._1);
                        value.setConfidence(confidence);
                }
                //Serialize the result
                byte[] result = Serialize.serialize(shapeTripletSupport);
                WriteBlobGivenName.writeBlobGivenName(shapeTripletSupportFile, result);
                return shapeTripletSupportFile;
        }
}
