package com.example.repository;

import com.example.entry.MoneyEntry;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MoneyEntryRepository extends MongoRepository<MoneyEntry, String> {
  Optional<MoneyEntry> findByNotionId(String notionId);

  MoneyEntry findTopByOrderByLastEditedTimeDesc();
}
