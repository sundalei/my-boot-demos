package com.example.controller;

import com.example.service.MongoToElasticSyncService;
import jakarta.annotation.Nonnull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MongoToElasticSyncController {

  private final MongoToElasticSyncService syncService;

  public MongoToElasticSyncController(@Nonnull MongoToElasticSyncService syncService) {
    this.syncService = syncService;
  }

  @GetMapping("/sync")
  public String sync() {
    syncService.sync();
    return "sync";
  }

  @GetMapping("/match-phrase-query")
  public void matchPhraseQuery() {
    syncService.matchPhraseQuery();
  }
}
