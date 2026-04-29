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

  @Value("${my.super.secret}")
  private String mySecret;

  @Value("${spring.elasticsearch.uris}")
  private String elasticsearchUris;

  @Value("${spring.elasticsearch.password}")
  private String elasticsearchPassword;

  @Value("${spring.mongodb.uri}")
  private String mongodbUri;

  @GetMapping("/show-secret")
  public Map<String, Object> showSecret() {
    Map<String, Object> secrets = new HashMap<>();

    Map<String, String> elasticsearch = new HashMap<>();
    elasticsearch.put("uris", elasticsearchUris);
    elasticsearch.put("password", elasticsearchPassword);

    Map<String, String> mongodb = new HashMap<>();
    mongodb.put("uri", mongodbUri);

    secrets.put("elasticsearch", elasticsearch);
    secrets.put("mongodb", mongodb);
    secrets.put("my.super.secret", mySecret);

    return secrets;
  }
}
