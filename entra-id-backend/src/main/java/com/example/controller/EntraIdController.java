package com.example.controller;

import java.util.Base64;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api")
public class EntraIdController {

  private final ObjectMapper objectMapper;

  // Inject Jackson ObjectMapper
  public EntraIdController(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @GetMapping("/me")
  public ResponseEntity<Map<String, Object>> getCurrentUser(
      @RequestHeader(value = "OIDC_id_token", required = false) String idToken) {

    if (idToken == null || idToken.isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    try {
      // A JWT is structured as: header.payload.signature
      String[] chunks = idToken.split("\\.");
      if (chunks.length < 2) {
        return ResponseEntity.badRequest().build();
      }

      // Decode the Base64 URL-safe payload
      String decodedPayload = new String(Base64.getUrlDecoder().decode(chunks[1]));

      // Parse the JSON string into a Map
      Map<String, Object> claims =
          objectMapper.readValue(decodedPayload, new TypeReference<Map<String, Object>>() {});

      // You can now access claims like claims.get("preferred_username") or claims.get("name")
      return ResponseEntity.ok(claims);

    } catch (Exception e) {
      return ResponseEntity.internalServerError().build();
    }
  }
}
