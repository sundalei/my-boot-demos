package com.example.service;

import com.example.entry.MoneyEntry;
import com.example.entry.Saving;
import com.example.entry.SyncType;
import com.example.repository.MoneyEntryRepository;
import com.example.repository.SavingRepository;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@Slf4j
public class NotionSyncService {

  private final MoneyEntryRepository repository;
  private final SavingRepository savingRepository;
  private final RestClient restClient;
  private final ObjectMapper mapper;
  private final RateLimiter rateLimiter;
  private final Retry retry;

  @Value("${notion.api.token}")
  private String notionToken;

  @Value("${notion.api.database-id}")
  private String databaseId;

  @Value("${notion.api.saving-database-id}")
  private String savingDatabaseId;

  @Value("${notion.api.version}")
  private String notionVersion;

  @Value("${notion.api.url}")
  private String notionApiUrl;

  public NotionSyncService(
      MoneyEntryRepository repository,
      SavingRepository savingRepository,
      RestClient.Builder restClientBuilder,
      RateLimiter rateLimiter,
      Retry retry) {
    this.repository = repository;
    this.savingRepository = savingRepository;
    this.restClient = restClientBuilder.build();
    this.mapper = new ObjectMapper();
    this.rateLimiter = rateLimiter;
    this.retry = retry;
  }

  public void sync() {
    log.info("Starting MoneyEntry sync...");
    syncMoneyEntryData();
    log.info("Starting Saving sync...");
    syncSavingData();
  }

  public void syncMoneyEntryData() {
    syncData(SyncType.MONEY_ENTRY);
  }

  public void syncSavingData() {
    syncData(SyncType.SAVING);
  }

  private void syncData(SyncType syncType) {
    String url = buildNotionQueryUrl(syncType);
    ObjectNode requestBodyTemplate = buildRequestBody(syncType);

    boolean hasMore = true;
    String nextCursor = null;
    int totalSaved = 0;

    while (hasMore) {
      ObjectNode requestBody = requestBodyTemplate.deepCopy();

      if (nextCursor != null && !"null".equals(nextCursor)) {
        requestBody.put("start_cursor", nextCursor);
      }

      JsonNode response = executeApiCall(url, requestBody);
      if (response == null) {
        log.warn("{} sync: received null response from Notion API", syncType);
        break;
      }

      totalSaved += processResponse(response, syncType);
      hasMore = response.path("has_more").asBoolean(false);
      nextCursor = response.path("next_cursor").asString(null);
    }

    log.info("{} sync complete. Total items processed: {}", syncType, totalSaved);
  }

  private String buildNotionQueryUrl(SyncType syncType) {
    String databaseIdToQuery =
        switch (syncType) {
          case MONEY_ENTRY -> databaseId;
          case SAVING -> savingDatabaseId;
          default -> throw new IllegalArgumentException("Unsupported SyncType: " + syncType);
        };

    if (databaseIdToQuery == null || databaseIdToQuery.isBlank()) {
      throw new IllegalStateException("Notion database ID is not configured for " + syncType);
    }

    String baseUrl = notionApiUrl;
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalStateException("Notion API URL is not configured");
    }

    if (!baseUrl.endsWith("/")) {
      baseUrl += "/";
    }

    return baseUrl + databaseIdToQuery + "/query";
  }

  private ObjectNode buildRequestBody(SyncType syncType) {
    ObjectNode requestBody = mapper.createObjectNode();
    Instant lastEditedTime = resolveLatestEditedTime(syncType);

    if (lastEditedTime != null) {
      log.info("Incremental sync: Fetching updates on or after {}", lastEditedTime);
      requestBody.set("filter", buildLastEditedTimeFilter(lastEditedTime));
    } else {
      log.info("First run detected: Fetching ALL records.");
    }

    return requestBody;
  }

  private Instant resolveLatestEditedTime(SyncType syncType) {
    return switch (syncType) {
      case MONEY_ENTRY -> {
        MoneyEntry latestMoneyEntry = repository.findTopByOrderByLastEditedTimeDesc();
        yield latestMoneyEntry != null ? latestMoneyEntry.getLastEditedTime() : null;
      }
      case SAVING -> {
        var latestSaving = savingRepository.findTopByOrderByLastEditedTimeDesc();
        yield latestSaving != null ? latestSaving.getLastEditedTime() : null;
      }
      default -> throw new IllegalArgumentException("Unsupported SyncType: " + syncType);
    };
  }

  private ObjectNode buildLastEditedTimeFilter(Instant startTime) {
    ObjectNode filter = mapper.createObjectNode();
    filter.put("timestamp", "last_edited_time");

    ObjectNode lastEditedTime = mapper.createObjectNode();
    lastEditedTime.put("after", startTime.toString());

    filter.set("last_edited_time", lastEditedTime);
    return filter;
  }

  private JsonNode executeApiCall(String url, ObjectNode requestBody) {
    log.info("request body {}", requestBody);

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

  private int processResponse(JsonNode response, SyncType syncType) {
    JsonNode results = response.path("results");
    int savedCount = 0;

    if (results.isArray()) {
      for (JsonNode page : results) {
        switch (syncType) {
          case MONEY_ENTRY -> {
            MoneyEntry moneyEntry = parseMoneyEntry(page);
            upsertMoneyEntry(moneyEntry);
          }
          case SAVING -> {
            Saving saving = parseSaving(page);
            upsertSaving(saving);
          }
        }
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

  private Saving parseSaving(JsonNode page) {
    String notionId = page.path("id").asString();
    JsonNode props = page.path("properties");

    Saving saving = new Saving();
    saving.setNotionId(notionId);

    // Watermark timestamp
    String editedTimeStr = page.path("last_edited_time").asString(null);
    if (editedTimeStr != null) {
      saving.setLastEditedTime(Instant.parse(editedTimeStr));
    }

    // Properties based on the saving database setup
    saving.setAmount(BigDecimal.valueOf(props.path("Amount").path("number").asDouble(0.0)));

    JsonNode accountNode = props.path("Account").path("rich_text");
    if (accountNode.isArray() && !accountNode.isEmpty()) {
      saving.setAccount(accountNode.get(0).path("plain_text").asString(""));
    }

    return saving;
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

    log.info("{} money entry {}", isUpdate ? "Updating" : "Saving", moneyEntry);
    repository.save(moneyEntry);
  }

  private void upsertSaving(Saving saving) {
    Optional<Saving> existingSaving = savingRepository.findByNotionId(saving.getNotionId());
    boolean isUpdate = existingSaving.isPresent();

    if (isUpdate) {
      Saving existing = existingSaving.get();
      // Update existing saving with new data
      existing.setLastEditedTime(saving.getLastEditedTime());
      existing.setAmount(saving.getAmount());
      existing.setAccount(saving.getAccount());
      saving = existing;
    }

    log.info("{} saving {}", isUpdate ? "Updating" : "Saving", saving);
    savingRepository.save(saving);
  }
}
