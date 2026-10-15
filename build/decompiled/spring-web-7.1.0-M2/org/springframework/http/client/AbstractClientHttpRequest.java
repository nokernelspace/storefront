package org.springframework.http.client;

import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;

public abstract class AbstractClientHttpRequest implements ClientHttpRequest {
   private final HttpHeaders headers = new HttpHeaders();
   private boolean executed = false;
   private @Nullable HttpHeaders readOnlyHeaders;
   private @Nullable Map<String, Object> attributes;

   @Override
   public final HttpHeaders getHeaders() {
      if (this.readOnlyHeaders != null) {
         return this.readOnlyHeaders;
      } else if (this.executed) {
         this.readOnlyHeaders = HttpHeaders.readOnlyHttpHeaders(this.headers);
         return this.readOnlyHeaders;
      } else {
         return this.headers;
      }
   }

   @Override
   public final OutputStream getBody() throws IOException {
      this.assertNotExecuted();
      return this.getBodyInternal(this.headers);
   }

   @Override
   public Map<String, Object> getAttributes() {
      Map<String, Object> attributes = this.attributes;
      if (attributes == null) {
         attributes = new LinkedHashMap<>();
         this.attributes = attributes;
      }

      return attributes;
   }

   @Override
   public final ClientHttpResponse execute() throws IOException {
      this.assertNotExecuted();
      ClientHttpResponse result = this.executeInternal(this.headers);
      this.executed = true;
      return result;
   }

   protected void assertNotExecuted() {
      Assert.state(!this.executed, "ClientHttpRequest already executed");
   }

   protected abstract OutputStream getBodyInternal(HttpHeaders headers) throws IOException;

   protected abstract ClientHttpResponse executeInternal(HttpHeaders headers) throws IOException;
}
