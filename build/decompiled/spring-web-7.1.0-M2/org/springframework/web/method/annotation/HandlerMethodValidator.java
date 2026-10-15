package org.springframework.web.method.annotation;

import jakarta.validation.Validator;
import java.lang.reflect.Method;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Conventions;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.validation.BindingResult;
import org.springframework.validation.MessageCodesResolver;
import org.springframework.validation.SmartValidator;
import org.springframework.validation.annotation.ValidationAnnotationUtils;
import org.springframework.validation.beanvalidation.MethodValidationAdapter;
import org.springframework.validation.beanvalidation.MethodValidationAdapter.ObjectNameResolver;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.MethodValidator;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.support.ConfigurableWebBindingInitializer;
import org.springframework.web.bind.support.WebBindingInitializer;

public final class HandlerMethodValidator implements MethodValidator {
   private static final ObjectNameResolver objectNameResolver = new HandlerMethodValidator.WebObjectNameResolver();
   private final MethodValidationAdapter validationAdapter;
   private final Predicate<MethodParameter> modelAttributePredicate;
   private final Predicate<MethodParameter> requestParamPredicate;

   private HandlerMethodValidator(
      MethodValidationAdapter validationAdapter, Predicate<MethodParameter> modelAttributePredicate, Predicate<MethodParameter> requestParamPredicate
   ) {
      this.validationAdapter = validationAdapter;
      this.modelAttributePredicate = modelAttributePredicate;
      this.requestParamPredicate = requestParamPredicate;
   }

   @Deprecated(since = "7.0.4", forRemoval = true)
   public Class<?>[] determineValidationGroups(Object target, Method method) {
      return ValidationAnnotationUtils.determineValidationGroups(target, method);
   }

   public void applyArgumentValidation(Object target, Method method, MethodParameter @Nullable [] parameters, @Nullable Object[] arguments, Class<?>[] groups) {
      MethodValidationResult result = this.validateArguments(target, method, parameters, arguments, groups);
      if (result.hasErrors()) {
         if (!result.getBeanResults().isEmpty()) {
            int bindingResultCount = 0;

            for (ParameterErrors errors : result.getBeanResults()) {
               Object[] var10 = arguments;
               int var11 = var10.length;

               for (int var12 = 0; var12 < var11; var12++) {
                  if (var10[var12] instanceof BindingResult bindingResult && bindingResult.getObjectName().equals(errors.getObjectName())) {
                     bindingResult.addAllErrors(errors);
                     bindingResultCount++;
                     break;
                  }
               }
            }

            if (result.getParameterValidationResults().size() == bindingResultCount) {
               return;
            }
         }

         throw new HandlerMethodValidationException(result, this.modelAttributePredicate, this.requestParamPredicate);
      }
   }

   public MethodValidationResult validateArguments(
      Object target, Method method, MethodParameter @Nullable [] parameters, @Nullable Object[] arguments, Class<?>[] groups
   ) {
      return this.validationAdapter.validateArguments(target, method, parameters, arguments, groups);
   }

   public void applyReturnValueValidation(Object target, Method method, @Nullable MethodParameter returnType, @Nullable Object returnValue, Class<?>[] groups) {
      MethodValidationResult result = this.validateReturnValue(target, method, returnType, returnValue, groups);
      if (result.hasErrors()) {
         throw new HandlerMethodValidationException(result);
      }
   }

   public MethodValidationResult validateReturnValue(
      Object target, Method method, @Nullable MethodParameter returnType, @Nullable Object returnValue, Class<?>[] groups
   ) {
      return this.validationAdapter.validateReturnValue(target, method, returnType, returnValue, groups);
   }

   public static @Nullable MethodValidator from(
      @Nullable WebBindingInitializer initializer,
      @Nullable ParameterNameDiscoverer paramNameDiscoverer,
      Predicate<MethodParameter> modelAttributePredicate,
      Predicate<MethodParameter> requestParamPredicate
   ) {
      if (initializer instanceof ConfigurableWebBindingInitializer configurableInitializer) {
         Validator validator = getValidator(configurableInitializer);
         if (validator != null) {
            MethodValidationAdapter adapter = new MethodValidationAdapter(validator);
            adapter.setObjectNameResolver(objectNameResolver);
            if (paramNameDiscoverer != null) {
               adapter.setParameterNameDiscoverer(paramNameDiscoverer);
            }

            MessageCodesResolver codesResolver = configurableInitializer.getMessageCodesResolver();
            if (codesResolver != null) {
               adapter.setMessageCodesResolver(codesResolver);
            }

            return new HandlerMethodValidator(adapter, modelAttributePredicate, requestParamPredicate);
         }
      }

      return null;
   }

   private static @Nullable Validator getValidator(ConfigurableWebBindingInitializer initializer) {
      if (initializer.getValidator() instanceof Validator validator) {
         return validator;
      } else {
         return initializer.getValidator() instanceof SmartValidator smartValidator ? (Validator)smartValidator.unwrap(Validator.class) : null;
      }
   }

   private static class WebObjectNameResolver implements ObjectNameResolver {
      public String resolveName(MethodParameter param, @Nullable Object value) {
         if (!param.hasParameterAnnotation(RequestBody.class) && !param.hasParameterAnnotation(RequestPart.class)) {
            return param.getParameterIndex() != -1 ? ModelFactory.getNameForParameter(param) : ModelFactory.getNameForReturnValue(value, param);
         } else {
            return Conventions.getVariableNameForParameter(param);
         }
      }
   }
}
