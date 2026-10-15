package org.springframework.http.client;

import java.io.IOException;
import java.io.InputStream;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.support.HttpComponentsHeadersAdapter;
import org.springframework.util.MultiValueMap;

final class HttpComponentsClientHttpResponse implements ClientHttpResponse {
   private final ClassicHttpResponse httpResponse;
   private @Nullable HttpHeaders headers;

   HttpComponentsClientHttpResponse(ClassicHttpResponse httpResponse) {
      this.httpResponse = httpResponse;
   }

   @Override
   public HttpStatusCode getStatusCode() {
      return HttpStatusCode.valueOf(this.httpResponse.getCode());
   }

   @Override
   public String getStatusText() {
      return this.httpResponse.getReasonPhrase();
   }

   @Override
   public HttpHeaders getHeaders() {
      if (this.headers == null) {
         MultiValueMap<String, String> adapter = new HttpComponentsHeadersAdapter(this.httpResponse);
         HttpEntity httpEntity = this.httpResponse.getEntity();
         if (httpEntity != null) {
            if (httpEntity.getContentLength() == -1L) {
               adapter.remove("Content-Length");
            }

            if (httpEntity.getContentEncoding() == null) {
               adapter.remove("Content-Encoding");
            }
         }

         this.headers = HttpHeaders.readOnlyHttpHeaders(adapter);
      }

      return this.headers;
   }

   @Override
   public InputStream getBody() throws IOException {
      HttpEntity entity = this.httpResponse.getEntity();
      return entity != null ? entity.getContent() : InputStream.nullInputStream();
   }

   @Override
   public void close() {
      try {
         try {
            EntityUtils.consume(this.httpResponse.getEntity());
         } finally {
            this.httpResponse.close();
         }
      } catch (IOException var5) {
      }
   }
}
