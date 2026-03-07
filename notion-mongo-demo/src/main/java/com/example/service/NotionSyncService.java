package com.example.service;

import com.example.entry.MoneyEntry;
import com.example.repository.MoneyEntryRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

  public void syncData() {
    LOG.info("Starting Notion sync...");
    String url = notionApiUrl + databaseId + "/query";

    // Construct the Base Request Body
    ObjectNode requestBody = mapper.createObjectNode();

    // Add Incremental Sync Filter (if we have a watermark)
    MoneyEntry latestEntry = repository.findTopByOrderByLastEditedTimeDesc();
    if (latestEntry != null && latestEntry.getLastEditedTime() != null) {
      LOG.info(
          "Incremental sync: Fetching updates on or after {}", latestEntry.getLastEditedTime());

      ObjectNode filter = mapper.createObjectNode();
      filter.put("timestamp", "last_edited_time");

      ObjectNode lastEditedTime = mapper.createObjectNode();
      lastEditedTime.put("after", latestEntry.getLastEditedTime().toString());

      filter.set("last_edited_time", lastEditedTime);
      requestBody.set("filter", filter);
    } else {
      LOG.info("First run detected: Fetching ALL records.");
    }

    // Pagination Loop
    boolean hasMore = true;
    String nextCursor = null;
    int totalSaved = 0;

    while (hasMore) {
      if (nextCursor != null && !nextCursor.equals("null")) {
        requestBody.put("start_cursor", nextCursor);
      }

      // Execute the request using RestClient
      LOG.info("request body {}", requestBody);
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
          if (entry.isEmpty()) {
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

          // Properties based on the money_entry database setup
          moneyEntry.setAmount(props.path("Amount").path("number").asDouble(0.0));

          JsonNode remarkNode = props.path("Remark").path("rich_text");
          if (remarkNode.isArray() && !remarkNode.isEmpty()) {
            moneyEntry.setRemark(remarkNode.get(0).path("plain_text").asString(""));
          }

          JsonNode timeNode = props.path("Time").path("rich_text");
          if (timeNode.isArray() && !timeNode.isEmpty()) {
            String dateStr = timeNode.get(0).path("plain_text").asString();
            moneyEntry.setTime(
                LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
          }

          JsonNode tagNode = props.path("Tag").path("rich_text");
          if (tagNode.isArray() && !tagNode.isEmpty()) {
            moneyEntry.setTag(tagNode.get(0).path("plain_text").asString(""));
          }

          LOG.info("{} money entry {}", entry.isPresent() ? "Updating" : "Saving", moneyEntry);

          repository.save(moneyEntry);
          totalSaved++;
        }
      }

      // Update Pagination Flags
      hasMore = response.path("has_more").asBoolean(false);

      if (hasMore) {
        // To respect Notion's rate limit (3 req/sec)
        try {
          Thread.sleep(500);
          nextCursor = response.path("next_cursor").asString(null);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Notion sync interrupted during pagination", e);
        }
      }
    }
    LOG.info("Sync complete. Total items processed: {}", totalSaved);
  }
}
