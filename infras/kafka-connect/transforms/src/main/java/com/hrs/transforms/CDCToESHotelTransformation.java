package com.hrs.transforms;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.sink.SinkRecord;
import org.apache.kafka.connect.transforms.Transformation;

import java.util.Map;

public class CDCToESHotelTransformation implements Transformation<SinkRecord> {

    @Override
    public SinkRecord apply(SinkRecord record) {
        // Extract the value from the Kafka SinkRecord
        Object value = record.value();
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> valueMap = (Map<String, Object>) value;
            Map<String, Object> payload = (Map<String, Object>) valueMap.get("payload");
            Map<String, Object> after = (Map<String, Object>) payload.get("after");

            if (after != null) {
                // Transform the "after" data into a flat ES-compatible document
                Map<String, Object> transformedValue = Map.of(
                        "id", after.get("id"),
                        "name", after.get("name"),
                        "address", after.get("address"),
                        "city", after.get("city"),
                        "capacity", after.get("capacity")
                );

                // Return the transformed record
                return new SinkRecord(
                        record.topic(),
                        record.kafkaPartition(),
                        record.keySchema(),
                        record.key(),
                        null, // No schema for the transformed value
                        transformedValue,
                        record.kafkaOffset()
                );
            }
        }

        // Return the original record if "after" is null or value is not a Map
        return record;
    }

    @Override
    public void configure(Map<String, ?> config) {
        // Configuration code (if necessary)
    }

    @Override
    public void close() {
        // Clean up resources (if necessary)
    }

    @Override
    public ConfigDef config() {
        return new ConfigDef();
    }
}
