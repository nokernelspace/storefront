package org.springframework.web;

import jakarta.servlet.ServletException;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

public abstract class HttpMediaTypeException extends ServletException implements ErrorResponse {
   private final List<MediaType> supportedMediaTypes;
   private final ProblemDetail body = ProblemDetail.forStatus(this.getStatusCode());
   private final String messageDetailCode;
   private final Object @Nullable [] messageDetailArguments;

   protected HttpMediaTypeException(
      @Nullable String message, List<MediaType> supportedMediaTypes, @Nullable String messageDetailCode, Object @Nullable [] messageDetailArguments
   ) {
      super(message);
      this.supportedMediaTypes = Collections.unmodifiableList(supportedMediaTypes);
      this.messageDetailCode = messageDetailCode != null ? messageDetailCode : ErrorResponse.getDefaultDetailMessageCode(this.getClass(), null);
      this.messageDetailArguments = messageDetailArguments;
   }

   public List<MediaType> getSupportedMediaTypes() {
      return this.supportedMediaTypes;
   }

   @Override
   public ProblemDetail getBody() {
      return this.body;
   }

   @Override
   public String getDetailMessageCode() {
      return this.messageDetailCode;
   }

   @Override
   public Object @Nullable [] getDetailMessageArguments() {
      return this.messageDetailArguments;
   }
}
