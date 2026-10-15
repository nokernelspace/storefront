package org.springframework.web.multipart.support;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

public class RequestPartServletServerHttpRequest extends ServletServerHttpRequest {
   private final MultipartHttpServletRequest multipartRequest;
   private final String requestPartName;
   private final HttpHeaders multipartHeaders;

   public RequestPartServletServerHttpRequest(HttpServletRequest request, String requestPartName) throws MissingServletRequestPartException {
      super(request);
      this.multipartRequest = MultipartResolutionDelegate.asMultipartHttpServletRequest(request);
      this.requestPartName = requestPartName;
      HttpHeaders multipartHeaders = this.multipartRequest.getMultipartHeaders(requestPartName);
      if (multipartHeaders == null) {
         throw new MissingServletRequestPartException(requestPartName);
      }

      this.multipartHeaders = multipartHeaders;
   }

   @Override
   public HttpHeaders getHeaders() {
      return this.multipartHeaders;
   }

   @Override
   public InputStream getBody() throws IOException {
      boolean servletParts = this.multipartRequest instanceof StandardMultipartHttpServletRequest;
      if (servletParts) {
         Part part = this.retrieveServletPart();
         if (part != null) {
            return part.getInputStream();
         }
      }

      MultipartFile file = this.multipartRequest.getFile(this.requestPartName);
      if (file != null) {
         return file.getInputStream();
      }

      String paramValue = this.multipartRequest.getParameter(this.requestPartName);
      if (paramValue != null) {
         return new ByteArrayInputStream(paramValue.getBytes(this.determineCharset()));
      }

      if (!servletParts) {
         Part part = this.retrieveServletPart();
         if (part != null) {
            return part.getInputStream();
         }
      }

      throw new IllegalStateException("No body available for request part '" + this.requestPartName + "'");
   }

   private @Nullable Part retrieveServletPart() {
      try {
         return this.multipartRequest.getPart(this.requestPartName);
      } catch (Exception ex) {
         throw new MultipartException("Failed to retrieve request part '" + this.requestPartName + "'", ex);
      }
   }

   private Charset determineCharset() {
      MediaType contentType = this.getHeaders().getContentType();
      if (contentType != null) {
         Charset charset = contentType.getCharset();
         if (charset != null) {
            return charset;
         }
      }

      String encoding = this.multipartRequest.getCharacterEncoding();
      return encoding != null ? Charset.forName(encoding) : FORM_CHARSET;
   }
}
