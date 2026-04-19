package com.example.service;

import java.util.Iterator;
import java.util.stream.Stream;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class MongoToElasticSyncService {

  private static final Logger log = LoggerFactory.getLogger(MongoToElasticSyncService.class);

  private final MongoTemplate mongoTemplate;
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public MongoToElasticSyncService(MongoTemplate mongoTemplate, RestClient restClient) {
    this.mongoTemplate = mongoTemplate;
    this.restClient = restClient;
    this.objectMapper = new ObjectMapper();
  }

  public void sync() {
    log.info("Starting MongoDB to Elasticsearch sync...");
    clearOldIndex();

    Query query = new Query();
    try (Stream<Document> movieStream = mongoTemplate.stream(query, Document.class, "movies")) {

      StringBuilder bulkNdJsonBuilder = new StringBuilder();
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

        // Action and Metadata line
        String actionMetaData =
            String.format("{\"index\":{\"_index\":\"movies\",\"_id\":\"%s\"}}\n", id);
        bulkNdJsonBuilder.append(actionMetaData);

        // Document Data line
        String docJson = objectMapper.writeValueAsString(doc);
        bulkNdJsonBuilder.append(docJson).append("\n");

        count++;
        currentBatchCount++;

        if (currentBatchCount == batchSize) {
          executeBulk(bulkNdJsonBuilder.toString());
          bulkNdJsonBuilder.setLength(0);
          currentBatchCount = 0;
          log.info("Index {} movies...", count);
        }
      }

      if (currentBatchCount > 0) {
        executeBulk(bulkNdJsonBuilder.toString());
        log.info("Finished! Total indexed: {}", count);
      }
    }
  }

  private void clearOldIndex() {
    try {
      ResponseEntity<Void> response =
          restClient.head().uri("/movies").retrieve().toBodilessEntity();

      if (response.getStatusCode().is2xxSuccessful()) {
        restClient.delete().uri("/movies").retrieve().toBodilessEntity();
        log.info("Old 'movies' index deleted.");
      }
    } catch (HttpClientErrorException.NotFound e) {
      log.info("Index 'movies' does not exist yet. Proceeding...");
    } catch (Exception e) {
      log.error("Failed to clear index: {}", e.getMessage());
    }
  }

  private void executeBulk(String bulkNdJson) {
    try {
      ResponseEntity<JsonNode> response =
          restClient
              .post()
              .uri("/_bulk")
              .contentType(MediaType.parseMediaType("application/x-ndjson;charset=UTF-8"))
              .body(bulkNdJson)
              .retrieve()
              .toEntity(JsonNode.class);

      JsonNode body = response.getBody();
      if (body != null && body.has("errors") && body.get("errors").asBoolean()) {
        log.error("Batch contained errors! Identifying failures...");

        JsonNode items = body.get("items");
        if (items != null && items.isArray()) {
          for (JsonNode item : items) {
            JsonNode indexAction = item.get("index");
            if (indexAction != null && indexAction.has("error")) {
              String failedId =
                  indexAction.has("_id") ? indexAction.get("_id").asString() : "unknown";
              JsonNode errorNode = indexAction.get("error");
              log.error(
                  "Failed to index document ID: {} | Error Type: {} | Reason: {}",
                  failedId,
                  errorNode.path("type").asString(),
                  errorNode.path("reason").asString());
            }
          }
        }
      }
    } catch (Exception e) {
      log.error("Network or execution error during bulk insert: {}", e.getMessage());
    }
  }
}
