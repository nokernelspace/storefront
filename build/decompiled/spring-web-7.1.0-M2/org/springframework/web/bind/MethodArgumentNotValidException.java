package org.springframework.web.bind;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.util.BindErrorUtils;

public class MethodArgumentNotValidException extends BindException implements ErrorResponse {
   private final MethodParameter parameter;
   private final ProblemDetail body;

   public MethodArgumentNotValidException(MethodParameter parameter, BindingResult bindingResult) {
      super(bindingResult);
      this.parameter = parameter;
      this.body = ProblemDetail.forStatusAndDetail(this.getStatusCode(), "Invalid request content.");
   }

   public final MethodParameter getParameter() {
      return this.parameter;
   }

   @Override
   public HttpStatusCode getStatusCode() {
      return HttpStatus.BAD_REQUEST;
   }

   @Override
   public ProblemDetail getBody() {
      return this.body;
   }

   @Override
   public Object[] getDetailMessageArguments(MessageSource source, Locale locale) {
      return new Object[]{
         BindErrorUtils.resolveAndJoin(this.getGlobalErrors(), source, locale), BindErrorUtils.resolveAndJoin(this.getFieldErrors(), source, locale)
      };
   }

   @Override
   public Object[] getDetailMessageArguments() {
      return new Object[]{BindErrorUtils.resolveAndJoin(this.getGlobalErrors()), BindErrorUtils.resolveAndJoin(this.getFieldErrors())};
   }

   public String getMessage() {
      StringBuilder sb = new StringBuilder("Validation failed for argument [")
         .append(this.parameter.getParameterIndex())
         .append("] in ")
         .append(this.parameter.getExecutable().toGenericString());
      BindingResult bindingResult = this.getBindingResult();
      if (bindingResult.getErrorCount() > 1) {
         sb.append(" with ").append(bindingResult.getErrorCount()).append(" errors");
      }

      sb.append(": ");

      for (ObjectError error : bindingResult.getAllErrors()) {
         sb.append('[').append(error).append("] ");
      }

      return sb.toString();
   }
}
