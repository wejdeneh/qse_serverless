package com.QSE.activityFunctions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.semanticweb.yars.nx.Literal;
import org.semanticweb.yars.nx.Node;
import org.semanticweb.yars.nx.parser.NxParser;
import org.semanticweb.yars.nx.parser.ParseException;

import com.QSE.Serialization.Serialize;
import com.QSE.Utils.*;
import com.QSE.encoders.StringEncoder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.specialized.BlobInputStream;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import com.QSE.models.*;;

public class EntityConstraintsExtraction {

    StringEncoder stringEncoder;
    ETD entityDataHashMap;
    CTP classToPropWithObjTypes = new CTP();
    ShapeTripletSupport shapeTripletSupport;
    @FunctionName("EntityConstraintsExtraction")
        public EntityConstraintsExtractionReturnValue entityConstraintsExtraction(@DurableActivityTrigger(name = "name") EntityConstraintsExtractionPayload payload, final ExecutionContext context) {
            //measure time
            Instant startTime = Instant.now();

            String filename = payload.filename;
            Set<String> entities = null;

            String connectStr=System.getenv("AzureWebJobsStorage");
            
            stringEncoder = new StringEncoder();
            Instant afterStringEncoderCreation = Instant.now();

            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectStr)
                .buildClient();
            BlobClient blobClient=blobServiceClient.getBlobContainerClient("filestorage").getBlobClient(filename);

            Instant afterClientFile = Instant.now();
            Duration timeParsing = Duration.ZERO;
            Duration timeExtractObject = Duration.ZERO;
            Duration timeLiteralTypeObject = Duration.ZERO;
            Duration timeUpdateClassToPropWithObjTypesMap = Duration.ZERO;

