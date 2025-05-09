package com.QSE.httpTriggers;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import java.util.*;
import java.util.stream.Collectors;

import com.microsoft.durabletask.*;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import com.microsoft.durabletask.azurefunctions.DurableClientInput;
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger;

import com.QSE.orchestrator.*;

public class HttpTriggerFunction {
        /**
         * This HTTP-triggered function starts the orchestration.
         */
        @FunctionName("startOrchestration")
        public HttpResponseMessage startOrchestration(
                @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
                @DurableClientInput(name = "durableContext") DurableClientContext durableContext,
                final ExecutionContext context) {
                context.getLogger().info("Java HTTP trigger processed a request.");
                //take the filename from the request
                String filename = request.getQueryParameters().get("filename");
                if (filename == null) {
                        filename = "lubm-mini.nt";
                }
                Integer numberOfSubset=null;
                try {
                        numberOfSubset=Integer.parseInt(request.getQueryParameters().get("subset"));
                } catch (Exception e) {
                        // TODO: handle exception
                }
                if(numberOfSubset==null){
                        numberOfSubset=0;
                }
                DurableTaskClient client = durableContext.getClient();
                String instanceId = client.scheduleNewOrchestrationInstance("Orchestrator", filename+" "+numberOfSubset.toString());
                context.getLogger().info("Created new Java orchestration with instance ID = " + instanceId);
                return durableContext.createCheckStatusResponse(request, instanceId);
        }
}
