package com.example.entry;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "savings")
@Data
public class Saving {

  @Id private String id;

  private String notionId;

  private String account;

  private BigDecimal amount;

  private Instant lastEditedTime; // Watermark for incremental sync
}
