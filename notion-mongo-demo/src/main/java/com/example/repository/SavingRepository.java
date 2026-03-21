package com.example.repository;

import com.example.entry.Saving;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SavingRepository extends MongoRepository<Saving, String> {}
