package org.springframework.boot.diagnostics.analyzer;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.boot.origin.Origin;
import org.springframework.util.Assert;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

class BindValidationFailureAnalyzer extends AbstractFailureAnalyzer<Throwable> {
   @Override
   protected @Nullable FailureAnalysis analyze(Throwable rootFailure, Throwable cause) {
      BindValidationFailureAnalyzer.ExceptionDetails details = this.getBindValidationExceptionDetails(rootFailure);
      return details == null ? null : this.analyzeBindValidationException(details);
   }

   private BindValidationFailureAnalyzer.@Nullable ExceptionDetails getBindValidationExceptionDetails(Throwable rootFailure) {
      BindValidationException validationException = this.findCause(rootFailure, BindValidationException.class);
      if (validationException != null) {
         BindException bindException = this.findCause(rootFailure, BindException.class);
         List<ObjectError> errors = validationException.getValidationErrors().getAllErrors();
         Assert.state(bindException != null, "BindException not found");
         return new BindValidationFailureAnalyzer.ExceptionDetails(errors, bindException.getTarget().getType(), validationException);
      } else {
         org.springframework.validation.BindException bindException = this.findCause(rootFailure, org.springframework.validation.BindException.class);
         if (bindException != null) {
            List<ObjectError> errors = bindException.getAllErrors();
            return new BindValidationFailureAnalyzer.ExceptionDetails(errors, bindException.getTarget(), bindException);
         } else {
            return null;
         }
      }
   }

   private FailureAnalysis analyzeBindValidationException(BindValidationFailureAnalyzer.ExceptionDetails details) {
      StringBuilder description = new StringBuilder(String.format("Binding to target %s failed:%n", details.getTarget()));

      for (ObjectError error : details.getErrors()) {
         if (error instanceof FieldError fieldError) {
            this.appendFieldError(description, fieldError);
         }

         description.append(String.format("%n    Reason: %s%n", error.getDefaultMessage()));
      }

      return this.getFailureAnalysis(description, details.getCause());
   }

   private void appendFieldError(StringBuilder description, FieldError error) {
      Origin origin = Origin.from(error);
      description.append(String.format("%n    Property: %s", error.getObjectName() + "." + error.getField()));
      description.append(String.format("%n    Value: \"%s\"", error.getRejectedValue()));
      if (origin != null) {
         description.append(String.format("%n    Origin: %s", origin));
      }
   }

   private FailureAnalysis getFailureAnalysis(Object description, Throwable cause) {
      return new FailureAnalysis(description.toString(), "Update your application's configuration", cause);
   }

   private static class ExceptionDetails {
      private final List<ObjectError> errors;
      private final @Nullable Object target;
      private final Throwable cause;

      ExceptionDetails(List<ObjectError> errors, @Nullable Object target, Throwable cause) {
         this.errors = errors;
         this.target = target;
         this.cause = cause;
      }

      @Nullable Object getTarget() {
         return this.target;
      }

      List<ObjectError> getErrors() {
         return this.errors;
      }

      Throwable getCause() {
         return this.cause;
      }
   }
}
