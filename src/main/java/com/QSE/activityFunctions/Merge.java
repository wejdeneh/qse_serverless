package com.QSE.activityFunctions;

import com.QSE.Serialization.Serialize;
import com.QSE.models.CEC;
import com.QSE.models.CTP;
import com.QSE.models.ShapeTripletSupport;
import com.QSE.models.SupportConfidence;
import com.QSE.models.Tuple3;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import java.util.*;
public class Merge {
        
        @FunctionName("mergeSupport")
        public String mergeSupport(@DurableActivityTrigger(name = "supportTableStrings") List<String> supportTableStrings, final ExecutionContext context) {
            ShapeTripletSupport mergedSupport = new ShapeTripletSupport();
            for(String i:supportTableStrings){
                byte[] byteArray = ReadBlobGivenName.readBlobGivenName(i);
                ShapeTripletSupport supportMap=(ShapeTripletSupport) Serialize.deserialize(byteArray);
                for(Tuple3<Integer, Integer, Integer> tuple:supportMap.keySet()){
                    if(mergedSupport.containsKey(tuple)){
                        //we have to merge the SupportConfidence
                        SupportConfidence mergedSupportConfidence = mergedSupport.get(tuple);
                        SupportConfidence currentSupportConfidence = supportMap.get(tuple);
                        //merge support and confidence
                        mergedSupportConfidence.mergeSupport(currentSupportConfidence);
                        mergedSupport.put(tuple,mergedSupportConfidence);
                    }else{
                        //No conflict -> add the new tuple to the mergedSupport
                        mergedSupport.put(tuple,supportMap.get(tuple));
                    }
                }
            }
            //Serialize mergedSupport and write to blob storage
            String shardName = "mergedSupport/shapeTripletSupport" + ".ser";
            byte[] serializedData = Serialize.serialize(mergedSupport);
            // write it down also in a readable format for debug purposes
            String data="";
            for (Tuple3<Integer, Integer, Integer> tuple : mergedSupport.keySet()) {
                data+="Support: " + tuple + " -> "+mergedSupport.get(tuple)+"\n";
            }
            WriteBlobGivenName.writeBlobGivenName(shardName, serializedData);
            WriteBlobGivenName.writeBlobGivenName(shardName+".txt", data);
            return shardName;
        }

        @FunctionName("mergeCTP")
        public String mergeCTP(@DurableActivityTrigger(name = "supportTableStrings") List<String> CTPStrings, final ExecutionContext context) {
            CTP mergedCTP = new CTP();
            for(String CTPName:CTPStrings){
                byte[] byteArray = ReadBlobGivenName.readBlobGivenName(CTPName);
                CTP CTPMap=(CTP)Serialize.deserialize(byteArray);
                for(Integer i:CTPMap.keySet()){
                    if(mergedCTP.containsKey(i)){
                        //we have to merge the CTP
                        Map<Integer, Set<Integer>> mergedCTPMap = mergedCTP.get(i);
                        Map<Integer, Set<Integer>> currentCTPMap = CTPMap.get(i);
                        //merge the CTP
                        for(Integer j:currentCTPMap.keySet()){
                            if(mergedCTPMap.containsKey(j)){
                                //we have to merge the Set<Integer>
                                Set<Integer> mergedSet = mergedCTPMap.get(j);
                                Set<Integer> currentSet = currentCTPMap.get(j);
                                //merge the Set<Integer>
                                mergedSet.addAll(currentSet);
                                mergedCTPMap.put(j,mergedSet);
                            }else{
                                //No conflict -> add the new Set<Integer> to the mergedCTPMap
                                mergedCTPMap.put(j,currentCTPMap.get(j));
                            }
                        }
                    }else{
                        //No conflict -> add the new Map<Integer, Set<Integer>> to the mergedCTP
                        mergedCTP.put(i,CTPMap.get(i));
                    }
                }
            }
            //Serialize mergedCTP and write to blob storage
            String shardName = "mergedCTP/CTP" + ".ser";
            byte[] serializedData = Serialize.serialize(mergedCTP);
            // write it down also in a readable format for debug purposes
            String data="";
            for (Integer i : mergedCTP.keySet()) {
                data+="CTP: " + i + " -> \n";
                for (Integer j : mergedCTP.get(i).keySet()) {
                    data+="\t"+j+" -> "+mergedCTP.get(i).get(j)+"\n";
                }
            }
            WriteBlobGivenName.writeBlobGivenName(shardName, serializedData);
            WriteBlobGivenName.writeBlobGivenName(shardName+".txt", data);
            return shardName;
        }

        @FunctionName("mergeCEC")
        public String mergeCEC(@DurableActivityTrigger(name = "supportTableStrings") List<String> CECStrings, final ExecutionContext context) {
            CEC mergedCEC = new CEC();
            for(String CECName:CECStrings){
                byte[] byteArray = ReadBlobGivenName.readBlobGivenName(CECName);
                CEC CECMap=(CEC) Serialize.deserialize(byteArray);
                for(Integer i:CECMap.keySet()){
                    if(mergedCEC.containsKey(i)){
                        //we have to merge the CEC
                        Integer mergedValue = mergedCEC.get(i);
                        Integer currentValue = CECMap.get(i);
                        //merge the CEC
                        mergedValue+=currentValue;
                        mergedCEC.put(i,mergedValue);
                    }else{
                        //No conflict -> add the new Integer to the mergedCEC
                        mergedCEC.put(i,CECMap.get(i));
                    }
                }
            }
            //Serialize mergedCEC and write to blob storage
            String shardName = "mergedCEC/CEC" + ".ser";
            byte[] serializedData = Serialize.serialize(mergedCEC);
            // write it down in readable
            String data="";
            for (Integer i : mergedCEC.keySet()) {
                data+="CEC: " + i + " -> "+mergedCEC.get(i)+"\n";
            }
            WriteBlobGivenName.writeBlobGivenName(shardName+".txt", data);
            WriteBlobGivenName.writeBlobGivenName(shardName, serializedData);
            return shardName;
        }

}
