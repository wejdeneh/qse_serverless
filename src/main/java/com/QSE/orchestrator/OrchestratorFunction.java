package com.QSE.orchestrator;

import com.microsoft.azure.functions.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import com.microsoft.durabletask.*;
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger;

import com.QSE.models.EntityConstraintsExtractionPayload;
import com.QSE.models.EntityConstraintsExtractionReturnValue;
import com.QSE.models.EntityExtractionPayload;
import com.QSE.models.EntityExtractionReturnValue;
import com.QSE.models.ShapesExtractionPayload;
import com.QSE.models.PartitionByEntitiesPayload;
import com.QSE.models.CreateSubFilesPayload;

/**
 * Please follow the below steps to run this durable function sample
 * 1. Send an HTTP GET/POST request to endpoint `StartHelloCities` to run a durable function
 * 2. Send request to statusQueryGetUri in `StartHelloCities` response to get the status of durable function
 * For more instructions, please refer https://aka.ms/durable-function-java
 * 
 * Please add com.microsoft:durabletask-azure-functions to your project dependencies
 * Please add `"extensions": { "durableTask": { "hubName": "JavaTestHub" }}` to your host.json
 */
public class OrchestratorFunction {

    /**
     * This is the orchestrator function, which can schedule activity functions, create durable timers,
     * or wait for external events in a way that's completely fault-tolerant.
     */
    @FunctionName("orchestrator")
    public String orchestrator(
            @DurableOrchestrationTrigger(name = "ctx") TaskOrchestrationContext ctx) {


        ctx.callActivity("clean").await();
        //Starting the timer
        Instant startTime = ctx.getCurrentInstant();
        String inputparams=ctx.getInput(String.class);
        System.out.println("Input params: "+inputparams);
        String[] params=inputparams.split(" ");
        System.out.println("Params: "+params[0]+" "+params[1]);
        String graphFileName=params[0];
        Integer numberOfSubset=Integer.parseInt(params[1]);
        
        // the folder is the filename without extension
        String folder = graphFileName.substring(0, graphFileName.lastIndexOf('.'));

        List<String> subfiles = new ArrayList<>();
        // Create numberOfSubset strings of type subfile_i
        for (int i = 0; i < numberOfSubset; i++) {
            subfiles.add(folder+"/subfile" + i + ".nt");
        }

        //-------------PHASE 1: Entity Extraction-------------------
        //partitionByEntities phase timer measure
        Instant createSubFilesTime = ctx.getCurrentInstant();

        //TODO: change payload of EntityExtraction and EntityConstraintsExtraction
        
        //Schedule activity functions to extract entities. Each activity function will perform entity extraction only for a subgroup of entities.
        List<Task<EntityExtractionReturnValue>> tasks = new ArrayList<>();
        for (int i = 0; i < numberOfSubset; i++) {
            tasks.add(ctx.callActivity("EntityExtraction", new EntityExtractionPayload(null, subfiles.get(i)), EntityExtractionReturnValue.class));
        }
        List<EntityExtractionReturnValue> results_tmp = ctx.allOf(tasks).await();

        Instant entityExtractionTime = ctx.getCurrentInstant();
        
        //Extract filenames of the shards of CEC data structures
        List<String> CECShards = results_tmp.stream().map(EntityExtractionReturnValue::getCECShardName).collect(Collectors.toList());

        Instant CECShardsExtractionTime = ctx.getCurrentInstant();

        //-------------PHASE 2 and PHASE 3: Entity Constraints Extraction and Support Computation-------------------
        //Schedule activity functions to extract entity constraints. Each activity function will perform entity constraints extraction only for a subgroup of entities.
        List<Task<EntityConstraintsExtractionReturnValue>> tasks2 = new ArrayList<>();
        for (int i = 0; i < numberOfSubset; i++) {
            tasks2.add(ctx.callActivity("EntityConstraintsExtraction", new EntityConstraintsExtractionPayload(subfiles.get(i), null), EntityConstraintsExtractionReturnValue.class));
        }
        List<EntityConstraintsExtractionReturnValue> results2 = ctx.allOf(tasks2).await();

        Instant entityConstraintsExtractionTime = ctx.getCurrentInstant();

        //Extract filenames of the shards of SUPP and CTP data structures
        List<String> shapeTripletSupportFilenames = results2.stream().map(EntityConstraintsExtractionReturnValue::getShapeTripletSupportFilename).collect(Collectors.toList());
        List<String> CTPFilenames = results2.stream().map(EntityConstraintsExtractionReturnValue::getCTPFilename).collect(Collectors.toList());

        //Schedule activity functions to merge SUPP, CEC(computed during phase 1) and CTP shards
        List<Task<String>> tasksMerge= new ArrayList<>();
        
        tasksMerge.add(ctx.callActivity("mergeSupport", shapeTripletSupportFilenames, String.class));
        tasksMerge.add(ctx.callActivity("mergeCEC", CECShards, String.class));
        tasksMerge.add(ctx.callActivity("mergeCTP", CTPFilenames, String.class));

        List<String> results=ctx.allOf(tasksMerge).await();

        System.out.println("Merged files "+results.get(0)+" "+results.get(1)+" "+results.get(2));

        String mergedSUPPShardName = results.get(0);
        String mergedCECShardName = results.get(1);
        String mergedCTPShardName = results.get(2);

        Instant mergeTime = ctx.getCurrentInstant();
        //-----------PHASE 4: Confidence computation and Shape Extraction------------------
        String result = ctx.callActivity("shapesExtraction", new ShapesExtractionPayload(mergedSUPPShardName, mergedCECShardName, mergedCTPShardName), String.class).await();

        Instant shapesExtractionTime = ctx.getCurrentInstant();

        //Print all the timings
        System.out.println("Total Execution Time: " + (shapesExtractionTime.toEpochMilli() - startTime.toEpochMilli()) + " ms");
        //System.out.println("readBlob Time: " + (readBlobTime.toEpochMilli() - startTime.toEpochMilli()) + " ms");
        //System.out.println("partitionByEntities Time: " + (partitionByEntitiesTime.toEpochMilli() - readBlobTime.toEpochMilli()) + " ms");
        //System.out.println("createSubFiles Time: " + (createSubFilesTime.toEpochMilli() - partitionByEntitiesTime.toEpochMilli()) + " ms");
        System.out.println("entityExtraction Time: " + (entityExtractionTime.toEpochMilli() - createSubFilesTime.toEpochMilli()) + " ms");
        System.out.println("CECShardsExtraction Time: " + (CECShardsExtractionTime.toEpochMilli() - entityExtractionTime.toEpochMilli()) + " ms");
        System.out.println("entityConstraintsExtraction Time: " + (entityConstraintsExtractionTime.toEpochMilli() - CECShardsExtractionTime.toEpochMilli()) + " ms");
        System.out.println("merge Time: " + (mergeTime.toEpochMilli() - entityConstraintsExtractionTime.toEpochMilli()) + " ms");
        System.out.println("shapesExtraction Time: " + (shapesExtractionTime.toEpochMilli() - mergeTime.toEpochMilli()) + " ms");
        return result;
    }    
}