            try (BlobInputStream blobIS = blobClient.openInputStream()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(blobIS));
                List<String> lines;
                String line;
                //Split first line into a set (it has entities separated by comma)
                String line_entities=reader.readLine();
                entities = Set.of(line_entities.split(","));
                entityDataHashMap = new ETD(entities);
                //read the rest of the lines
                while ((lines=readNLines(reader, 1000)).size()>0){
                    List<Node[]> nodesList = lines.stream().map(line_node->{
                        try {
                            return NxParser.parseNodes(line_node);
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                        return null;
                    
                    }).toList();
                    List<Node> entityNodes = nodesList.stream().map(nodes -> nodes[0]).toList();
                    List<String> objectTypes = nodesList.stream().map(nodes -> extractObjectType(nodes[2].toString())).toList();
                    List<Integer> propIDs = stringEncoder.encode(nodesList.stream().map(nodes -> nodes[1].getLabel()).toList());

                    Instant beforeParsing = Instant.now();

                    entityDataHashMap.get(nodesList.stream().map(nodes -> nodes[2]).toList());

                    for (int i = 0; i < lines.size(); i++) {
                        //Declaring required sets
                        Set<Integer> objTypesIDs = new HashSet<>(10);
                        Set<Tuple2<Integer, Integer>> prop2objTypeTuples = new HashSet<>(10);
                        
                        // parsing <s,p,o> of triple from each line as node[0], node[1], and node[2]
                        Node[] nodes = nodesList.get(i);
                        Node entityNode = entityNodes.get(i);
                        String objectType = objectTypes.get(i);
                        int propID = propIDs.get(i);

                        
                        // object is an instance or entity of some class e.g., :Paris is an instance of :City & :Capital
                        if (objectType.equals("IRI")) {
                            objTypesIDs = parseIriTypeObject(objTypesIDs, prop2objTypeTuples, nodes, entityNode, propID);
                        }
                        // Object is of type literal, e.g., xsd:String, xsd:Integer, etc.
                        else {
                            objTypesIDs = parseLiteralTypeObject(objTypesIDs, entityNode, objectType, propID);
                        }
                        // for each type (class) of current entity -> append the property and object type in classToPropWithObjTypes HashMap
                        updateClassToPropWithObjTypesMap(objTypesIDs, entityNode, propID);
                    }
                }         
            }   catch (Exception e) {
                e.printStackTrace();
            }
            Instant afterParsing = Instant.now();
            shapeTripletSupport = new ShapeTripletSupport();
            //Compute support
            entityDataHashMap.forEach((entity, entityData) -> {
                Set<Integer> instanceClasses = entityDataHashMap.get(entity).getClassTypes();
                if (instanceClasses != null) {
                    for (Integer c : instanceClasses) {
                        for (Tuple2<Integer, Integer> propObjTuple : entityData.getPropertyConstraints()) {
                            Tuple3<Integer, Integer, Integer> tuple3 = new Tuple3<>(c, propObjTuple._1, propObjTuple._2);
                            SupportConfidence sc = this.shapeTripletSupport.get(tuple3);
                            if (sc == null) {
                                this.shapeTripletSupport.put(tuple3, new SupportConfidence(1));
                            } else {
                                //SupportConfidence sc = this.shapeTripletSupport.get(tuple3);
                                Integer newSupp = sc.getSupport() + 1;
                                sc.setSupport(newSupp);
                                this.shapeTripletSupport.put(tuple3, sc);
                            }
                        }
                    }
                }
            }, entities);
            Instant afterSupport = Instant.now();
            //print all the timings
            System.out.println("Time to create string encoder: " + (afterStringEncoderCreation.toEpochMilli() - startTime.toEpochMilli()) + " ms");
            System.out.println("Time to create blob client: " + (afterClientFile.toEpochMilli() - afterStringEncoderCreation.toEpochMilli()) + " ms");
            System.out.println("Time to parse: " + timeParsing.toMillis() + " ms");
            System.out.println("Time to extract object: " + timeExtractObject.toMillis() + " ms");
            System.out.println("Time to literal type object: " + timeLiteralTypeObject.toMillis() + " ms");
            System.out.println("Time to update class to prop with obj types map: " + timeUpdateClassToPropWithObjTypesMap.toMillis() + " ms");
            System.out.println("Time to compute support: " + (afterSupport.toEpochMilli() - afterParsing.toEpochMilli()) + " ms");
            // print stats of stringEncoder and entityDataHashMap
            stringEncoder.printStats();
            entityDataHashMap.printStats();
            //Serialize the 2 class attributes
            byte[] classToPropWithObjTypesByteArray = Serialize.serialize(classToPropWithObjTypes);
            String CTPFilename = "CTP/shard" + java.util.UUID.randomUUID() + ".ser";
            WriteBlobGivenName.writeBlobGivenName(CTPFilename, classToPropWithObjTypesByteArray);  
            byte[] shapeTripletSupportByteArray = Serialize.serialize(shapeTripletSupport);
            String shapeTripletSupportShardName = "shapeTripletSupport/shard" + java.util.UUID.randomUUID() + ".ser";
            WriteBlobGivenName.writeBlobGivenName(shapeTripletSupportShardName, shapeTripletSupportByteArray);  
            
