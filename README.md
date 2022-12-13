# mbta-arrivals
Using Akka’s built-in backpressure functionality, a stream of prediction events is throttled to one per second to simulate the speed of the MBTA streaming API. Predictions are stored in an in-memory map, and printed to the console. When an update is received for a prediction that was previously reported, an updated is printed to the console. The result is a human readable stream of predicted arrival times, and updates when trains are running early or late.

# MBTA API
https://www.mbta.com/developers/v3-api

# Setup
To get the application to run:

1. Add a file `application.conf` to src/main/resources with the below content:

```
arrivals {
    predictions-uri = "https://api-v3.mbta.com/predictions/?filter[route]=Red&include=schedule,stop"
    api-key = ""
    events-file-path = "PATH/TO/cleaned_data.txt"
}
```

2. Download a period of MBTA predictions to a text file using the below cURL command (replace YOUR_API_KEY with MBTA api key):

```sh
curl -sN -H "Accept:text/event-stream" -H "X-API-Key:YOUR_API_KEY" "https://api-v3.mbta.com/predictions/?filter\[route\]=Red&include=schedule,stop" > ~/Desktop/raw-data.txt
```

3. Use clean_data.py to prepare the data. This produces an output file in JSON-L format. Add the path to that output file to application.conf in the arrivals.events-file-path node.

Alternatively, you can use the included `cleaned_data.txt`

4. Use `sbt run` to run the application. 
