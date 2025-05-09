package com.QSE.activityFunctions;
import com.QSE.models.JedisClass;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.ExecutionContext;
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
public class Clean {
    /**
     * Computes the confidence of the shape triplets
     */
    @FunctionName("clean")
    public void clean(
            @DurableActivityTrigger(name = "name") String payload,
            final ExecutionContext context) {
                String connectStr=System.getenv("AzureWebJobsStorage");
                // clean redis
                JedisClass jedis = new JedisClass();
                jedis.clean();
                //clean blob
                BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectStr)
                .buildClient();
                BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient("filestorage");
                for (BlobItem blobItem : blobContainerClient.listBlobs()) {
                    String blobName = blobItem.getName();
                    // Skip blobs in the "files/" directory
                    if  (blobName.startsWith("classEntityCount/")||blobName.startsWith("CTP/")||blobName.startsWith("merged")||blobName.startsWith("shapeTripletSupport")) {
                        BlobClient blobClient = blobContainerClient.getBlobClient(blobItem.getName());
                        blobClient.delete();
                        context.getLogger().info("Deleted blob: " + blobItem.getName());
                    }
                }                
    }
}
