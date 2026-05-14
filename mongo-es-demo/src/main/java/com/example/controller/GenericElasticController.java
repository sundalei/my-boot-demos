package com.example.controller;

import com.example.service.GenericElasticService;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/elastic")
public class GenericElasticController {

  private final GenericElasticService elasticService;

  public GenericElasticController(GenericElasticService elasticService) {
    this.elasticService = elasticService;
  }

  @PutMapping("/{indexName}")
  public Map<String, Object> index(
      @PathVariable String indexName, @RequestBody Map<String, Object> payload) {
    return elasticService.index(indexName, payload);
  }
}
