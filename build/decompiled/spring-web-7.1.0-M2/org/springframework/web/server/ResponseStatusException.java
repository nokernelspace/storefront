package org.springframework.web.server;

import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class ResponseStatusException extends ErrorResponseException {
   private final @Nullable String reason;

   public ResponseStatusException(HttpStatusCode status) {
      this(status, null);
   }

   public ResponseStatusException(HttpStatusCode status, @Nullable String reason) {
      this(status, reason, null);
   }

   public ResponseStatusException(int rawStatusCode, @Nullable String reason, @Nullable Throwable cause) {
      this(HttpStatusCode.valueOf(rawStatusCode), reason, cause);
   }

   public ResponseStatusException(HttpStatusCode status, @Nullable String reason, @Nullable Throwable cause) {
      this(status, reason, cause, null, null);
   }

   protected ResponseStatusException(
      HttpStatusCode status, @Nullable String reason, @Nullable Throwable cause, @Nullable String messageDetailCode, Object @Nullable [] messageDetailArguments
   ) {
      super(status, ProblemDetail.forStatus(status), cause, messageDetailCode, messageDetailArguments);
      this.reason = reason;
      this.setDetail(reason);
   }

   public @Nullable String getReason() {
      return this.reason;
   }

   @Override
   public HttpHeaders getHeaders() {
      return HttpHeaders.EMPTY;
   }

   @Override
   public ProblemDetail updateAndGetBody(@Nullable MessageSource messageSource, Locale locale) {
      super.updateAndGetBody(messageSource, locale);
      if (messageSource != null && this.getReason() != null && this.getReason().equals(this.getBody().getDetail())) {
         Object[] arguments = this.getDetailMessageArguments(messageSource, locale);
         String resolved = messageSource.getMessage(this.getReason(), arguments, null, locale);
         if (resolved != null) {
            this.getBody().setDetail(resolved);
         }
      }

      return this.getBody();
   }

   @Override
   public String getMessage() {
      return this.getStatusCode() + (this.reason != null ? " \"" + this.reason + "\"" : "");
   }
}
