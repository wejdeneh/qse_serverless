# for running
# gunicorn --timeout 0 -b 0.0.0.0:5001 VMServer:app
# wget http://0.0.0.0:5001/10
from multiprocessing import Manager, Process
from flask import Flask, jsonify
from azure.storage.blob import BlobServiceClient
from rdflib import Graph
import logging
import time

from collections import OrderedDict
import os
import sys

app = Flask(__name__)

# Azure Blob Storage connection string
AZURE_CONNECTION_STRING = "DefaultEndpointsProtocol=https;AccountName=qseusgroupbaf1;AccountKey=wakS6Oz7qHnlDwrqn0k/HMwjNxTXZnW5HqD999xz9SYYezQf8A9hmObjPey8CxxqECIHj4eyEVNP+AStDNlOiw==;EndpointSuffix=core.windows.net"
logging.basicConfig(stream=sys.stdout, level=logging.INFO)

@app.route("/")
def hello():
    return jsonify({"message": "Insert the number of subgroups in the URL"})

@app.route("/<int:num_subgroups>")
def home(num_subgroups):
    response = read_blob_from_azure("lubm-big.nt", num_subgroups)
    if response is None:
        return jsonify({"message": "Error processing the request"}), 500
    return jsonify(response)

@app.route("/check/<int:num_subgroups>")
def check(num_subgroups):
    for i in range(num_subgroups):
        blob_name = f"files/subfile{i}_10.nt"
        blob_client = BlobServiceClient.from_connection_string(AZURE_CONNECTION_STRING).get_container_client("filestorage").get_blob_client(blob_name)
        blob_stream = blob_client.download_blob()
        blob_content = blob_stream.content_as_text()
        logging.info(f"Subfile {i} has {len(blob_content.splitlines())} triples")
    return jsonify({"message": "Success"})

def read_blob_from_azure(blob_name, number_of_subgroups):
    while True:
        try:
            blob_service_client = BlobServiceClient.from_connection_string(AZURE_CONNECTION_STRING)
            blob_client = blob_service_client.get_container_client("filestorage").get_blob_client(blob_name)
            start_time = time.time()
            # logging.info memory ram usage
            logging.info("Memory usage before reading the blob")
            os.system("free -m")
            # download blob to file
            logging.info(f"Downloading blob {blob_name}")
            file_name= "downloaded_file.nt"
            with open(file_name, "wb") as file:
                blob_data = blob_client.download_blob(max_concurrency=10)
                blob_data.readinto(file)
            os.system("free -m")
            entity_count = {}
            os.system("free -m")
            file= open(file_name, "r")
            for line in file:
                subj, _, _, _ = line.split()
                entity_count[subj] = entity_count.get(subj, 0) + 1
            os.system("free -m")

            logging.info(f"Entity counting completed in {time.time() - start_time} seconds")
            logging.info(f"Number of entities: {len(entity_count)}")
            
            subgroups = distribute_entities(entity_count, number_of_subgroups)

            subgroups = [set(subgroup) for subgroup in subgroups]
            
            with Manager() as manager:
                files = []
                processes = []

                for i in range(number_of_subgroups):
                    filename=f"lubm_big/subfile{i}.nt"
                    process = Process(target=create_subfile, args=(i, subgroups[i], file_name,filename))
                    files.append(filename)
                    process.start()
                    processes.append(process)

                for process in processes:
                    process.join()
                
            logging.info(f"Total processing time: {time.time() - start_time} seconds")
            files= list(files)
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

def create_subfile(index, entity_list, graphFilename, filename):
    try:
        # first line is the list of entities splitted by a comma
        cleaned_entities= []
        for entity in entity_list:
            cleaned_entities.append(entity.replace("<", "").replace(">", "").replace("\"",""))
        subfile_content = ','.join(cleaned_entities) + "\n"
        with open(graphFilename, "r") as file:
            for line in file:
                subj, pred, obj, _ = line.split()
                if str(subj) in entity_list:
                    subfile_content += f"{subj} {pred} {obj} .\n"
        write_blob(filename, subfile_content)
    except Exception as e:
        logging.info(f"Error creating subfile {index}: {str(e)}")

def write_blob(blob_name, data):
    while True:
        try:
            blob_service_client = BlobServiceClient.from_connection_string(AZURE_CONNECTION_STRING)
            blob_client = blob_service_client.get_container_client("filestorage").get_blob_client(blob_name)
            blob_client.upload_blob(data, overwrite=True)
            logging.info(f"Blob {blob_name} uploaded successfully")
            break
        except Exception as e:
            logging.info(f"Error uploading blob {blob_name}: {str(e)}")
    

if __name__ == "__main__":
    app.run(debug=True)
