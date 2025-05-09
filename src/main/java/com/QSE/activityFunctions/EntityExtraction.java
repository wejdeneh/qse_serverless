package com.QSE.activityFunctions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;

import java.util.List;
import java.util.stream.Collectors;

import org.semanticweb.yars.nx.Node;
import org.semanticweb.yars.nx.parser.NxParser;
import org.semanticweb.yars.nx.parser.ParseException;

import com.QSE.Serialization.Serialize;
import com.QSE.encoders.StringEncoder;
import com.QSE.models.CEC;
import com.QSE.models.ETD;
import com.QSE.models.EntityData;
import com.QSE.models.EntityExtractionPayload;
import com.QSE.models.EntityExtractionReturnValue;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.specialized.BlobInputStream;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;

public class EntityExtraction {
    String connectStr=System.getenv("AzureWebJobsStorage");
    String typePredicate="<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>";
    /* entityExtraction phase, given an list of entities taken from the partitionByEntities function it computes a local ETD table */
    @FunctionName("EntityExtraction")
        public EntityExtractionReturnValue entityExtraction(@DurableActivityTrigger(name = "payload") EntityExtractionPayload payload, final ExecutionContext context) {
            String filename = payload.filename;
            Instant startTime = Instant.now();
            ETD entityDataHashMap=new ETD();
            CEC classEntityCount=new CEC(); // Map from classID to number of entities of that class
            StringEncoder stringEncoder = new StringEncoder();

            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectStr)
                .buildClient();
            BlobClient blobClient=blobServiceClient.getBlobContainerClient("filestorage").getBlobClient(filename);
            
            Instant readBlobTime = Instant.now();
            try (BlobInputStream blobIS = blobClient.openInputStream()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(blobIS));
                List<String> lines;
                //if the first line is the entities
                reader.readLine();
                while ((lines = EntityExtraction.readNLines(reader, 1000)).size() > 0){
                    List<Node[]> nodes=lines.stream().map(line -> {
                        try {
                            return NxParser.parseNodes(line);
                        } catch (ParseException e) {
                            e.printStackTrace();
                            return null;
                        }
                    }).collect(Collectors.toList());
                    nodes=nodes.stream().filter(node-> (node[1].toString().equals(typePredicate))).collect(Collectors.toList());
                    List<Node> subjects=nodes.stream().map(node -> node[0]).collect(Collectors.toList());
                    List<String> objects=nodes.stream().map(node -> node[2].getLabel()).collect(Collectors.toList());                 
                    List<Integer> objIDs=stringEncoder.encode(objects);
                    for (int i=0;i<subjects.size();i++){
                        EntityData entityData=entityDataHashMap.get(subjects.get(i));
                        if (entityData==null){
                            entityData=new EntityData();
                        }
                        entityData.addClassType(objIDs.get(i));
                        entityDataHashMap.put(subjects.get(i),entityData);
                        classEntityCount.merge(objIDs.get(i),1,Integer::sum);
                    }
                }                    
                reader.close();
                blobIS.close();

                Instant entityExtractionTime = Instant.now();

                String CECShardName = "classEntityCount/shard" + java.util.UUID.randomUUID() + ".ser";
                EntityExtractionReturnValue entityExtractionReturnValue = new EntityExtractionReturnValue(CECShardName);

                byte[] serializedCEC = Serialize.serialize(classEntityCount);
                WriteBlobGivenName.writeBlobGivenName(CECShardName, serializedCEC);

                //push in redis
                entityDataHashMap.pushInRedis();

                //printStats
                stringEncoder.printStats();
                entityDataHashMap.printStats();

                System.out.println("Read Blob Time: " + (readBlobTime.toEpochMilli() - startTime.toEpochMilli()) + " ms");
                System.out.println("Entity Extraction Time: " + (entityExtractionTime.toEpochMilli() - readBlobTime.toEpochMilli()) + " ms");
                
                return entityExtractionReturnValue;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }catch (ParseException e) {
                // Handle the exception (e.g., log the error or throw a custom exception)
                e.printStackTrace();
                return null;
            }
        }

        public static List<String> readNLines(BufferedReader reader, int n) throws IOException, ParseException{
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String line = reader.readLine();
                if (line == null) {
                    return lines;
                }
                lines.add(line);
            }
            return lines;
        }
}


