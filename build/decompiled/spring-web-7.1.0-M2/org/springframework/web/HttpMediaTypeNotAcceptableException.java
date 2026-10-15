package org.springframework.web;

import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;

public class HttpMediaTypeNotAcceptableException extends HttpMediaTypeException {
   private static final String PARSE_ERROR_DETAIL_CODE = ErrorResponse.getDefaultDetailMessageCode(HttpMediaTypeNotAcceptableException.class, "parseError");

   public HttpMediaTypeNotAcceptableException(String message) {
      super(message, Collections.emptyList(), PARSE_ERROR_DETAIL_CODE, null);
      this.getBody().setDetail("Could not parse Accept header.");
   }

   public HttpMediaTypeNotAcceptableException(List<MediaType> mediaTypes) {
      super("No acceptable representation", mediaTypes, null, new Object[]{mediaTypes});
      this.getBody().setDetail("Acceptable representations: " + mediaTypes + ".");
   }

   @Override
   public HttpStatusCode getStatusCode() {
      return HttpStatus.NOT_ACCEPTABLE;
   }

   @Override
   public HttpHeaders getHeaders() {
      if (CollectionUtils.isEmpty(this.getSupportedMediaTypes())) {
         return HttpHeaders.EMPTY;
      }

      HttpHeaders headers = new HttpHeaders();
      headers.setAccept(this.getSupportedMediaTypes());
      return headers;
   }
}
