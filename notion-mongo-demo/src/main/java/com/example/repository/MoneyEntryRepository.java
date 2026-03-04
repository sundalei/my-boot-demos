package com.example.repository;

import com.example.entry.MoneyEntry;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MoneyEntryRepository extends MongoRepository<MoneyEntry, String> {
  MoneyEntry findTopByOrderByLastEditedTimeDesc();
}
