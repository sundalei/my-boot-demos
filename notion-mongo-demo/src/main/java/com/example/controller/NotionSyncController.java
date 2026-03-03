package com.example.controller;

import com.example.service.NotionSyncService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notion")
public class NotionSyncController {

  private final NotionSyncService notionSyncService;

  public NotionSyncController(NotionSyncService notionSyncService) {
    this.notionSyncService = notionSyncService;
  }

  @GetMapping
  public String syncData() {
    return notionSyncService.syncData();
  }
}
