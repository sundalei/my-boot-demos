package com.example.entry;

import java.time.Instant;
import java.time.LocalDateTime;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "money_entries")
public class MoneyEntry {

  @Id private String id;
  private String notionId;
  private Instant lastEditedTime; // Watermark for incremental sync
  private Double amount;
  private String remark;
  private LocalDateTime time;
  private String tag;

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

  public Double getAmount() {
    return amount;
  }

  public void setAmount(Double amount) {
    this.amount = amount;
  }

  public String getRemark() {
    return remark;
  }

  public void setRemark(String remark) {
    this.remark = remark;
  }

  public LocalDateTime getTime() {
    return time;
  }

  public void setTime(LocalDateTime time) {
    this.time = time;
  }

  public String getTag() {
    return tag;
  }

  public void setTag(String tag) {
    this.tag = tag;
  }

  @Override
  public String toString() {
    return "MoneyEntry [id="
        + id
        + ", notionId="
        + notionId
        + ", lastEditedTime="
        + lastEditedTime
        + ", amount="
        + amount
        + ", remark="
        + remark
        + ", time="
        + time
        + ", tag="
        + tag
        + "]";
  }
}
