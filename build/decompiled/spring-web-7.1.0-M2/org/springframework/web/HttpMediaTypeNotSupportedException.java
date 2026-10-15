package org.springframework.web;

import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;

public class HttpMediaTypeNotSupportedException extends HttpMediaTypeException {
   private static final String PARSE_ERROR_DETAIL_CODE = ErrorResponse.getDefaultDetailMessageCode(HttpMediaTypeNotSupportedException.class, "parseError");
   private final @Nullable MediaType contentType;
   private final @Nullable HttpMethod httpMethod;

   public HttpMediaTypeNotSupportedException(String message) {
      this(message, Collections.emptyList());
   }

   public HttpMediaTypeNotSupportedException(@Nullable String message, List<MediaType> mediaTypes) {
      super(message, mediaTypes, PARSE_ERROR_DETAIL_CODE, null);
      this.contentType = null;
      this.httpMethod = null;
      this.getBody().setDetail("Could not parse Content-Type.");
   }

   public HttpMediaTypeNotSupportedException(@Nullable MediaType contentType, List<MediaType> mediaTypes) {
      this(contentType, mediaTypes, null);
   }

   public HttpMediaTypeNotSupportedException(@Nullable MediaType contentType, List<MediaType> mediaTypes, @Nullable HttpMethod httpMethod) {
      this(contentType, mediaTypes, httpMethod, "Content-Type " + (contentType != null ? "'" + contentType + "' " : "") + "is not supported");
   }

   public HttpMediaTypeNotSupportedException(
      @Nullable MediaType contentType, List<MediaType> supportedMediaTypes, @Nullable HttpMethod httpMethod, String message
   ) {
      super(message, supportedMediaTypes, null, new Object[]{contentType, supportedMediaTypes});
      this.contentType = contentType;
      this.httpMethod = httpMethod;
      this.getBody().setDetail("Content-Type '" + this.contentType + "' is not supported.");
   }

   public @Nullable MediaType getContentType() {
      return this.contentType;
   }

   @Override
   public HttpStatusCode getStatusCode() {
      return HttpStatus.UNSUPPORTED_MEDIA_TYPE;
   }

   @Override
   public HttpHeaders getHeaders() {
      if (CollectionUtils.isEmpty(this.getSupportedMediaTypes())) {
         return HttpHeaders.EMPTY;
      }

      HttpHeaders headers = new HttpHeaders();
      headers.setAccept(this.getSupportedMediaTypes());
      if (HttpMethod.PATCH.equals(this.httpMethod)) {
         headers.setAcceptPatch(this.getSupportedMediaTypes());
      } else if (HttpMethod.QUERY.equals(this.httpMethod)) {
         headers.setAcceptQuery(this.getSupportedMediaTypes());
      }

      return headers;
   }
}
