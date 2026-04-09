package com.example.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MongoToElasticSyncService {

  private static final Logger log = LoggerFactory.getLogger(MongoToElasticSyncService.class);

  private final ElasticsearchClient esClient;

  public MongoToElasticSyncService(ElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  public void sync() {
    log.info("Starting MongoDB to Elasticsearch sync...");
    recreateIndex();
  }

  private void recreateIndex() {
    try {

      // Check if the index exists first
      IndicesResponse response = esClient.cat().indices();

      log.info("Elasticsearch indices");

      response
          .indices()
          .forEach(
              record -> {
                log.info(
                    "Name: {} | Health: {} | Status: {} | Docs: {}",
                    record.index(),
                    record.health(),
                    record.status(),
                    record.docsCount());
              });
    } catch (IOException e) {
      log.error("Failed to fetch indices: {}", e.getMessage());
    }
  }
}
