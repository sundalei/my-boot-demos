package com.example.controller;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/me")
public class EntraIdController {

  private final ObjectMapper objectMapper;

  public EntraIdController(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Endpoint 1: The Base64 Header Purist Route */
  @GetMapping("/claims")
  public ResponseEntity<Map<String, String>> getEnrichedClaims(
      @RequestHeader(value = "OIDC_CLAIM_preferred_username", defaultValue = "Not Provided")
          String username,
      @RequestHeader(value = "OIDC_CLAIM_name_b64", defaultValue = "Not Provided") String nameB64,
      @RequestHeader(value = "OIDC_CLAIM_email", defaultValue = "Not Provided") String email) {

    String decodedName = "Not Provided";

    if (!"Not Provided".equals(nameB64) && !nameB64.isEmpty()) {
      try {
        // Safely unpack the Chinese characters
        byte[] decodedBytes = Base64.getDecoder().decode(nameB64);
        decodedName = new String(decodedBytes, StandardCharsets.UTF_8);
      } catch (IllegalArgumentException e) {
        decodedName = "Decode Error";
      }
    }

    return ResponseEntity.ok(
        Map.of(
            "username", username,
            "name", decodedName,
            "email", email));
  }

  /** Endpoint 2: The Raw Token Route */
  @GetMapping("/token/raw")
  public ResponseEntity<String> getRawToken(
      @RequestHeader(value = "OIDC_id_token", required = false) String idToken) {

    if (idToken == null || idToken.isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing OIDC_id_token header");
    }
    return ResponseEntity.ok(idToken);
  }

  /** Endpoint 3: The Token Decoded Route */
  @GetMapping("/token/decoded")
  public ResponseEntity<Map<String, Object>> getDecodedTokenPayload(
      @RequestHeader(value = "OIDC_id_token", required = false) String idToken) {

    if (idToken == null || idToken.isEmpty()) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    try {
      String[] chunks = idToken.split("\\.");
      String decodedPayload = new String(Base64.getUrlDecoder().decode(chunks[1]));

      Map<String, Object> fullClaims =
          objectMapper.readValue(decodedPayload, new TypeReference<Map<String, Object>>() {});

      return ResponseEntity.ok(fullClaims);
    } catch (Exception e) {
      return ResponseEntity.internalServerError().build();
    }
  }
}
