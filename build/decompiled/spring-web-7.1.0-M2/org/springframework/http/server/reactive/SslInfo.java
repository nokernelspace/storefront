package org.springframework.http.server.reactive;

import java.security.cert.X509Certificate;
import org.jspecify.annotations.Nullable;

public interface SslInfo {
   @Nullable String getSessionId();

   X509Certificate @Nullable [] getPeerCertificates();

   static SslInfo from(String sessionId) {
      return new DefaultSslInfo(sessionId, new X509Certificate[0]);
   }

   static SslInfo from(String sessionId, X509Certificate... peerCertificates) {
      return new DefaultSslInfo(sessionId, peerCertificates);
   }
}
