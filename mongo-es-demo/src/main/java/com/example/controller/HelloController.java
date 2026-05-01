package com.example.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class HelloController {

  @GetMapping("/ping/hello")
  public String greeting() {
    return "Hello";
  }

  @Value("${elasticsearch.base-url}")
  private String elasticsearchBaseUrl;

  @Value("${elasticsearch.password}")
  private String elasticsearchPassword;

  @Value("${spring.mongodb.uri}")
  private String mongodbUri;

  @GetMapping("/show-secret")
  public Map<String, Object> showSecret() {
    Map<String, Object> secrets = new HashMap<>();

    Map<String, String> elasticsearch = new HashMap<>();
    elasticsearch.put("base-url", elasticsearchBaseUrl);
    elasticsearch.put("password", elasticsearchPassword);

    Map<String, String> mongodb = new HashMap<>();
    mongodb.put("uri", mongodbUri);

    secrets.put("elasticsearch", elasticsearch);
    secrets.put("mongodb", mongodb);

    return secrets;
  }
}
