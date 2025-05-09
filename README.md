QSE over Serverless functions.
+
# INSTRUCTIONS TO RUN IT
0) NUMBEROFSUBFILES=number of subfiles, FILENAME=name of the file
1) split the file into "NUMBEROFSUBFILES" subfiles in the folder ./datasets/FILENAME
2) Set the FILENAME variable inside script.sh with name of the file choosen before (FILENAME)
3) docker-compose up --build
4) run http://localhost:7071/api/startOrchestration?filename=FILENAME.nt&subset=NUMBEROFSUBFILES

# INSTRUCTIONS TO RUN IT WITHOUT DOCKER
0) NUMBEROFSUBFILES=number of subfiles, FILENAME=name of the file, redis and azurite are already running
1) split the file into "NUMBEROFSUBFILES" subfiles in the folder ./datasets/FILENAME
2) Upload the folder "FILENAME" inside the azurite storage under the "filestorage" container
3) Modify the url inside JedisClass with "localhost" instead of "azurite"
4) Modify the AzureWebJobsStorage variable inside local.settings.json with the value: "UseDevelopmentStorage=true"
5) run "mvn clean package -X && mvn azure-functions:run"

# Progress
## Week 1
Implemented System Design using Cache for Redis for having a very fast and efficient memory which can keep up to 50 GB.
Difficulties:
- How to parallelize everything without having to keep in memory the entire ETD table (with also the properties0)

## Week 2
Started implemeneting a first version of the code, with all the structures passed as parameters.
Difficulties:
- We faced some problem with how azure functions work underthehood. Complex parameters are passed wrongly by azure and at runime a compatibility error is raised.

## Week 3
### First Goal: Instead of passing parameters to each function we try to use protobuf and blob storage
Difficulties:
- Azure uses protobuf underthehood, so it raised many compatibility errors at the beginning. At the end we found the right compatible version.
At the moment, we are passing parameters using blob storage but we are using the native java serialization instead of protobuf to speed up the developement of the first draft of the algorithm.
### Second Goal: try to make a first working draft of our algorithm
Difficulties:
- Very very hard to debug code inside the environment of serverless functions.
- Need to have a good understanding of the actual java implementation of the pseudo-code explained in the QSE paper.
- We found out that in order to pass complex parameters to azure functions, the class we want to use needs to define an empty constructor and the setters and getters of its attributes.
At the moment, we are missing the last phase of shapes extraction. Using a very trivial dataset, it seems that the previous phases are working.

## Week 4
### First Goal: finish the first draft of the algorithm:
#### First difficulty: StringEncoder
We found out that the StringEncoder structure (the dictionary that maps integer to string and vice-versa), can not be divided in shard and then merged after each phase, since during the merge there may be conflict of same integer value that maps to 2 different values in two different shards of the string encoder for example.

Solution: We treat the stringEncoder as a global shared structure between the functions. This can be done in two ways:
- Exploit leases (basically these are locks) on blobs and azure blob storage to manage concurrent access to the StringEncoder structure by different functions
- Use Redis key-value storage to reproduce the behaviour of the two HashMaps contained in the StringEncoder structure

#### Second difficulty: format of the SHACL shapes
Another difficulty was understanding the single-thread QSE code regarding the generation of the SHACL shapes.

At the moment we developed a first draft of the algorithm that on a trivial dataset extracts the same shapes as the single-thread QSE algo.

## Week 5
### First Goal: exploit redis to manage StringEncoder and ETD data structures that we need to share between the functions

#### First difficulty: StringEncoder
We need to find a way to manage concurrent access to the StringEncoder by different functions when we call the encode functions, otherwise we create race conditions, with the risk of having different id values mapping always to the same string value.

Solution: we exploited redis script execution (Lua) 

ETD does not have the problem of concurrent access, since the key are the entities, and each serverless function works on a different set of entities.

