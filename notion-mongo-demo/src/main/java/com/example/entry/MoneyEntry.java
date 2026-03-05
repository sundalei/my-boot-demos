package com.example.entry;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "money_entries")
public class MoneyEntry {

  @Id private String id;
  private String notionId;
  private Instant lastEditedTime; // Watermark for incremental sync

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getNotionId() {
    return notionId;
  }

  public void setNotionId(String notionId) {
    this.notionId = notionId;
  }

  public Instant getLastEditedTime() {
    return lastEditedTime;
  }

  public void setLastEditedTime(Instant lastEditedTime) {
    this.lastEditedTime = lastEditedTime;
  }
}
