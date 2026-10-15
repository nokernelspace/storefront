package org.springframework.web.multipart;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponse;

public class MaxUploadSizeExceededException extends MultipartException implements ErrorResponse {
   private final ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "Maximum upload size exceeded");
   private final long maxUploadSize;

   public MaxUploadSizeExceededException(long maxUploadSize) {
      this(maxUploadSize, null);
   }

   public MaxUploadSizeExceededException(long maxUploadSize, @Nullable Throwable ex) {
      super("Maximum upload size " + (maxUploadSize >= 0L ? "of " + maxUploadSize + " bytes " : "") + "exceeded", ex);
      this.maxUploadSize = maxUploadSize;
   }

   public long getMaxUploadSize() {
      return this.maxUploadSize;
   }

   @Override
   public HttpStatusCode getStatusCode() {
      return HttpStatus.CONTENT_TOO_LARGE;
   }

   @Override
   public ProblemDetail getBody() {
      return this.body;
   }
}
