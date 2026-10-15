package org.springframework.http.server;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import org.jspecify.annotations.Nullable;
import org.springframework.core.SpringProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;

public class ServletServerHttpResponse implements ServerHttpResponse {
   public static final String FLUSH_ENABLED_PROPERTY_NAME = "spring.http.response.flush.enabled";
   private final boolean flushEnabled = SpringProperties.getFlag("spring.http.response.flush.enabled");
   private final HttpServletResponse servletResponse;
   private final HttpHeaders headers;
   private boolean headersWritten = false;
   private boolean bodyUsed = false;
   private @Nullable HttpHeaders readOnlyHeaders;

   public ServletServerHttpResponse(HttpServletResponse servletResponse) {
      Assert.notNull(servletResponse, "HttpServletResponse must not be null");
      this.servletResponse = servletResponse;
      this.headers = new HttpHeaders(new ServletResponseHeadersAdapter(servletResponse));
   }

   public HttpServletResponse getServletResponse() {
      return this.servletResponse;
   }

   @Override
   public void setStatusCode(HttpStatusCode status) {
      Assert.notNull(status, "HttpStatusCode must not be null");
      this.servletResponse.setStatus(status.value());
   }

   @Override
   public HttpHeaders getHeaders() {
      if (this.readOnlyHeaders != null) {
         return this.readOnlyHeaders;
      } else if (this.headersWritten) {
         this.readOnlyHeaders = HttpHeaders.readOnlyHttpHeaders(this.headers);
         return this.readOnlyHeaders;
      } else {
         return this.headers;
      }
   }

   @Override
   public OutputStream getBody() throws IOException {
      this.bodyUsed = true;
      this.writeHeaders();
      return (OutputStream)(this.flushEnabled ? this.servletResponse.getOutputStream() : StreamUtils.nonFlushing(this.servletResponse.getOutputStream()));
   }

   @Override
   public void flush() throws IOException {
      this.writeHeaders();
      if (this.bodyUsed) {
         this.servletResponse.flushBuffer();
      }
   }

   @Override
   public void close() {
      this.writeHeaders();
   }

   private void writeHeaders() {
      if (!this.headersWritten) {
         if (this.servletResponse.getContentType() == null && this.headers.containsHeader("Content-Type")) {
            this.servletResponse.setContentType(this.headers.getFirst("Content-Type"));
         }

         if (this.servletResponse.getCharacterEncoding() == null && this.headers.containsHeader("Content-Type")) {
            try {
               MediaType contentType = this.headers.getContentType();
               if (contentType != null) {
                  Charset charset = contentType.getCharset();
                  if (charset != null) {
                     this.servletResponse.setCharacterEncoding(charset);
                  }
               }
            } catch (Exception var3) {
            }
         }

         this.headersWritten = true;
      }
   }
}
