package com.example.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.IndicesResponse;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import java.io.IOException;
import java.util.Iterator;
import java.util.stream.Stream;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class MongoToElasticSyncService {

  private static final Logger log = LoggerFactory.getLogger(MongoToElasticSyncService.class);

  private final MongoTemplate mongoTemplate;
  private final ElasticsearchClient esClient;

  public MongoToElasticSyncService(MongoTemplate mongoTemplate, ElasticsearchClient esClient) {
    this.mongoTemplate = mongoTemplate;
    this.esClient = esClient;
  }

  public void sync() {
    log.info("Starting MongoDB to Elasticsearch sync...");
    clearOldIndex();

    Query query = new Query();
    try (Stream<Document> movieStream = mongoTemplate.stream(query, Document.class, "movies")) {

      BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
      int batchSize = 1000;
      int count = 0;
      int currentBatchCount = 0;
      Iterator<Document> cursor = movieStream.iterator();

      while (cursor.hasNext()) {
        Document doc = cursor.next();

        String id = doc.getObjectId("_id").toString();
        doc.remove("_id");

        if (doc.containsKey("year")) {
          String yearStr = doc.get("year").toString();
          String cleanYear = yearStr.replaceAll("[^0-9]", "");

          if (!cleanYear.isEmpty()) {
            doc.put("year", Long.parseLong(cleanYear));
          } else {
            doc.remove("year");
          }
        }

        bulkBuilder.operations(op -> op.index(idx -> idx.index("movies").id(id).document(doc)));

        count++;
        currentBatchCount++;

        if (currentBatchCount == batchSize) {
          executeBulk(bulkBuilder);
          bulkBuilder = new BulkRequest.Builder();
          currentBatchCount = 0;
          log.info("Index {} movies...", count);
        }
      }

      if (currentBatchCount > 0) {
        executeBulk(bulkBuilder);
        log.info("Finished! Total indexed: {}", count);
      }
    } catch (IOException e) {
      log.error("Error {}", e.getMessage());
    }
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

  private void executeBulk(BulkRequest.Builder bulkBuilder) throws IOException {
    BulkResponse response = esClient.bulk(bulkBuilder.build());

    if (response.errors()) {
      log.error("Batch contained errors! Identifying failures...");

      for (BulkResponseItem item : response.items()) {
        if (item.error() != null) {
          var error = item.error();
          if (error != null) {
            log.error(
                "Failed to index document ID: {} | Error Type: {} | Reason: {}",
                item.id(),
                error.type(),
                error.reason());
          }
        }
      }
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
