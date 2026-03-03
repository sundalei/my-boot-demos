package com.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotionSyncService {

  private static final Logger LOG = LoggerFactory.getLogger(NotionSyncService.class);

  @Value("${notion.api.database-id}")
  private String databaseId;

  @Value("${notion.api.url}")
  private String notionApiUrl;

  public String syncData() {
    LOG.info("Starting Notion sync...");
    String url = notionApiUrl + databaseId + "/query";
    LOG.info("url is {}", url);
    return "hello";
  }
}
