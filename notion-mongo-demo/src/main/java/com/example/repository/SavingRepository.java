package com.example.repository;

import com.example.entry.Saving;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SavingRepository extends MongoRepository<Saving, String> {
  Optional<Saving> findByNotionId(String notionId);

  Saving findTopByOrderByLastEditedTimeDesc();
}
