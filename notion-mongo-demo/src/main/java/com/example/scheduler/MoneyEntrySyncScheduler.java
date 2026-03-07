package com.example.scheduler;

import com.example.service.NotionSyncService;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
public class MoneyEntrySyncScheduler {

  private final NotionSyncService notionSyncService;

  public MoneyEntrySyncScheduler(NotionSyncService notionSyncService) {
    this.notionSyncService = notionSyncService;
  }

  @Scheduled(cron = "0 0 11 * * ?", zone = "Asia/Shanghai")
  public void scheduleSync() {
    notionSyncService.syncData();
  }
}
