package org.springframework.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.function.Supplier;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.io.entity.NullEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.util.StringUtils;

final class HttpComponentsClientHttpRequest extends AbstractStreamingClientHttpRequest {
   private final HttpClient httpClient;
   private final ClassicHttpRequest httpRequest;
   private final HttpContext httpContext;

   HttpComponentsClientHttpRequest(HttpClient client, ClassicHttpRequest request, HttpContext context) {
      this.httpClient = client;
      this.httpRequest = request;
      this.httpContext = context;
   }

   @Override
   public HttpMethod getMethod() {
      return HttpMethod.valueOf(this.httpRequest.getMethod());
   }

   @Override
   public URI getURI() {
      try {
         return this.httpRequest.getUri();
      } catch (URISyntaxException ex) {
         throw new IllegalStateException(ex.getMessage(), ex);
      }
   }

   HttpContext getHttpContext() {
      return this.httpContext;
   }

   @Override
   protected ClientHttpResponse executeInternal(HttpHeaders headers, StreamingHttpOutputMessage.@Nullable Body body) throws IOException {
      addHeaders(this.httpRequest, headers);
      if (body != null) {
         this.httpRequest.setEntity(new HttpComponentsClientHttpRequest.BodyEntity(headers, body));
      } else if (!Method.isSafe(this.httpRequest.getMethod())) {
         this.httpRequest.setEntity(NullEntity.INSTANCE);
      }

      ClassicHttpResponse httpResponse = this.httpClient.executeOpen(null, this.httpRequest, this.httpContext);
      return new HttpComponentsClientHttpResponse(httpResponse);
   }

   static void addHeaders(ClassicHttpRequest httpRequest, HttpHeaders headers) {
      headers.forEach((headerName, headerValues) -> {
         if ("Cookie".equalsIgnoreCase(headerName)) {
            String headerValue = StringUtils.collectionToDelimitedString(headerValues, "; ");
            httpRequest.addHeader(headerName, headerValue);
         } else if (!"Content-Length".equalsIgnoreCase(headerName) && !"Transfer-Encoding".equalsIgnoreCase(headerName)) {
            for (String headerValue : headerValues) {
               httpRequest.addHeader(headerName, headerValue);
            }
         }
      });
   }

   private static class BodyEntity implements HttpEntity {
      private final HttpHeaders headers;
      private final StreamingHttpOutputMessage.Body body;

      public BodyEntity(HttpHeaders headers, StreamingHttpOutputMessage.Body body) {
         this.headers = headers;
         this.body = body;
      }

      public long getContentLength() {
         return this.headers.getContentLength();
      }

      public @Nullable String getContentType() {
         return this.headers.getFirst("Content-Type");
      }

      public InputStream getContent() {
         throw new UnsupportedOperationException();
      }

      public void writeTo(OutputStream outStream) throws IOException {
         this.body.writeTo(outStream);
      }

      public boolean isRepeatable() {
         return this.body.repeatable();
      }

      public boolean isStreaming() {
         return false;
      }

      public @Nullable Supplier<List<? extends Header>> getTrailers() {
         return null;
      }

      public @Nullable String getContentEncoding() {
         return this.headers.getFirst("Content-Encoding");
      }

      public boolean isChunked() {
         return false;
      }

      public @Nullable Set<String> getTrailerNames() {
         return null;
      }

      public void close() {
      }
   }
}
