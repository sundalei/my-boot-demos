package com.example.entry;

import java.math.BigDecimal;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "savings")
@Data
public class Saving {

  @Id private String id;

  private String account;

  private BigDecimal amount;
}
