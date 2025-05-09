# for running
# gunicorn --timeout 0 -b 0.0.0.0:5001 VMServer:app
# wget http://0.0.0.0:5001/10
from multiprocessing import Manager, Process
from azure.storage.blob import BlobServiceClient
import logging
import time

from collections import OrderedDict
import os
import sys

# Azure Blob Storage connection string
AZURE_CONNECTION_STRING = "DefaultEndpointsProtocol=https;AccountName=qseserverlessusb2f4;AccountKey=CIE9GHYVUilHZPrLfesENPpjj9/Uqt3Mt4os5+WaZTBJXl8K5pVnieQc54Zn2mwto0lKMOaf8zU7+AStmaoOsQ==;EndpointSuffix=core.windows.net"
logging.basicConfig(stream=sys.stdout, level=logging.INFO)

def read_blob_from_azure(blob_name, number_of_subgroups):
    while True:
        try:
            start_time = time.time()
            '''blob_service_client = BlobServiceClient.from_connection_string(AZURE_CONNECTION_STRING)
            blob_client = blob_service_client.get_container_client("filestorage").get_blob_client(blob_name)
            start_time = time.time()
            # logging.info memory ram usage
            logging.info("Memory usage before reading the blob")
            os.system("free -m")
            # download blob to file
            logging.info(f"Downloading blob {blob_name}")
            file_name= "downloaded_file.nt"
            with open(file_name, "wb") as file:
                blob_data = blob_client.download_blob()
                blob_data.readinto(file)
            #os.system("free -m")
            entity_count = {}'''
            #os.system("free -m")
            entity_count = {}
            file_name = "datasets/lubm-big.nt"
            file= open(file_name, "r")
            lines = []
            for line in file:
                subj, _, _, _ = line.split()
                entity_count[subj] = entity_count.get(subj, 0) + 1
                lines.append(line)
            #os.system("free -m")

            logging.info(f"Entity counting completed in {time.time() - start_time} seconds")
            logging.info(f"Number of entities: {len(entity_count)}")

            subgroups = distribute_entities(entity_count, number_of_subgroups)

            #transform subgroups in a list of python Set
            subgroups = [set(subgroup) for subgroup in subgroups]
            
            for i in range(number_of_subgroups):
                filename=f"datasets/subfiles/lubm-big/subfile{i}.nt"
                create_subfile(i, subgroups[i], "d",filename, lines)
                
            logging.info(f"Total processing time: {time.time() - start_time} seconds")
            return {"message": "Success", "files": files}
        except Exception as e:
            logging.info(f"Error reading blob: {str(e)}")
            time.sleep(10)
            logging.info(f"Read Error: {str(e)}")
            return None

def distribute_entities(entity_count, number_of_subgroups):
    sorted_entities = sorted(entity_count.keys())
    subgroups = [[] for _ in range(number_of_subgroups)]
    for i, entity in enumerate(sorted_entities):
        subgroups[i % number_of_subgroups].append(entity)
    return subgroups

def create_subfile(index, entity_list, graphFilename, filename, lines):
    try:
        # first line is the list of entities splitted by a comma
        #remove brackets or quotes for all entities in entity_list
        removed_content = []
        for entity in entity_list:
            entity = entity.replace("<", "").replace(">", "")
            entity = entity.replace('"', "")
            removed_content.append(entity)

        subfile_content = ','.join(removed_content) + "\n"
        N = 0
        for line in lines:
            N += 1
            subj, pred, obj, _ = line.split()
            if str(subj) in entity_list:
                subfile_content += f"{subj} {pred} {obj} .\n"
            if N % 10000 == 0:
                print(f"Processed {N} lines by worker {index}")

        #save in local file
        with open(filename, "w") as file:
            file.write(subfile_content)
        logging.info(f"Subfile {index} created successfully")
        #write_blob(filename, subfile_content)
    except Exception as e:
        logging.info(f"Error creating subfile {index}: {str(e)}")

def write_blob(blob_name, data):
    try:
        blob_service_client = BlobServiceClient.from_connection_string(AZURE_CONNECTION_STRING)
        blob_client = blob_service_client.get_container_client("filestorage").get_blob_client(blob_name)
        blob_client.upload_blob(data, overwrite=True)
        logging.info(f"Blob {blob_name} uploaded successfully")
    except Exception as e:
        logging.info(f"Error uploading blob {blob_name}: {str(e)}")

def upload_blob_from_files(files):
    for file in files:
        with open(file, "r") as f:
            data = f.read()
            write_blob(file, data)
    

if __name__ == "__main__":
    read_blob_from_azure("lubm-mini.nt", 50)
    #files = [f"files/subfile{i}.nt" for i in range(50)]
    #upload_blob_from_files(files)
