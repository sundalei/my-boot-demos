package com.example.config;

import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.autoconfigure.RestClientSsl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Configuration
public class ElasticConfig {

  @Value("${elasticsearch.base-url}")
  private String elasticsearchBaseUrl;

  @Value("${elasticsearch.username}")
  private String username;

  @Value("${elasticsearch.password}")
  private String password;

  @Value("${elasticsearch.ssl-bundle:}")
  private String sslBundle;

  @Bean
  public RestClient elasticsearchRestClient(
      RestClient.Builder restClientBuilder, RestClientSsl ssl) {
    restClientBuilder
        .baseUrl(elasticsearchBaseUrl)
        .defaultHeader("Authorization", createAuthHeader());
    if (StringUtils.hasText(sslBundle)) {
      restClientBuilder.apply(ssl.fromBundle(sslBundle)); // self-signed: trust custom CA
    }
    return restClientBuilder.build();
  }

  private String createAuthHeader() {
    return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
  }
}
