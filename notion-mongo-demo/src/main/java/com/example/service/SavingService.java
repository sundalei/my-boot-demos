package com.example.service;

import com.example.entry.Saving;
import com.example.repository.SavingRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SavingService {

  private final SavingRepository savingRepository;

  public SavingService(SavingRepository savingRepository) {
    this.savingRepository = savingRepository;
  }

  public List<Saving> getAllSavings() {
    return savingRepository.findAll();
  }
}