            //entityDataHashMap.pushInRedis();
            //measure time
            Instant endTime = Instant.now();
            long duration = java.time.Duration.between(startTime, endTime).toMillis();
            System.out.println("Duration: " + duration + " ms");
            return new EntityConstraintsExtractionReturnValue(CTPFilename, shapeTripletSupportShardName);
        }

        //A utility method to extract the literal object type, returns String literal type : for example RDF.LANGSTRING, XSD.STRING, XSD.INTEGER, XSD.DATE, etc.
        protected String extractObjectType(String literalIri) {
            Literal theLiteral = new Literal(literalIri, true);
            String type = null;
            if (theLiteral.getDatatype() != null) {   // is literal type
                type = theLiteral.getDatatype().toString();
            } else if (theLiteral.getLanguageTag() != null) {  // is rdf:lang type
                type = "<" + RDF.LANGSTRING + ">"; //theLiteral.getLanguageTag(); will return the language tag
            } else {
                if (Utils.isValidIRI(literalIri)) {
                    if (SimpleValueFactory.getInstance().createIRI(literalIri).isIRI()) type = "IRI";
                } else {
                    type = "<" + XSD.STRING + ">";
                }
            }
            return type;
        }

        private Set<Integer> parseIriTypeObject(Set<Integer> objTypesIDs, Set<Tuple2<Integer, Integer>> prop2objTypeTuples, Node[] nodes, Node subject, int propID) {
            EntityData currEntityData = entityDataHashMap.get(nodes[2]);
            EntityData currEntityData2 = entityDataHashMap.getFromRedis(nodes[2]);
            if (currEntityData != null && currEntityData.getClassTypes().size() != 0) {
                objTypesIDs = currEntityData.getClassTypes();
                for (Integer node : objTypesIDs) { // get classes of node2
                    prop2objTypeTuples.add(new Tuple2<>(propID, node));
                }
                addEntityToPropertyConstraints(prop2objTypeTuples, subject);
            }
            /*else { // If we do not have data this is an unlabelled IRI objTypes = Collections.emptySet(); }*/
            else {
                int objID = stringEncoder.encode(Constants.OBJECT_UNDEFINED_TYPE);
                objTypesIDs.add(objID);
                prop2objTypeTuples = Collections.singleton(new Tuple2<>(propID, objID));
                addEntityToPropertyConstraints(prop2objTypeTuples, subject);
            }
            return objTypesIDs;
        }

        /**
         * A utility method to add property constraints of each entity in the 2nd phase
         *
         * @param prop2objTypeTuples : Tuples containing property and its object type, e.g., Tuple2<livesIn, :City>, Tuple2<livesIn, :Capital>
         * @param subject            : Subject entity such as :Paris
         */
        protected void addEntityToPropertyConstraints(Set<Tuple2<Integer, Integer>> prop2objTypeTuples, Node subject) {
            EntityData currentEntityData = entityDataHashMap.get(subject);
            if (currentEntityData == null) {
                currentEntityData = new EntityData();
            }
            //Add Property Constraint and Property cardinality
            for (Tuple2<Integer, Integer> tuple2 : prop2objTypeTuples) {
                currentEntityData.addPropertyConstraint(tuple2._1, tuple2._2);
            }
            //Add entity data into the map
            entityDataHashMap.put(subject, currentEntityData);
        }

        private Set<Integer> parseLiteralTypeObject(Set<Integer> objTypes, Node subject, String objectType, int propID) {
            Set<Tuple2<Integer, Integer>> prop2objTypeTuples;
            int objID = stringEncoder.encode(objectType);
            //objTypes = Collections.singleton(objID); Removed because the set throws an UnsupportedOperationException if modification operation (add) is performed on it later in the loop
            objTypes.add(objID);
            prop2objTypeTuples = Collections.singleton(new Tuple2<>(propID, objID));
            addEntityToPropertyConstraints(prop2objTypeTuples, subject);
            return objTypes;
        }
        
        private void updateClassToPropWithObjTypesMap(Set<Integer> objTypesIDs, Node entityNode, int propID) {
            EntityData entityData = entityDataHashMap.get(entityNode);
            if (entityData != null) {
                for (Integer entityTypeID : entityData.getClassTypes()) {
                    Map<Integer, Set<Integer>> propToObjTypes = classToPropWithObjTypes.computeIfAbsent(entityTypeID, k -> new HashMap<>());
                    Set<Integer> classObjTypes = propToObjTypes.computeIfAbsent(propID, k -> new HashSet<>());
                    classObjTypes.addAll(objTypesIDs);
                    propToObjTypes.put(propID, classObjTypes);
                    classToPropWithObjTypes.put(entityTypeID, propToObjTypes);
                }
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
