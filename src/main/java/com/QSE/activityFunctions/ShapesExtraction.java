package com.QSE.activityFunctions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.FileOutputStream;
import java.io.OutputStream;

import org.apache.jena.riot.RDFDataMgr;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.datatypes.XMLDatatypeUtil;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.ModelBuilder;
import org.eclipse.rdf4j.model.util.RDFCollections;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.SHACL;
import org.eclipse.rdf4j.model.vocabulary.XSD;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.Rio;

import static org.eclipse.rdf4j.model.util.Values.bnode;

import de.atextor.turtle.formatter.FormattingStyle;
import de.atextor.turtle.formatter.TurtleFormatter;


import com.QSE.Serialization.Serialize;
import com.QSE.Utils.Constants;
import com.QSE.Utils.Utils;
import com.QSE.encoders.StringEncoder;
import com.QSE.models.CEC;
import com.QSE.models.CTP;
import com.QSE.models.ShapeTripletSupport;
import com.QSE.models.ShapesExtractionPayload;
import com.QSE.models.SupportConfidence;
import com.QSE.models.Tuple3;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;

public class ShapesExtraction {

    ValueFactory factory = SimpleValueFactory.getInstance();
    StringEncoder encoder;
    //This is the equivalent of class entity count
    CEC classInstanceCount;
    String typePredicate = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    ShapeTripletSupport shapeTripletSupport;


