#!/bin/bash
# Start application.
echo "Waiting for Kafka Connect to be ready..."

# Wait for Kafka Connect listener
while [ $(curl -s -o /dev/null -w %{http_code} http://kafka-connect:8083) -ne 200 ] ; do
  echo -e "\t" $(date) " Kafka Connect listener HTTP state: " $(curl -s -o /dev/null -w %{http_code} http://kafka-connect:8083) " (waiting for 200)" 
  sleep 5
done 

curl --location --request DELETE 'http://kafka-connect:8083/connectors/elasticsearch-hotel-sink' 
curl --location --request DELETE 'http://kafka-connect:8083/connectors/elasticsearch-booking-sink' 
curl --location --request DELETE 'http://kafka-connect:8083/connectors/mysql-connector' 

curl -s -X POST -H  "Content-Type:application/json" http://kafka-connect:8083/connectors \
    -d @resources/kafka-connect-configs/elasticsearch-hotel-sink-connector.json
curl -s -X POST -H  "Content-Type:application/json" http://kafka-connect:8083/connectors \
    -d @resources/kafka-connect-configs/elasticsearch-booking-sink-connector.json
curl -s -X POST -H  "Content-Type:application/json" http://kafka-connect:8083/connectors \
    -d @resources/kafka-connect-configs/mysql-source-connector.json

# Wait for Kafka Connect listener
while [ $(curl -s -o /dev/null -w %{http_code} http://kafka-connect:8083/connectors) -ne 200 ] ; do
  echo -e "\t" $(date) " Kafka Connect listener HTTP state: " $(curl -s -o /dev/null -w %{http_code} http://kafka-connect:8083/connectors) " (waiting for 200)" 
  sleep 5
done 

echo -e $(date) "\n\n--------------\n\o/ Kafka Connect is ready! \n--------------\n" 