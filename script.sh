#!/bin/sh
set -e
FILENAME="test_dataset_small"

# Create the storage container
az storage container create -n filestorage --connection-string "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://azurite:10000/devstoreaccount1;QueueEndpoint=http://azurite:10001/devstoreaccount1;"

# Upload the files in the lubm-mini folder
for file in /app/datasets/${FILENAME}/*; do
    if [ -f "$file" ]; then
        az storage blob upload -f "$file" -c filestorage -n "${FILENAME}/$(basename "$file")" --connection-string "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://azurite:10000/devstoreaccount1;QueueEndpoint=http://azurite:10001/devstoreaccount1;"
        echo ${FILENAME}/$(basename "$file") uploaded
    fi
done

# Package the application and run it
mvn clean package -X && mvn azure-functions:run
