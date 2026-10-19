package com.accountledger.clientservice.config;

import feign.Client;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
 * Disables TLS certificate and hostname checks for calls to Fineract.
 *
 * Only for a local Fineract with a self-signed certificate. It is off unless
 * fineract.ssl.trust-all=true, and must never be enabled against a real
 * server: with it on, anyone on the network can impersonate Fineract.
 */
@Configuration
@ConditionalOnProperty(name = "fineract.ssl.trust-all", havingValue = "true")
public class FineractSSLConfig {

  @Bean
  public Client feignClient() throws Exception {
    TrustManager[] trustAllCerts =
        new TrustManager[] {
          new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
              return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {}

            public void checkServerTrusted(X509Certificate[] certs, String authType) {}
          }
        };
    SSLContext sslContext = SSLContext.getInstance("SSL");
    sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
    return new Client.Default(sslContext.getSocketFactory(), (hostname, session) -> true);
  }
}
