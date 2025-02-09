FROM confluentinc/cp-kafka-connect-base:latest

USER root
RUN yum -y update
RUN yum -y --allowerasing install crontabs jq findutils coreutils vi

# RUN chown -R appuser:appuser /home/appuser

# Create directories for Kafka Connect configuration and data
RUN mkdir -p /home/appuser/resources/kafka-connect-configs && \
    chmod -R 755 /home/appuser/resources && \
    mkdir -p /data && \
    chmod -R 755 /data

# Change ownership of the /data directory to appuser
# RUN chown -R appuser:appuser /data

# Create the directory for Kafka Connect configuration and set permissions
# USER appuser
ENV KAFKA_CONNECT_HOST=8083
EXPOSE 8083

# Install the Kafka Connect Elasticsearch Sink connector from Confluent Hub
RUN confluent-hub install --no-prompt confluentinc/kafka-connect-elasticsearch:latest

# Copy Kafka Connect configuration files into the container
COPY ./*.json resources/kafka-connect-configs/
COPY ./data/ /data/

# Copy the startup script and set appropriate permissions
COPY ./scripts/startup-connector.sh /etc/confluent/docker/startup-connector.sh
COPY ./scripts/docker-entrypoint.sh /etc/confluent/docker/docker-entrypoint.sh

# Make sure the entrypoint script is executable
RUN chmod +x /etc/confluent/docker/docker-entrypoint.sh /etc/confluent/docker/startup-connector.sh

CMD ["/bin/bash", "-c", "/etc/confluent/docker/startup-connector.sh & /etc/confluent/docker/docker-entrypoint.sh"]