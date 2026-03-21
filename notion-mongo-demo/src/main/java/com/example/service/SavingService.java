package com.example.service;

import com.example.repository.SavingRepository;
import org.springframework.stereotype.Service;

@Service
public class SavingService {

  private final SavingRepository savingRepository;

  public SavingService(SavingRepository savingRepository) {
    this.savingRepository = savingRepository;
  }
}
