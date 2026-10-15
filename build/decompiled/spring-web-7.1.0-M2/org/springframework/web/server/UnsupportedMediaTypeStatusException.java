package org.springframework.web.server;

import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.web.ErrorResponse;

public class UnsupportedMediaTypeStatusException extends ResponseStatusException {
   private static final String PARSE_ERROR_DETAIL_CODE = ErrorResponse.getDefaultDetailMessageCode(UnsupportedMediaTypeStatusException.class, "parseError");
   private final @Nullable MediaType contentType;
   private final List<MediaType> supportedMediaTypes;
   private final @Nullable ResolvableType bodyType;
   private final @Nullable HttpMethod method;

   public UnsupportedMediaTypeStatusException(@Nullable String reason) {
      this(reason, Collections.emptyList());
   }

   public UnsupportedMediaTypeStatusException(@Nullable String reason, List<MediaType> supportedTypes) {
      super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, reason, null, PARSE_ERROR_DETAIL_CODE, null);
      this.contentType = null;
      this.supportedMediaTypes = Collections.unmodifiableList(supportedTypes);
      this.bodyType = null;
      this.method = null;
      this.setDetail("Could not parse Content-Type.");
   }

   public UnsupportedMediaTypeStatusException(@Nullable MediaType contentType, List<MediaType> supportedTypes) {
      this(contentType, supportedTypes, null, null);
   }

   public UnsupportedMediaTypeStatusException(@Nullable MediaType contentType, List<MediaType> supportedTypes, @Nullable ResolvableType bodyType) {
      this(contentType, supportedTypes, bodyType, null);
   }

   public UnsupportedMediaTypeStatusException(@Nullable MediaType contentType, List<MediaType> supportedTypes, @Nullable HttpMethod method) {
      this(contentType, supportedTypes, null, method);
   }

   public UnsupportedMediaTypeStatusException(
      @Nullable MediaType contentType, List<MediaType> supportedTypes, @Nullable ResolvableType bodyType, @Nullable HttpMethod method
   ) {
      super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, initMessage(contentType, bodyType), null, null, new Object[]{contentType, supportedTypes});
      this.contentType = contentType;
      this.supportedMediaTypes = Collections.unmodifiableList(supportedTypes);
      this.bodyType = bodyType;
      this.method = method;
      this.setDetail(contentType != null ? "Content-Type '" + contentType + "' is not supported." : null);
   }

   private static String initMessage(@Nullable MediaType contentType, @Nullable ResolvableType bodyType) {
      return "Content type '" + (contentType != null ? contentType : "") + "' not supported" + (bodyType != null ? " for bodyType=" + bodyType : "");
   }

   public @Nullable MediaType getContentType() {
      return this.contentType;
   }

   public List<MediaType> getSupportedMediaTypes() {
      return this.supportedMediaTypes;
   }

   public @Nullable ResolvableType getBodyType() {
      return this.bodyType;
   }

   @Override
   public HttpHeaders getHeaders() {
      if (CollectionUtils.isEmpty(this.supportedMediaTypes)) {
         return HttpHeaders.EMPTY;
      }

      HttpHeaders headers = new HttpHeaders();
      headers.setAccept(this.supportedMediaTypes);
      if (this.method == HttpMethod.PATCH) {
         headers.setAcceptPatch(this.supportedMediaTypes);
      } else if (this.method == HttpMethod.QUERY) {
         headers.setAcceptQuery(this.supportedMediaTypes);
      }

      return headers;
   }
}
