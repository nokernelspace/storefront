package org.springframework.web.method.annotation;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.BindErrorUtils;

public class HandlerMethodValidationException extends ResponseStatusException implements MethodValidationResult {
   private final MethodValidationResult validationResult;
   private final Predicate<MethodParameter> modelAttributePredicate;
   private final Predicate<MethodParameter> requestParamPredicate;

   public HandlerMethodValidationException(MethodValidationResult validationResult) {
      this(validationResult, param -> param.hasParameterAnnotation(ModelAttribute.class), param -> param.hasParameterAnnotation(RequestParam.class));
   }

   public HandlerMethodValidationException(
      MethodValidationResult validationResult, Predicate<MethodParameter> modelAttributePredicate, Predicate<MethodParameter> requestParamPredicate
   ) {
      super(initHttpStatus(validationResult), "Validation failure", null, null, null);
      this.validationResult = validationResult;
      this.modelAttributePredicate = modelAttributePredicate;
      this.requestParamPredicate = requestParamPredicate;
   }

   private static HttpStatus initHttpStatus(MethodValidationResult validationResult) {
      return validationResult.isForReturnValue() ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST;
   }

   @Override
   public Object[] getDetailMessageArguments(MessageSource messageSource, Locale locale) {
      return new Object[]{BindErrorUtils.resolveAndJoin(this.getAllErrors(), messageSource, locale)};
   }

   @Override
   public Object[] getDetailMessageArguments() {
      return new Object[]{BindErrorUtils.resolveAndJoin(this.getAllErrors())};
   }

   public Object getTarget() {
      return this.validationResult.getTarget();
   }

   public Method getMethod() {
      return this.validationResult.getMethod();
   }

   public boolean isForReturnValue() {
      return this.validationResult.isForReturnValue();
   }

   public List<ParameterValidationResult> getParameterValidationResults() {
      return this.validationResult.getParameterValidationResults();
   }

   public List<MessageSourceResolvable> getCrossParameterValidationResults() {
      return this.validationResult.getCrossParameterValidationResults();
   }

   public void visitResults(HandlerMethodValidationException.Visitor visitor) {
      for (ParameterValidationResult result : this.getParameterValidationResults()) {
         MethodParameter param = result.getMethodParameter();
         CookieValue cookieValue = (CookieValue)param.getParameterAnnotation(CookieValue.class);
         if (cookieValue != null) {
            visitor.cookieValue(cookieValue, result);
         } else {
            MatrixVariable matrixVariable = (MatrixVariable)param.getParameterAnnotation(MatrixVariable.class);
            if (matrixVariable != null) {
               visitor.matrixVariable(matrixVariable, result);
            } else if (this.modelAttributePredicate.test(param)) {
               ModelAttribute modelAttribute = (ModelAttribute)param.getParameterAnnotation(ModelAttribute.class);
               visitor.modelAttribute(modelAttribute, asErrors(result));
            } else {
               PathVariable pathVariable = (PathVariable)param.getParameterAnnotation(PathVariable.class);
               if (pathVariable != null) {
                  visitor.pathVariable(pathVariable, result);
               } else {
                  RequestBody requestBody = (RequestBody)param.getParameterAnnotation(RequestBody.class);
                  if (requestBody != null) {
                     if (result instanceof ParameterErrors errors) {
                        visitor.requestBody(requestBody, errors);
                     } else {
                        visitor.requestBodyValidationResult(requestBody, result);
                     }
                  } else {
                     RequestHeader requestHeader = (RequestHeader)param.getParameterAnnotation(RequestHeader.class);
                     if (requestHeader != null) {
                        visitor.requestHeader(requestHeader, result);
                     } else if (this.requestParamPredicate.test(param)) {
                        RequestParam requestParam = (RequestParam)param.getParameterAnnotation(RequestParam.class);
                        visitor.requestParam(requestParam, result);
                     } else {
                        RequestPart requestPart = (RequestPart)param.getParameterAnnotation(RequestPart.class);
                        if (requestPart != null) {
                           visitor.requestPart(requestPart, asErrors(result));
                        } else {
                           visitor.other(result);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static ParameterErrors asErrors(ParameterValidationResult result) {
      Assert.state(result instanceof ParameterErrors, "Expected ParameterErrors");
      return (ParameterErrors)result;
   }

   public interface Visitor {
      void cookieValue(CookieValue cookieValue, ParameterValidationResult result);

      void matrixVariable(MatrixVariable matrixVariable, ParameterValidationResult result);

      void modelAttribute(@Nullable ModelAttribute modelAttribute, ParameterErrors errors);

      void pathVariable(PathVariable pathVariable, ParameterValidationResult result);

      void requestBody(RequestBody requestBody, ParameterErrors errors);

      default void requestBodyValidationResult(RequestBody requestBody, ParameterValidationResult result) {
      }

      void requestHeader(RequestHeader requestHeader, ParameterValidationResult result);

      void requestParam(@Nullable RequestParam requestParam, ParameterValidationResult result);

      void requestPart(RequestPart requestPart, ParameterErrors errors);

      void other(ParameterValidationResult result);
   }
}
