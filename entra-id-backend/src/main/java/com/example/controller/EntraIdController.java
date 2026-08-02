package com.example.controller;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class EntraIdController {

  /**
   * Endpoint to verify the Maverics-compatible identity headers injected by IdentityHeaderFilter. To
   * test this via the Gateway, hit: http://localhost:8080/api/me/headers
   */
  @GetMapping("/headers")
  public ResponseEntity<Map<String, String>> getHeaders(
      @RequestHeader(value = "Remote-User", defaultValue = "Not Provided") String remoteUser,
      @RequestHeader(value = "X-Remote-User", defaultValue = "Not Provided") String xRemoteUser,
      @RequestHeader(value = "Smuniversalid", defaultValue = "Not Provided") String smUniversalId,
      @RequestHeader(value = "X-Auth-Uids", defaultValue = "Not Provided") String uids,
      @RequestHeader(value = "X-Auth-Time", defaultValue = "Not Provided") String authTime,
      @RequestHeader(value = "X-Auth-Strength", defaultValue = "Not Provided") String authStrength,
      @RequestHeader(value = "X-Auth-Location", defaultValue = "Not Provided") String location,
      @RequestHeader(value = "X-Auth-Channel", defaultValue = "Not Provided") String channel,
      @RequestHeader(value = "X-Auth-Mandator", defaultValue = "Not Provided") String mandator) {

    Map<String, String> body = new LinkedHashMap<>();
    body.put("Remote-User", remoteUser);
    body.put("X-Remote-User", xRemoteUser);
    body.put("Smuniversalid", smUniversalId);
    body.put("X-Auth-Uids", uids);
    body.put("X-Auth-Time", authTime);
    body.put("X-Auth-Strength", authStrength);
    body.put("X-Auth-Location", location);
    body.put("X-Auth-Channel", channel);
    body.put("X-Auth-Mandator", mandator);
    return ResponseEntity.ok(body);
  }

  /**
   * Endpoint to decode and view the raw JWT payload (claims) sent via TokenRelay. To test this via
   * the Gateway, hit: http://localhost:8080/api/me/token/decoded
   */
  @GetMapping(value = "/token/decoded", produces = "application/json")
  public ResponseEntity<String> getDecodedToken(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body("{\"error\": \"Missing or invalid Authorization header (Bearer token expected)\"}");
    }

    String token = authHeader.substring(7);
    String[] chunks = token.split("\\.");

    if (chunks.length < 2) {
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body("{\"error\": \"Invalid JWT structure\"}");
    }

    try {
      // Decode the payload (second part of the JWT)
      String payload = new String(Base64.getUrlDecoder().decode(chunks[1]), StandardCharsets.UTF_8);
      return ResponseEntity.ok(payload);
    } catch (Exception e) {
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body("{\"error\": \"Failed to decode token\"}");
    }
  }
}
