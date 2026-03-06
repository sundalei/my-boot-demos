package com.example.service;

import com.example.entry.MoneyEntry;
import com.example.repository.MoneyEntryRepository;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class NotionSyncService {

  private static final Logger LOG = LoggerFactory.getLogger(NotionSyncService.class);

  private final MoneyEntryRepository repository;
  private final RestClient restClient;
  private final ObjectMapper mapper;

  @Value("${notion.api.token}")
  private String notionToken;

  @Value("${notion.api.database-id}")
  private String databaseId;

  @Value("${notion.api.version}")
  private String notionVersion;

  @Value("${notion.api.url}")
  private String notionApiUrl;

  public NotionSyncService(MoneyEntryRepository repository, RestClient.Builder restClientBuilder) {
    this.repository = repository;
    this.restClient = restClientBuilder.build();
    this.mapper = new ObjectMapper();
  }

  public JsonNode syncData() {
    LOG.info("Starting Notion sync...");
    String url = notionApiUrl + databaseId + "/query";

    // Construct the Base Request Body
    ObjectNode requestBody = mapper.createObjectNode();

    // Add Incremental Sync Filter (if we have a watermark)
    MoneyEntry latestEntry = repository.findTopByOrderByLastEditedTimeDesc();
    if (latestEntry != null && latestEntry.getLastEditedTime() != null) {
      // TODO Add filter
    } else {
      LOG.info("First run detected: Fetching ALL records.");
    }

    // Pagination Loop
    boolean hasMore = true;

    while (hasMore) {

      // Execute the request using RestClient
      JsonNode response =
          restClient
              .post()
              .uri(url)
              .header("Authorization", "Bearer " + notionToken)
              .header("Notion-Version", notionVersion)
              .contentType(MediaType.APPLICATION_JSON)
              .body(requestBody)
              .retrieve()
              .body(JsonNode.class);

      if (response == null) {
        break;
      }

      JsonNode results = response.path("results");

      // Parse and Save
      if (results.isArray()) {
        for (JsonNode page : results) {
          String notionId = page.path("id").asString();
          JsonNode props = page.path("properties");

          // Upsert logic
          Optional<MoneyEntry> entry = repository.findByNotionId(notionId);
          MoneyEntry moneyEntry;
          if (!entry.isPresent()) {
            moneyEntry = new MoneyEntry();
            moneyEntry.setNotionId(notionId);
          } else {
            moneyEntry = entry.get();
          }

          // Watermark timestamp
          String editedTimeStr = page.path("last_edited_time").asString(null);
          if (editedTimeStr != null) {
            moneyEntry.setLastEditedTime(Instant.parse(editedTimeStr));
          }

          LOG.info("notion id {} exist? {}", notionId, entry.isPresent());
          LOG.info("notionId {}", notionId);
        }
      }

      return response;
    }
    return null;
  }
}
