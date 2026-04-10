package com.example.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class MongoToElasticSyncService {

  private static final Logger log = LoggerFactory.getLogger(MongoToElasticSyncService.class);

  private final ElasticsearchClient esClient;

  public MongoToElasticSyncService(ElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  public void sync() {
    log.info("Starting MongoDB to Elasticsearch sync...");
    clearOldIndex();
  }

  private void clearOldIndex() {
    try {
      boolean exists = esClient.indices().exists(ex -> ex.index("movies")).value();
      if (exists) {
        esClient.indices().delete(del -> del.index("movies"));
        log.info("Old 'movies' index deleted.");
      }
    } catch (IOException e) {
      log.info("Failed to clear index: {}", e.getMessage());
    }
  }

  public void listIndices() {
    try {

      // Check if the index exists first
      IndicesResponse response = esClient.cat().indices();

      log.info("Elasticsearch indices");

      response
          .indices()
          .forEach(
              record ->
                  log.info(
                      "Name: {} | Health: {} | Status: {} | Docs: {}",
                      record.index(),
                      record.health(),
                      record.status(),
                      record.docsCount()));
    } catch (IOException e) {
      log.error("Failed to fetch indices: {}", e.getMessage());
    }
  }

  public void matchPhraseQuery() {
    log.info("match phrase query");
    try {
      SearchResponse<JsonNode> response =
          esClient.search(
              s ->
                  s.index("books")
                      .query(
                          q ->
                              q.matchPhrase(
                                  m ->
                                      m.field("synopsis")
                                          .query("must-have book for every Java programmer"))),
              JsonNode.class);

      log.info("Search result");
      response
          .hits()
          .hits()
          .forEach(hit -> log.info("Document ID: {}, Source: {}", hit.id(), hit.source()));
    } catch (IOException e) {
      log.error("Failed to execute match phrase query: {}", e.getMessage());
    }
  }
}
