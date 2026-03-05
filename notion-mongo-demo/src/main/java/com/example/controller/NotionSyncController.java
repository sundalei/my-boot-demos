package com.example.controller;

import com.example.service.NotionSyncService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/notion")
public class NotionSyncController {

  private final NotionSyncService notionSyncService;

  public NotionSyncController(NotionSyncService notionSyncService) {
    this.notionSyncService = notionSyncService;
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
  public JsonNode syncData() {
    return notionSyncService.syncData();
  }
}
