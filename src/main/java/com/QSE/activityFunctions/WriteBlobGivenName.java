package com.QSE.activityFunctions;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.*;

public class WriteBlobGivenName {
    public static void writeBlobGivenName(String name, byte[] data) {
        // Write the blob with the given name
        String connectionString = System.getenv("AzureWebJobsStorage");
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(connectionString).buildClient();
        BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient("filestorage");
        BlobClient blobClient = blobContainerClient.getBlobClient(name);
        blobClient.upload(BinaryData.fromBytes(data), true);
    }

    public static void writeBlobGivenName(String name, String data) {
        // Write the blob with the given name
        String connectionString = System.getenv("AzureWebJobsStorage");
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(connectionString).buildClient();
        BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient("filestorage");
        BlobClient blobClient = blobContainerClient.getBlobClient(name);
        blobClient.upload(BinaryData.fromString(data), true);
    }
}