### Second Goal: transform the HashMap in java classes to make the code more modular
We simply transformed all the data structures that were represented as HashMaps in the QSE code into java classes. The ETD and the StringEncoder classes hide all the call to the redis storage, while the others for the moment are simple wrappers of the previous HashMaps.

### Third Goal: implement trivial "cache" mechanism for the ETD
The "gets" on these two data structures do not always access the redis storage, but first check if the value is already in memory. If not, it is fetched from the redis storage and then stored in memory. In this way we avoid to access the redis storage for each get, and we can easily exploit the maximum amount of memory that we have avaialble in each serverless function. (To remember: ETD is the biggest data structure that we have to manage)



## Week 6
### First Goal: benchmarking of Azure Functions
We did not find interesting documentation and information about benchmarking Azure Functions in local, so what we did was simply using timers inside our code, to track the duration of each of our phases.

| Process(Serverless QSE (10 functions))               | Time (ms) | Process(Single thread QSE)            | Time (ms) |
|---------------------------------------|-----------|---------------------------------------|-----------|
| readBlob Time                         | 2537      | Time Elapsed firstPass                | 511       |
| partitionByEntities Time              | 1205      | Time Elapsed secondPhase              | 847       |
| entityExtraction Time                 | 5750      | Time Elapsed computeSupportConfidence | 105       |
| entityConstraintsExtraction Time      | 16893     | Time Elapsed 0.8_1                    | 495       |
| mergeSupport Time                     | 369       | Time Elapsed extractSHACLShapes-Time.For.Pruning.Only | 497 |
| mergeCEC Time                         | 347       | Time Elapsed extractSHACLShapes       | 2479      |
| mergeCTP Time                         | 356       | Total Execution Time                   | 4934      |
| shapesExtraction Time                 | 399       |                                        |           |
| Total Execution Time                  | 27856     |                                        |           |

