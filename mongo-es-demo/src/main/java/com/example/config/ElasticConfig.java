package com.example.config;

import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ElasticConfig {

  @Value("${spring.elasticsearch.username}")
  private String username;

  @Value("${spring.elasticsearch.password}")
  private String password;

  @Bean
  public RestClient restClient() throws Exception {

    TrustManager[] trustAllCerts =
        new TrustManager[] {
          new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {}

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {}

            @Override
            public X509Certificate[] getAcceptedIssuers() {
              return null;
            }
          }
        };

    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(null, trustAllCerts, new SecureRandom());

    HttpClient httpClient = HttpClient.newBuilder().sslContext(sslContext).build();
    ClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    String authHeader =
        "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());

    return RestClient.builder()
        .baseUrl("https://75.127.15.212:9200") // Point to your Master node
        .defaultHeader("Authorization", authHeader)
        .requestFactory(requestFactory)
        .build();
  }
}
