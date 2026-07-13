package com.example;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class MeController {

  @GetMapping("/me")
  public Mono<Map<String, Object>> me(@AuthenticationPrincipal OidcUser user) {
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("name", user.getName());
    info.put("given_name", user.getGivenName());
    info.put("preferredUsername", user.getPreferredUsername());
    info.put("email", user.getEmail());
    info.put("claims", user.getClaims());
    return Mono.just(info);
  }
}
