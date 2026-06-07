package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class EntraIdConfig {

  @Bean
  ObjectMapper objectMapper() {
    return new ObjectMapper();
  }
}
