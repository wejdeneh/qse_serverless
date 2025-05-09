package com.QSE.activityFunctions;

import java.io.ByteArrayOutputStream;

import com.azure.storage.blob.*;
import com.azure.storage.blob.specialized.BlobInputStream;

public class ReadBlobGivenName {
    public static byte[] readBlobGivenName(String name) {
        // Read the blob with the given name
        String connectionString = System.getenv("AzureWebJobsStorage");
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().connectionString(connectionString).buildClient();
        BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient("filestorage");
        BlobClient blobClient = blobContainerClient.getBlobClient(name);
        //get the byteArray from the blob
        BlobInputStream blobIS = blobClient.openInputStream();
        byte[] byteArray = null;
        try {
            byteArray=blobIS.readAllBytes();
        } catch (Exception e) {
            // TODO: handle exception
            System.err.println("Error reading the blob");
        }
        return byteArray;
    }
}