File benchmark: there is a problem when multiple functions read the same file, the time increases (while it shouldn't given the read-only access).
we benchmarked a simplified version of the readBlob function and it gave us the following results.
Time: from 720ms to 4/5 seconds with 10 functions to 9 seconds with 20 functions


### Second Goal: try to make the algorithm faster
After spotting the slowest phase(EntityConstraintsExtraction) in our algorithm we tried to make our algorithm faster. First thing we did was trying to change serialization mechanisms: we tried both **Kryo** and **Protobuf**
#### First difficulty: Implementing protoBuf
We tried to implement protobuf in both ways, as just a serializer and as a subclass of EntityData (so it was the main storage used for all the EntityData structures). In both cases the performances were worse than before.
Problems encountered: some code was using pointers to data structures which were then modified locally (causing a lost of data when I switched to protobuf due to the lost of the pointers). Also protobuf doesn't enforce the Set, so 
I had to force it with an additional check.
Still slower. (from 25 to 30/35s).
https://www.alibabacloud.com/blog/an-introduction-and-comparison-of-several-common-java-serialization-frameworks_597900

### Third Goal: make the output compliant with SHACLShapes
We obtain a Model that seems to have the correct statements, but we need to format them in the proper way for SHACLShapes for the validation phase (and also in order to compare them with the single thread QSE algorithm).

We did not manage to do this at the moment, as suggested by Baya we may be missing something in the Model building.

## Week 7-8-9
We started optimizing the sequential functions path to obtain the best time without parallelism (the local emulator is not very good at handling parallelism when it comes to functions).

### First Goal: improving the performance
By adding some cache to the String Encoder, by keeping Kryo and by doing the merges in parallel we lowered the time from 30/35 to 22/20 seconds (sequential time). We also fixed some bug that we found in the path.
We also tried to remove the StringEncoder to find out if there was any improvement in time performane with an higher storage cost (branch dev-no-StringEncoder) without getting a much better performance

We also found out that the time of execution of the function computed within the function itself is different from the one given by azure functions Duration, this is probably due to the overhead the scheduler has when calling activities.
Ex for readBlob:
[2024-04-17T15:46:54.533Z] Total time taken: 1567
[2024-04-17T15:46:54.769Z] Executed 'Functions.readBlob' (Succeeded, Id=a8e2483a-283c-4e97-9a40-198dbebc1524, Duration=4106ms)
We created a file called benchmarks.log where we write down the most significative benchmarks done. 
### Second Goal: output SHACL compliant
We are still missing a minor difference, but the shapes in output are the same as the ones in the single thread QSE algorithm. 

## Week 10

### First Goal: make the code run in the cloud
We managed to obtain a students subscribtion with 100$ for 1 month, and we made our code run on the Azure cloud.
##### Main Problems:
- Which subscription tier should we use for running functions in Azure ?
![Azure Functions](./function_plans.png)

- The code is running incredibly slow, we still don't know why since we spent most of the last time in trying to get the students subscription and refactor the code in order to run on the cloud.

## Next Goals
### First Goal:
EntityExtraction and EntityConstraintsExtraction are incredibly slow, we think that it may be because, even if they have to work only on a subgroup of entities, they always read the whole graph file. Our possible solution may be split the initial file in as many file as subgroup of entities, so that EntityExtraction/EntityConstraintsExtraction function will read a file containing **only** the subgroup of entities it is responsible for, rather than reading the whole file and then filtering the entities.
### Second Goal:
Make a benchmark using a file with O(GB) dimensions rather than just a 30MB file and compare the performances of:
- Our algorithm run locally on our PC
- Our algorithm run on the Azure Cloud
- Single thread qse algorithm run on our PC

## Week 11
### First Goal: speed up the algorithm
We managed to make the algorithm run on lubm-mini, at least, as fast as it did on our local computer (25/30 sec). Since when we first uploaded our code on the cloud it was running incredibly slow.

The problem was the redis storage, in fact, by running some benchmark we noticed that most of the processing time of the two critical phases (EntityExtraction and EntityConstraintsExtraction) was spent on redis.

By upgrading the redis cache plan from standard to premium, we managed to re-obtain the same results we were obtaining locally.

### Second Goal: run algorithm on a O(GB) file in the cloud
We faced many many problems when trying to do so:
- There is no way to read a file from blob storage line by line, without downloading the whole blob before. Thus when it comes to bigger files, we are forced to assign a small subfile to each function, so that we do not run out of memory when reading from a O(GB) file.
- The second problem, thus, was having a function that splits our file before starting the computation. Of course, this cannot be done using a simple azure functions (since we would end up again in the same memory error), so we wrote a python script to be run on a VM, that splits the files into subfiles and save them in our blob storage before starting the whole orchestration. This process is quite slow, but we think that there's no need to do it everytime, since update to the graph may be handled with simple append to the already split subfiles.
- Third problem, azure functions timeout during entityConstrainstExtraction phase when dealing with O(GB) file. We are still investigating why, probably the redis time is too high. We have to see if we can change the algorithm in order to access fewer times the redis cache since that appears to be the bottleneck now.
Still, the main problem is the ETD structure in EntityConstraintsExtraction, since we need to have access to the instance type of the objects, that may not belong to the subgroup of entities assigned to the specific worker.

- We saw that redis is not fully utilized during algorithm execution, so the idea is to make more requests in parallel, inside each function, since each request is blocking otherwise.
### Benchmarks

| Algorithm              | Time (s) |
|---------------------------------------|-----------|
| Single thread                        | 25      | 
|Serverless QSE on cloud||
|Serverless QSE local||

EntityExtraction timings with O(GB) files:

Function "EntityExtraction" (Id: 5cf94bb2-2d42-4d38-a919-00ab10a8c48d) invoked by Java Worker **With 50 functions**
- 2024-05-27T07:55:26Z   [Information]   ETD push time: 22499
- 2024-05-27T07:55:26Z   [Information]   Processing Time: 33828 ms
- 2024-05-27T07:55:26Z   [Information]   Redis Time: 31575 ms

Function "EntityExtraction" (Id: 5cf94bb2-2d42-4d38-a919-00ab10a8c48d) invoked by Java Worker **With 100 functions**
- 2024-05-27T08:36:15Z   [Information]   ETD push time: 14619
- 2024-05-27T08:36:15Z   [Information]   Processing Time: 16844 ms
- 2024-05-27T08:36:15Z   [Information]   Redis Time: 16546 ms


## Week 12
Main objectives:
-   fixing the code with StringEncoder
-   running benchmarks
Main results:
-   fixing: code fixed, we had an issue on how the functions work in the cloud. We discovered that the orchestrator works in a similar way as java when running functions so given our Singleton pattern used for the StringEncoder when doing new run it was reusing an old instance of the SE causing many problems when synchronizing the SE cache with the SE table on Redis.
-   improvements: added a new cache instead of a simple Map which has better performances and better sobstitution algorithms implemented
-   improvements: rewrote entirely the code for managing redis, now we use a unique wrapper class around JedisPool which allows a more efficient and robust use of the redis connections. Among the features we have multiple connections client-server, an autocheck for the liveliness of the connection and automatic creation of new connections when needed.
-   improvements: finishing adding the pipelines for a better efficiency of redis

-   benchmarks:
( all the following benchmarks don't take into account the time for splitting the file given the not optimized code used for doing it )

| Model                  | Number of Subsets | File          | first run  | average runs |
|------------------------|-------------------|---------------|------------|--------------|
| QSE-no-String-Encoder  | 1                 | CimpleAvril   |   30.5s    |    29s       |
| QSE-no-String-Encoder  | 10                | CimpleAvril   |    10s     |    7.5s      |
| QSE-no-String-Encoder  | 20                | CimpleAvril   |    9.4s    |     7.5s     |
| QSE-StringEncoder                    | 1                 | CimpleAvril   |  77.8s     |   77s        |
| QSE-StringEncoder                    | 10                | CimpleAvril   |   25s      |     23s      |
| QSE-StringEncoder                    | 20                | CimpleAvril   |   23s      |    22s       |
| Single thread QSE      | -                 | CimpleAvril   |   3.8s     |     3.8s     |


| Model                  | Number of Subsets | File          | first run  | average runs |
|------------------------|-------------------|---------------|------------|--------------|
| QSE-no-String-Encoder  | 1                 | CimpleJanvier | 16s        | 10s          |
| QSE-no-String-Encoder  | 10                | CimpleJanvier | 5.7s       | 3.5s         |
| QSE-no-String-Encoder  | 20                | CimpleJanvier | 5.6s       | 3.2s         |
| QSE-StringEncoder                    | 1                 | CimpleJanvier |    19s     |  19s         |
| QSE-StringEncoder                    | 10                | CimpleJanvier |    7.5s    |  6.5s        |
| QSE-StringEncoder                    | 20                | CimpleJanvier |   7.4s     |  7s          |
| Single thread QSE      | -                 | CimpleJanvier | 3.3s       | 3.3s         |



| Model                  | Number of Subsets | File          | first run  | average runs |
|------------------------|-------------------|---------------|------------|--------------|
| QSE-no-String-Encoder  | 200               | WikiData      | Heap Error |  Heap Error  |
| QSE-StringEncoder                    | 200               | WikiData      | Heap Error | Heap Error   |
| Single thread QSE      | -                 | WikiData      |   77s      |     76.3s      |


| Model                  | Number of Subsets | File          | first run  | average runs |
|------------------------|-------------------|---------------|------------|--------------|
| QSE-no-String-Encoder  | 100               | lubm-big (1GB)      | 317.5 |  310  |
| QSE-StringEncoder                    | 100               | lubm-big (1GB)      | not done | not done   |
| Single thread QSE      | -                 | lubm-big (1GB)      |   23.5s      |     23.3s      |


## Future things:
-   writing the report