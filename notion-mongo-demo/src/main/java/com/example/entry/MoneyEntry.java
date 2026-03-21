package com.example.entry;

import java.time.Instant;
import java.time.LocalDateTime;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "money_entries")
@Data
public class MoneyEntry {

  @Id private String id;
  private String notionId;
  private Instant lastEditedTime; // Watermark for incremental sync
  private Double amount;
  private String remark;
  private LocalDateTime time;
  private String tag;
}
