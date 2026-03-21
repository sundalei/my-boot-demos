package com.example.controller;

import com.example.service.SavingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/saving")
public class SavingController {

  private final SavingService savingService;

  public SavingController(SavingService savingService) {
    this.savingService = savingService;
  }

  @GetMapping("/all")
  public String allSavings() {
    return "All savings";
  }
}
