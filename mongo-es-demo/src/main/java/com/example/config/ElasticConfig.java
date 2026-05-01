package com.example.config;

import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.autoconfigure.RestClientSsl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ElasticConfig {

  @Value("${elasticsearch.base-url}")
  private String elasticsearchBaseUrl;

  @Value("${elasticsearch.username}")
  private String username;

  @Value("${elasticsearch.password}")
  private String password;

  @Bean
  public RestClient elasticsearchRestClient(
      RestClient.Builder restClientBuilder, RestClientSsl ssl) {
    return restClientBuilder
        .baseUrl(elasticsearchBaseUrl)
        .defaultHeader("Authorization", createAuthHeader())
        .apply(ssl.fromBundle("elastic-client"))
        .build();
  }

  private String createAuthHeader() {
    return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
  }
}
