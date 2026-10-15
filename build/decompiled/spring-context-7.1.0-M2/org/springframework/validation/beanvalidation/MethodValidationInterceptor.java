package org.springframework.validation.beanvalidation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.core.MethodParameter;
import org.springframework.core.ReactiveAdapter;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.annotation.ValidationAnnotationUtils;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MethodValidationInterceptor implements MethodInterceptor {
   private static final boolean REACTOR_PRESENT = ClassUtils.isPresent("reactor.core.publisher.Mono", MethodValidationInterceptor.class.getClassLoader());
   private final MethodValidationAdapter validationAdapter;
   private final boolean adaptViolations;

   public MethodValidationInterceptor() {
      this(new MethodValidationAdapter(), false);
   }

   public MethodValidationInterceptor(ValidatorFactory validatorFactory) {
      this(new MethodValidationAdapter(validatorFactory), false);
   }

   public MethodValidationInterceptor(Validator validator) {
      this(new MethodValidationAdapter(validator), false);
   }

   public MethodValidationInterceptor(Supplier<Validator> validator) {
      this(validator, false);
   }

   public MethodValidationInterceptor(Supplier<Validator> validator, boolean adaptViolations) {
      this(new MethodValidationAdapter(validator), adaptViolations);
   }

   private MethodValidationInterceptor(MethodValidationAdapter validationAdapter, boolean adaptViolations) {
      this.validationAdapter = validationAdapter;
      this.adaptViolations = adaptViolations;
   }

   public Object invoke(MethodInvocation invocation) throws Throwable {
      if (this.isFactoryBeanMetadataMethod(invocation.getMethod())) {
         return invocation.proceed();
      }

      Object target = getTarget(invocation);
      Method method = invocation.getMethod();
      Object[] arguments = invocation.getArguments();
      Class<?>[] groups = this.determineValidationGroups(invocation);
      if (REACTOR_PRESENT) {
         arguments = MethodValidationInterceptor.ReactorValidationHelper.insertAsyncValidation(
            this.validationAdapter.getSpringValidatorAdapter(), this.adaptViolations, target, method, arguments
         );
      }

      if (this.adaptViolations) {
         this.validationAdapter.applyArgumentValidation(target, method, null, arguments, groups);
      } else {
         Set<ConstraintViolation<Object>> violations = this.validationAdapter.invokeValidatorForArguments(target, method, arguments, groups);
         if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
         }
      }

      Object returnValue = invocation.proceed();
      if (this.adaptViolations) {
         this.validationAdapter.applyReturnValueValidation(target, method, null, returnValue, groups);
      } else {
         Set<ConstraintViolation<Object>> violations = this.validationAdapter.invokeValidatorForReturnValue(target, method, returnValue, groups);
         if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
         }
      }

      return returnValue;
   }

   private static Object getTarget(MethodInvocation invocation) {
      Object target = invocation.getThis();
      if (target == null && invocation instanceof ProxyMethodInvocation methodInvocation) {
         target = methodInvocation.getProxy();
      }

      Assert.state(target != null, "Target must not be null");
      return target;
   }

   private boolean isFactoryBeanMetadataMethod(Method method) {
      Class<?> clazz = method.getDeclaringClass();
      if (clazz.isInterface()) {
         return (clazz == FactoryBean.class || clazz == SmartFactoryBean.class) && !method.getName().equals("getObject");
      }

      Class<?> factoryBeanType = null;
      if (SmartFactoryBean.class.isAssignableFrom(clazz)) {
         factoryBeanType = SmartFactoryBean.class;
      } else if (FactoryBean.class.isAssignableFrom(clazz)) {
         factoryBeanType = FactoryBean.class;
      }

      return factoryBeanType != null && !method.getName().equals("getObject") && ClassUtils.hasMethod(factoryBeanType, method);
   }

   protected Class<?>[] determineValidationGroups(MethodInvocation invocation) {
      Object target = getTarget(invocation);
      return ValidationAnnotationUtils.determineValidationGroups(target, invocation.getMethod());
   }

   private static final class ReactorValidationHelper {
      private static final ReactiveAdapterRegistry reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();

      static @Nullable Object[] insertAsyncValidation(
         Supplier<SpringValidatorAdapter> validatorAdapterSupplier, boolean adaptViolations, Object target, Method method, @Nullable Object[] arguments
      ) {
         for (int i = 0; i < method.getParameterCount(); i++) {
            if (arguments[i] != null) {
               Class<?> parameterType = method.getParameterTypes()[i];
               ReactiveAdapter reactiveAdapter = reactiveAdapterRegistry.getAdapter(parameterType);
               if (reactiveAdapter != null && !reactiveAdapter.isNoValue()) {
                  Class<?>[] groups = determineValidationGroups(method.getParameters()[i]);
                  if (groups != null) {
                     SpringValidatorAdapter validatorAdapter = validatorAdapterSupplier.get();
                     MethodParameter param = new MethodParameter(method, i);
                     arguments[i] = reactiveAdapter.isMultiValue()
                        ? Flux.from(reactiveAdapter.toPublisher(arguments[i]))
                           .doOnNext(value -> validate(validatorAdapter, adaptViolations, target, method, param, value, groups))
                        : Mono.from(reactiveAdapter.toPublisher(arguments[i]))
                           .doOnNext(value -> validate(validatorAdapter, adaptViolations, target, method, param, value, groups));
                  }
               }
            }
         }

         return arguments;
      }

      private static Class<?> @Nullable [] determineValidationGroups(Parameter parameter) {
         Validated validated = (Validated)AnnotationUtils.findAnnotation(parameter, Validated.class);
         if (validated != null) {
            return validated.value();
         }

         Valid valid = (Valid)AnnotationUtils.findAnnotation(parameter, Valid.class);
         return valid != null ? new Class[0] : null;
      }

      private static <T> void validate(
         SpringValidatorAdapter validatorAdapter,
         boolean adaptViolations,
         Object target,
         Method method,
         MethodParameter parameter,
         Object argument,
         Class<?>[] groups
      ) {
         if (adaptViolations) {
            Errors errors = new BeanPropertyBindingResult(argument, argument.getClass().getSimpleName());
            validatorAdapter.validate(argument, errors);
            if (errors.hasErrors()) {
               ParameterErrors paramErrors = new ParameterErrors(parameter, argument, errors, null, null, null);
               List<ParameterValidationResult> results = Collections.singletonList(paramErrors);
               throw new MethodValidationException(MethodValidationResult.create(target, method, results));
            }
         } else {
            Set<ConstraintViolation<T>> violations = validatorAdapter.validate((T)argument, groups);
            if (!violations.isEmpty()) {
               throw new ConstraintViolationException(violations);
            }
         }
      }
   }
}