    @FunctionName("shapesExtraction")
        public String shapesExtraction(@DurableActivityTrigger(name = "name") ShapesExtractionPayload payload, final ExecutionContext context) {
            String SUPPShardName = payload.SUPPShardName;
            String CECShardName = payload.CECShardName;
            String CTPShardName = payload.CTPShardName;            

            //Read the support confidence of shape triplets (at this moment it contains only information on the support, we still haven't computed the confidence)
            shapeTripletSupport = readShapeTripletSupportConfidence(SUPPShardName);
            //Read the class entity count map
            classInstanceCount = readClassEntityCount(CECShardName);

            //Compute Confidence
            for (Map.Entry<Tuple3<Integer, Integer, Integer>, SupportConfidence> entry : shapeTripletSupport.entrySet()) {
                SupportConfidence value = entry.getValue();
                double confidence = (double) value.getSupport() / classInstanceCount.get(entry.getKey()._1);
                value.setConfidence(confidence);
            }

            encoder = new StringEncoder();
            CTP classToPropWithObjTypes = readClassToPropWithObjTypes(CTPShardName);

            //This is the version that does not perform pruning
            Model m2 = null;
            ModelBuilder b = new ModelBuilder();
            for (Map.Entry<Integer, Map<Integer, Set<Integer>>> entry : classToPropWithObjTypes.entrySet()) {
                Integer encodedClassIRI = entry.getKey();
                Map<Integer, Set<Integer>> propToObjectType = entry.getValue();
                buildShapes(b, encodedClassIRI, propToObjectType);
            }
            m2 = b.build();
            //Get all the statements from the built model
            List<String> statements = new ArrayList<>();
            m2.forEach(st -> {
                statements.add(st.toString());
            });

            //This is the version the performs also pruning, uncomment the code below and comment the code above to enable it
            /*Double confidence = 0.25;
            Integer support = 1;
            Model m2 = null;
            ModelBuilder b2 = new ModelBuilder();
            for (Map.Entry<Integer, Map<Integer, Set<Integer>>> entry : classToPropWithObjTypes.entrySet()) {
                Integer encodedClassIRI = entry.getKey();
                Map<Integer, Set<Integer>> propToObjectType = entry.getValue();
                buildAndPruneShapes(confidence, support, b2, encodedClassIRI, propToObjectType);
            }
            m2 = b2.build();*/

            System.out.println("PRUNED SHAPES:");

            //Simple way to format the output shapes and write them to a file
            String path = "./output_shapes.ttl";
            try {
                FileWriter fileWriter = new FileWriter(path, false);
                Rio.write(m2, fileWriter, RDFFormat.TURTLE);
                TurtleFormatter formatter = new TurtleFormatter(FormattingStyle.DEFAULT);
                OutputStream out = new FileOutputStream("./formatted_shapes.ttl", false);
                // Build or load a Jena Model
                org.apache.jena.rdf.model.Model model = RDFDataMgr.loadModel(path);
                formatter.accept(model, out);
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println("Number of statements:" + statements.size());

            return statements.toString();
        }

        private void buildAndPruneShapes(Double confidence, Integer support, ModelBuilder b, Integer encodedClassIRI, Map<Integer, Set<Integer>> propToObjectType) {
            if (Utils.isValidIRI(encoder.decode(encodedClassIRI))) {
                IRI subj = factory.createIRI(encoder.decode(encodedClassIRI));
                int classId = encoder.encode(subj.stringValue());
                int classInstances = classInstanceCount.get(classId);
                
                //NODE SHAPES PRUNING based on support
                if (support == 1) {
                    if (classInstances >= support) {
                        prepareNodeAndPropertyShapes(confidence, support, b, encodedClassIRI, propToObjectType, subj);
                    }
                } else {
                    if (classInstances > support) {
                        prepareNodeAndPropertyShapes(confidence, support, b, encodedClassIRI, propToObjectType, subj);
                    }
                }
            } else {
                System.out.println("constructShapesWithPruning:: INVALID SUBJECT IRI: " + encoder.decode(encodedClassIRI));
            }
        }
        
        /**
         * QSE-Pruned sub-method
         */
        private void prepareNodeAndPropertyShapes(Double confidence, Integer support, ModelBuilder b, Integer encodedClassIRI, Map<Integer, Set<Integer>> propToObjectType, IRI subj) {
            //String nodeShape = "shape:" + subj.getLocalName() + "Shape";
            String nodeShape = Constants.SHAPES_NAMESPACE + subj.getLocalName() + "Shape";
            b.subject(nodeShape)
                    .add(RDF.TYPE, SHACL.NODE_SHAPE)
                    .add(SHACL.TARGET_CLASS, subj);
            //.add(SHACL.IGNORED_PROPERTIES, RDF.TYPE)
            //.add(SHACL.CLOSED, true);
            
            if (propToObjectType != null) {
                Map<Integer, Set<Integer>> propToObjectTypesLocalPositive = performPropShapePruningPositive(encodedClassIRI, propToObjectType, confidence, support);
                constructPropertyShapes(b, subj, encodedClassIRI, nodeShape, propToObjectTypesLocalPositive);// call this for positive only 
            }
        }

        private Map<Integer, Set<Integer>> performPropShapePruningPositive(Integer classEncodedLabel, Map<Integer, Set<Integer>> propToObjectType, Double confidence, Integer support) {
        Map<Integer, Set<Integer>> propToObjectTypesLocal = new HashMap<>();
        for (Map.Entry<Integer, Set<Integer>> entry : propToObjectType.entrySet()) {
            Integer prop = entry.getKey();
            Set<Integer> propObjectTypes = entry.getValue();
            HashSet<Integer> objTypesSet = new HashSet<>();
            
            //Make sure instant type (either rdf:type or wdt:P31 of WikiData) is not pruned
            IRI property = factory.createIRI(encoder.decode(prop));
            boolean isInstantTypeProperty = property.toString().equals(remAngBrackets(typePredicate));
            if (isInstantTypeProperty) {
                propToObjectTypesLocal.put(prop, objTypesSet);
            }
            
            //compute Relative Support if sampling is on
            double relativeSupport = 0;
            
            positivePruning(classEncodedLabel, confidence, support, prop, propObjectTypes, objTypesSet, relativeSupport);
            if (objTypesSet.size() != 0) {
                propToObjectTypesLocal.put(prop, objTypesSet);
            }
        }
        return propToObjectTypesLocal;
    }

    private void positivePruning(Integer classEncodedLabel, Double confidence, Integer support, Integer prop, Set<Integer> propObjectTypes, HashSet<Integer> objTypesSet, double relativeSupport) {
        for (Integer encodedObjectType : propObjectTypes) {
            Tuple3<Integer, Integer, Integer> tuple3 = new Tuple3<>(classEncodedLabel, prop, encodedObjectType);
            
            
            if (shapeTripletSupport.containsKey(tuple3)) {
                SupportConfidence sc = shapeTripletSupport.get(tuple3);
                
                if (support == 1) {
                    if (sc.getConfidence() > confidence && sc.getSupport() >= support) {
                        objTypesSet.add(encodedObjectType);
                    }
                }
                
                if (support != 1) {
                    //support = (int) relativeSupport;
                    if (sc.getConfidence() > confidence && sc.getSupport() > relativeSupport) {
                        objTypesSet.add(encodedObjectType);
                    }
                } else {
                    if (sc.getConfidence() > confidence && sc.getSupport() > support) {
                        objTypesSet.add(encodedObjectType);
                    }
                }
            }
        }
    }

        private void buildShapes(ModelBuilder b, Integer encodedClassIRI, Map<Integer, Set<Integer>> propToObjectType) {
            if (Utils.isValidIRI(encoder.decode(encodedClassIRI))) {
                IRI subj = factory.createIRI(encoder.decode(encodedClassIRI));
                String nodeShape = Constants.SHAPES_NAMESPACE + subj.getLocalName() + "Shape";
                b.subject(nodeShape)
                        .add(RDF.TYPE, SHACL.NODE_SHAPE)
                        .add(SHACL.TARGET_CLASS, subj);
                //.add(SHACL.IGNORED_PROPERTIES, RDF.TYPE)
                //.add(SHACL.CLOSED, true);
                //annotatingSupportConfidence
                b.subject(nodeShape).add(Constants.SUPPORT, classInstanceCount.get(encodedClassIRI));
                //b.subject(nodeShape).add(VOID.ENTITIES, classInstanceCount.get(encodedClassIRI));
            
                if (propToObjectType != null) {
                    constructPropertyShapes(b, subj, encodedClassIRI, nodeShape, propToObjectType); // Property Shapes
                }
            } else {
                System.out.println("constructShapeWithoutPruning::INVALID SUBJECT IRI: " + encoder.decode(encodedClassIRI));
            }
        }

        private void constructPropertyShapes(ModelBuilder b, IRI subj, Integer subjEncoded, String nodeShape, Map<Integer, Set<Integer>> propToObjectTypesLocal) {
            Map<String, Integer> propDuplicateDetector = new HashMap<>();
            
            propToObjectTypesLocal.forEach((prop, propObjectTypes) -> {
                ModelBuilder localBuilder = new ModelBuilder();
                IRI property = factory.createIRI(encoder.decode(prop));
                String localName = property.getLocalName();
                
                boolean isInstanceTypeProperty = property.toString().equals(remAngBrackets(typePredicate));
                if (isInstanceTypeProperty) {
                    localName = "instanceType";
                }
                
                if (propDuplicateDetector.containsKey(localName)) {
                    int freq = propDuplicateDetector.get(localName);
                    propDuplicateDetector.put(localName, freq + 1);
                    localName = localName + "_" + freq;
                }
                propDuplicateDetector.putIfAbsent(localName, 1);
                
                IRI propShape = factory.createIRI(Constants.SHAPES_NAMESPACE + localName + subj.getLocalName() + "ShapeProperty");
                
                b.subject(nodeShape)
                        .add(SHACL.PROPERTY, propShape);
                b.subject(propShape)
                        .add(RDF.TYPE, SHACL.PROPERTY_SHAPE)
                        .add(SHACL.PATH, property);
                
                if (isInstanceTypeProperty) {
                    Resource head = bnode();
                    List<Resource> members = Arrays.asList(new Resource[]{subj});
                    Model tempModel = RDFCollections.asRDF(members, head, new LinkedHashModel());
                    propObjectTypes.forEach(encodedObjectType -> {
                        Tuple3<Integer, Integer, Integer> tuple3 = new Tuple3<>(encoder.encode(subj.stringValue()), prop, encodedObjectType);
                        annotateWithSupportAndConfidence(propShape, localBuilder, tuple3);
                    });
                    tempModel.add(propShape, SHACL.IN, head);
                    b.build().addAll(tempModel);
                    b.build().addAll(localBuilder.build());
                }
                
                int numberOfObjectTypes = propObjectTypes.size();
                
                if (numberOfObjectTypes == 1 && !isInstanceTypeProperty) {
                    propObjectTypes.forEach(encodedObjectType -> {
                        Tuple3<Integer, Integer, Integer> tuple3 = new Tuple3<>(encoder.encode(subj.stringValue()), prop, encodedObjectType);
                        if (shapeTripletSupport.containsKey(tuple3)) {
                            if (shapeTripletSupport.get(tuple3).getSupport().equals(classInstanceCount.get(encoder.encode(subj.stringValue())))) {
                                b.subject(propShape).add(SHACL.MIN_COUNT, factory.createLiteral(XMLDatatypeUtil.parseInteger("1")));
                            }
                            /*if (Main.extractMaxCardConstraints) {
                                if (propWithClassesHavingMaxCountOne.containsKey(prop) && propWithClassesHavingMaxCountOne.get(prop).contains(subjEncoded)) {
                                    b.subject(propShape).add(SHACL.MAX_COUNT, factory.createLiteral(XMLDatatypeUtil.parseInteger("1")));
                                }
                            }*/
                        }
                        String objectType = encoder.decode(encodedObjectType);
                        if (objectType != null) {
                            if (objectType.contains(XSD.NAMESPACE) || objectType.contains(RDF.LANGSTRING.toString())) {
                                if (objectType.contains("<")) {objectType = objectType.replace("<", "").replace(">", "");}
                                IRI objectTypeIri = factory.createIRI(objectType);
                                b.subject(propShape).add(SHACL.DATATYPE, objectTypeIri);
                                b.subject(propShape).add(SHACL.NODE_KIND, SHACL.LITERAL);
                                annotateWithSupportAndConfidence(propShape, localBuilder, tuple3);
                            } else {
                                //objectType = objectType.replace("<", "").replace(">", "");
                                if (Utils.isValidIRI(objectType) && !objectType.equals(Constants.OBJECT_UNDEFINED_TYPE)) {
                                    IRI objectTypeIri = factory.createIRI(objectType);
                                    b.subject(propShape).add(SHACL.CLASS, objectTypeIri);
                                    b.subject(propShape).add(SHACL.NODE_KIND, SHACL.IRI);
                                    annotateWithSupportAndConfidence(propShape, localBuilder, tuple3);
                                } else {
                                    //IRI objectTypeIri = factory.createIRI(objectType);
                                    //b.subject(propShape).add(SHACL.CLASS, objectType);
                                    //System.out.println("INVALID Object Type IRI: " + objectType);
                                    b.subject(propShape).add(SHACL.NODE_KIND, SHACL.IRI);
                                    annotateWithSupportAndConfidence(propShape, localBuilder, tuple3);
                                    if (objectType.equals(Constants.OBJECT_UNDEFINED_TYPE))
                                        b.subject(propShape).add(SHACL.MIN_COUNT, factory.createLiteral(XMLDatatypeUtil.parseInteger("1")));
                                }
                            }
                        } else {
                            // in case the type is null, we set it default as string
                            b.subject(propShape).add(SHACL.DATATYPE, XSD.STRING);
                        }
                    });
                    
                    b.build().addAll(localBuilder.build());
                }
                if (numberOfObjectTypes > 1) {
                    List<Resource> members = new ArrayList<>();
                    Resource headMember = bnode();
                    
                    
                    for (Integer encodedObjectType : propObjectTypes) {
                        Tuple3<Integer, Integer, Integer> tuple3 = new Tuple3<>(encoder.encode(subj.stringValue()), prop, encodedObjectType);
                        String objectType = encoder.decode(encodedObjectType);
                        Resource currentMember = bnode();
                        //Cardinality Constraints
                        if (shapeTripletSupport.containsKey(tuple3)) {
                            if (shapeTripletSupport.get(tuple3).getSupport().equals(classInstanceCount.get(encoder.encode(subj.stringValue())))) {
                                b.subject(propShape).add(SHACL.MIN_COUNT, factory.createLiteral(XMLDatatypeUtil.parseInteger("1")));
                            }
                            /*if (Main.extractMaxCardConstraints) {
                                if (propWithClassesHavingMaxCountOne.containsKey(prop) && propWithClassesHavingMaxCountOne.get(prop).contains(subjEncoded)) {
                                    b.subject(propShape).add(SHACL.MAX_COUNT, factory.createLiteral(XMLDatatypeUtil.parseInteger("1")));
                                }
                            }*/
                        }
                        
                        if (objectType != null) {
                            if (objectType.contains(XSD.NAMESPACE) || objectType.contains(RDF.LANGSTRING.toString())) {
                                if (objectType.contains("<")) {objectType = objectType.replace("<", "").replace(">", "");}
                                IRI objectTypeIri = factory.createIRI(objectType);
                                localBuilder.subject(currentMember).add(SHACL.DATATYPE, objectTypeIri);
                                localBuilder.subject(currentMember).add(SHACL.NODE_KIND, SHACL.LITERAL);
                                
                                annotateWithSupportAndConfidence(currentMember, localBuilder, tuple3);
                                
                            } else {
                                if (Utils.isValidIRI(objectType) && !objectType.equals(Constants.OBJECT_UNDEFINED_TYPE)) {
                                    IRI objectTypeIri = factory.createIRI(objectType);
                                    localBuilder.subject(currentMember).add(SHACL.CLASS, objectTypeIri);
                                    localBuilder.subject(currentMember).add(SHACL.NODE_KIND, SHACL.IRI);
                                    annotateWithSupportAndConfidence(currentMember, localBuilder, tuple3);
                                } else {
                                    //System.out.println("INVALID Object Type IRI: " + objectType);
                                    localBuilder.subject(currentMember).add(SHACL.NODE_KIND, SHACL.IRI);
                                    annotateWithSupportAndConfidence(currentMember, localBuilder, tuple3);
                                }
                            }
                        } else {
                            // in case the type is null, we set it default as string
                            //b.subject(propShape).add(SHACL.DATATYPE, XSD.STRING);
                            localBuilder.subject(currentMember).add(SHACL.DATATYPE, XSD.STRING);
                        }
                        members.add(currentMember);
                    }
                    Model localModel = RDFCollections.asRDF(members, headMember, new LinkedHashModel());
                    localModel.add(propShape, SHACL.OR, headMember);
                    localModel.addAll(localBuilder.build());
                    b.build().addAll(localModel);
                }
            });
    }

        ShapeTripletSupport readShapeTripletSupportConfidence (String SUPPShardName) {
            byte[] shapeTripletSupportBytes = ReadBlobGivenName.readBlobGivenName(SUPPShardName);
            return (ShapeTripletSupport) Serialize.deserialize(shapeTripletSupportBytes);
        }

        CEC readClassEntityCount (String CECShardName) {
            byte[] CECBytes = ReadBlobGivenName.readBlobGivenName(CECShardName);
            return (CEC) Serialize.deserialize(CECBytes);
        }

        CTP readClassToPropWithObjTypes (String CTPShardName) {
            byte[] CTPBytes = ReadBlobGivenName.readBlobGivenName(CTPShardName);
            return (CTP) Serialize.deserialize(CTPBytes);
        }

        public String remAngBrackets(String typePredicate) {
            return typePredicate.replace("<", "").replace(">", "");
        }
    
        /**
     * SHARED METHOD (QSE-Default & QSE-Pruned) : to annotate shapes with support and confidence
     */
    /**
     * SHARED METHOD (QSE-Default & QSE-Pruned) : to annotate shapes with support and confidence
     */
    private void annotateWithSupportAndConfidence(Resource currentMember, ModelBuilder localBuilder, Tuple3<Integer, Integer, Integer> tuple3) {
        if (shapeTripletSupport.containsKey(tuple3)) {
            Literal entities = Values.literal(shapeTripletSupport.get(tuple3).getSupport()); // support value
            localBuilder.subject(currentMember).add(Constants.SUPPORT, entities);
            //localBuilder.subject(currentMember).add(VOID.ENTITIES, entities);
            Literal confidence = Values.literal(shapeTripletSupport.get(tuple3).getConfidence()); // confidence value
            localBuilder.subject(currentMember).add(Constants.CONFIDENCE, confidence);
        }
    }
}
