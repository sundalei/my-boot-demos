package com.example.service;

import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GenericElasticService {

  private final RestClient restClient;

  public GenericElasticService(RestClient restClient) {
    this.restClient = restClient;
  }

  public Map<String, Object> index(String indexName, Map<String, Object> payload) {
    return restClient
        .put()
        .uri("/" + indexName)
        .contentType(MediaType.APPLICATION_JSON)
        .body(payload)
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }
}
