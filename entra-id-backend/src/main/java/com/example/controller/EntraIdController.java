package com.example.controller;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class EntraIdController {

  /** 
   * Endpoint to verify headers injected by IdentityHeaderFilter and TokenRelay.
   * To test this via the Gateway, hit: http://localhost:8080/api/me/headers
   */
  @GetMapping("/headers")
  public ResponseEntity<Map<String, String>> getHeaders(
      @RequestHeader(value = "X-Auth-Sub", defaultValue = "Not Provided") String sub,
      @RequestHeader(value = "X-Auth-Name", defaultValue = "Not Provided") String name,
      @RequestHeader(value = "X-Auth-Email", defaultValue = "Not Provided") String email,
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader) {
      
    // The gateway URL-encodes the name to avoid non-ASCII header issues
    String decodedName = "Not Provided".equals(name) ? name : URLDecoder.decode(name, StandardCharsets.UTF_8);

    // Provide a snippet of the Bearer token if it exists
    String tokenPreview = "Not Provided";
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
       String token = authHeader.substring(7);
       tokenPreview = token.length() > 20 ? token.substring(0, 20) + "..." : token;
    }

    return ResponseEntity.ok(
        Map.of(
            "sub", sub,
            "name", decodedName,
            "email", email,
            "bearer_token_preview", tokenPreview
        ));
  }

  /**
   * Endpoint to decode and view the raw JWT payload (claims) sent via TokenRelay.
   * To test this via the Gateway, hit: http://localhost:8080/api/me/token/decoded
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
