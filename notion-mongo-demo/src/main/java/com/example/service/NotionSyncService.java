package com.example.service;

import com.example.entry.MoneyEntry;
import com.example.repository.MoneyEntryRepository;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Supplier;
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
  private final RateLimiter rateLimiter;
  private final Retry retry;

  @Value("${notion.api.token}")
  private String notionToken;

  @Value("${notion.api.database-id}")
  private String databaseId;

  @Value("${notion.api.version}")
  private String notionVersion;

  @Value("${notion.api.url}")
  private String notionApiUrl;

  public NotionSyncService(
      MoneyEntryRepository repository,
      RestClient.Builder restClientBuilder,
      RateLimiter rateLimiter,
      Retry retry) {
    this.repository = repository;
    this.restClient = restClientBuilder.build();
    this.mapper = new ObjectMapper();
    this.rateLimiter = rateLimiter;
    this.retry = retry;
  }

  public void syncData() {
    LOG.info("Starting Notion sync...");
    String url = notionApiUrl + databaseId + "/query";

    ObjectNode requestBody = buildRequestBody();

    // Pagination Loop
    boolean hasMore = true;
    String nextCursor = null;
    int totalSaved = 0;

    while (hasMore) {
      if (nextCursor != null && !nextCursor.equals("null")) {
        requestBody.put("start_cursor", nextCursor);
      }

      JsonNode response = executeApiCall(url, requestBody);
      if (response == null) {
        break;
      }

      totalSaved += processResponse(response);

      // Update Pagination Flags
      hasMore = response.path("has_more").asBoolean(false);
      nextCursor = response.path("next_cursor").asString(null);
    }
    LOG.info("Sync complete. Total items processed: {}", totalSaved);
  }

  private ObjectNode buildRequestBody() {
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

    return requestBody;
  }

  private JsonNode executeApiCall(String url, ObjectNode requestBody) {
    LOG.info("request body {}", requestBody);

    // Decorate the API call with RateLimiter first and Retry second
    // to ensure we respect rate limits while also handling transient failures.
    Supplier<JsonNode> apiCall =
        () ->
            restClient
                .post()
                .uri(url)
                .header("Authorization", "Bearer " + notionToken)
                .header("Notion-Version", notionVersion)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(JsonNode.class);

    Supplier<JsonNode> rateLimitedCall = RateLimiter.decorateSupplier(rateLimiter, apiCall);
    Supplier<JsonNode> retryingRateLimitedCall = Retry.decorateSupplier(retry, rateLimitedCall);

    return retryingRateLimitedCall.get();
  }

  private int processResponse(JsonNode response) {
    JsonNode results = response.path("results");
    int savedCount = 0;

    if (results.isArray()) {
      for (JsonNode page : results) {
        MoneyEntry moneyEntry = parseMoneyEntry(page);
        upsertMoneyEntry(moneyEntry);
        savedCount++;
      }
    }

    return savedCount;
  }

  private MoneyEntry parseMoneyEntry(JsonNode page) {
    String notionId = page.path("id").asString();
    JsonNode props = page.path("properties");

    MoneyEntry moneyEntry = new MoneyEntry();
    moneyEntry.setNotionId(notionId);

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

    return moneyEntry;
  }

  private void upsertMoneyEntry(MoneyEntry moneyEntry) {
    Optional<MoneyEntry> existingEntry = repository.findByNotionId(moneyEntry.getNotionId());
    boolean isUpdate = existingEntry.isPresent();

    if (isUpdate) {
      MoneyEntry existing = existingEntry.get();
      // Update existing entry with new data
      existing.setLastEditedTime(moneyEntry.getLastEditedTime());
      existing.setAmount(moneyEntry.getAmount());
      existing.setRemark(moneyEntry.getRemark());
      existing.setTime(moneyEntry.getTime());
      existing.setTag(moneyEntry.getTag());
      moneyEntry = existing;
    }

    LOG.info("{} money entry {}", isUpdate ? "Updating" : "Saving", moneyEntry);
    repository.save(moneyEntry);
  }
}
