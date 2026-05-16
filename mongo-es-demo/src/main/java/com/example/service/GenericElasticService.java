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
    return callElasticRequest("/" + indexName, payload);
  }

  public Map<String, Object> save(String indexName, int id, Map<String, Object> payload) {
    return callElasticRequest("/" + indexName + "/_doc/" + id, payload);
  }

  private Map<String, Object> callElasticRequest(String url, Map<String, Object> payload) {
    return restClient
        .put()
        .uri(url)
        .contentType(MediaType.APPLICATION_JSON)
        .body(payload)
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }
}
