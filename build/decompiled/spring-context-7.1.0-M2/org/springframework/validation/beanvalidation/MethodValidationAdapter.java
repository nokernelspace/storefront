package org.springframework.validation.beanvalidation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.Path.Node;
import jakarta.validation.Path.ParameterNode;
import jakarta.validation.executable.ExecutableValidator;
import jakarta.validation.metadata.ConstraintDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.Conventions;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.util.Assert;
import org.springframework.util.function.SingletonSupplier;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.DefaultMessageCodesResolver;
import org.springframework.validation.Errors;
import org.springframework.validation.MessageCodesResolver;
import org.springframework.validation.annotation.ValidationAnnotationUtils;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.MethodValidator;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;

public class MethodValidationAdapter implements MethodValidator {
   private static final MethodValidationResult emptyValidationResult = MethodValidationResult.emptyResult();
   private static final MethodValidationAdapter.ObjectNameResolver defaultObjectNameResolver = new MethodValidationAdapter.DefaultObjectNameResolver();
   private static final Comparator<ParameterValidationResult> resultComparator = new MethodValidationAdapter.ResultComparator();
   private final Supplier<Validator> validator;
   private final Supplier<SpringValidatorAdapter> validatorAdapter;
   private MessageCodesResolver messageCodesResolver = new DefaultMessageCodesResolver();
   private ParameterNameDiscoverer parameterNameDiscoverer = DefaultParameterNameDiscoverer.getSharedInstance();
   private MethodValidationAdapter.ObjectNameResolver objectNameResolver = defaultObjectNameResolver;

   public MethodValidationAdapter() {
      this.validator = SingletonSupplier.of(() -> Validation.buildDefaultValidatorFactory().getValidator());
      this.validatorAdapter = initValidatorAdapter(this.validator);
   }

   public MethodValidationAdapter(ValidatorFactory validatorFactory) {
      if (validatorFactory instanceof SpringValidatorAdapter adapter) {
         this.validator = () -> adapter;
         this.validatorAdapter = () -> adapter;
      } else {
         this.validator = SingletonSupplier.of(validatorFactory::getValidator);
         this.validatorAdapter = SingletonSupplier.of(() -> new SpringValidatorAdapter(this.validator.get()));
      }
   }

   public MethodValidationAdapter(Validator validator) {
      this.validator = () -> validator;
      this.validatorAdapter = initValidatorAdapter(this.validator);
   }

   public MethodValidationAdapter(Supplier<Validator> validator) {
      this.validator = validator;
      this.validatorAdapter = initValidatorAdapter(validator);
   }

   private static Supplier<SpringValidatorAdapter> initValidatorAdapter(Supplier<Validator> validatorSupplier) {
      return SingletonSupplier.of(() -> {
         Validator validator = validatorSupplier.get();
         return validator instanceof SpringValidatorAdapter sva ? sva : new SpringValidatorAdapter(validator);
      });
   }

   public Supplier<SpringValidatorAdapter> getSpringValidatorAdapter() {
      return this.validatorAdapter;
   }

   public void setMessageCodesResolver(MessageCodesResolver messageCodesResolver) {
      this.messageCodesResolver = messageCodesResolver;
   }

   public MessageCodesResolver getMessageCodesResolver() {
      return this.messageCodesResolver;
   }

   public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
      this.parameterNameDiscoverer = parameterNameDiscoverer;
   }

   public ParameterNameDiscoverer getParameterNameDiscoverer() {
      return this.parameterNameDiscoverer;
   }

   public void setObjectNameResolver(MethodValidationAdapter.ObjectNameResolver nameResolver) {
      this.objectNameResolver = nameResolver;
   }

   @Deprecated(since = "7.0.4", forRemoval = true)
   @Override
   public Class<?>[] determineValidationGroups(Object target, Method method) {
      return ValidationAnnotationUtils.determineValidationGroups(target, method);
   }

   @Override
   public final MethodValidationResult validateArguments(
      Object target, Method method, MethodParameter @Nullable [] parameters, @Nullable Object[] arguments, Class<?>[] groups
   ) {
      Set<ConstraintViolation<Object>> violations = this.invokeValidatorForArguments(target, method, arguments, groups);
      return violations.isEmpty()
         ? emptyValidationResult
         : this.adaptViolations(target, method, violations, i -> parameters != null ? parameters[i] : this.initMethodParameter(method, i), i -> arguments[i]);
   }

   public final Set<ConstraintViolation<Object>> invokeValidatorForArguments(Object target, Method method, @Nullable Object[] arguments, Class<?>[] groups) {
      ExecutableValidator execVal = this.validator.get().forExecutables();

      try {
         return execVal.validateParameters(target, method, arguments, groups);
      } catch (IllegalArgumentException ex) {
         Method bridgedMethod = BridgeMethodResolver.getMostSpecificMethod(method, target.getClass());
         return execVal.validateParameters(target, bridgedMethod, arguments, groups);
      }
   }

   @Override
   public final MethodValidationResult validateReturnValue(
      Object target, Method method, @Nullable MethodParameter returnType, @Nullable Object returnValue, Class<?>[] groups
   ) {
      Set<ConstraintViolation<Object>> violations = this.invokeValidatorForReturnValue(target, method, returnValue, groups);
      return violations.isEmpty()
         ? emptyValidationResult
         : this.adaptViolations(target, method, violations, i -> returnType != null ? returnType : this.initMethodParameter(method, -1), i -> returnValue);
   }

   public final Set<ConstraintViolation<Object>> invokeValidatorForReturnValue(Object target, Method method, @Nullable Object returnValue, Class<?>[] groups) {
      ExecutableValidator execVal = this.validator.get().forExecutables();
      return execVal.validateReturnValue(target, method, returnValue, groups);
   }

   private MethodValidationResult adaptViolations(
      Object target,
      Method method,
      Set<ConstraintViolation<Object>> violations,
      Function<Integer, MethodParameter> parameterFunction,
      Function<Integer, @Nullable Object> argumentFunction
   ) {
      Map<Node, MethodValidationAdapter.ParamValidationResultBuilder> paramViolations = new LinkedHashMap<>();
      Map<Node, MethodValidationAdapter.ParamErrorsBuilder> nestedViolations = new LinkedHashMap<>();
      List<MessageSourceResolvable> crossParamErrors = null;

      label81:
      for (ConstraintViolation<Object> violation : violations) {
         Iterator<Node> nodes = violation.getPropertyPath().iterator();

         Node node;
         MethodParameter parameter;
         while (true) {
            if (!nodes.hasNext()) {
               continue label81;
            }

            node = nodes.next();
            if (node.getKind().equals(ElementKind.PARAMETER)) {
               int index = ((ParameterNode)node.as(ParameterNode.class)).getParameterIndex();
               parameter = parameterFunction.apply(index);
               break;
            }

            if (node.getKind().equals(ElementKind.RETURN_VALUE)) {
               parameter = parameterFunction.apply(-1);
               break;
            }

            if (node.getKind().equals(ElementKind.CROSS_PARAMETER)) {
               crossParamErrors = crossParamErrors != null ? crossParamErrors : new ArrayList<>();
               crossParamErrors.add(this.createCrossParamError(target, method, violation));
               continue label81;
            }
         }

         Object arg = argumentFunction.apply(parameter.getParameterIndex());
         Node parameterNode = node;
         if (nodes.hasNext()) {
            node = nodes.next();
         }

         Integer index = node.getIndex();
         Object key = node.getKey();
         Object value;
         Object container;
         if (index != null && arg instanceof List<?> list) {
            value = list.get(index);
            container = list;
         } else if (index != null && arg instanceof Object[] array) {
            value = array[index];
            container = array;
         } else if (key != null && arg instanceof Map<?, ?> map) {
            value = map.get(key);
            container = map;
         } else if (arg instanceof Iterable) {
            value = arg;
            container = arg;
         } else if (arg instanceof Optional<?> optional) {
            value = optional.orElse(null);
            container = optional;
         } else {
            value = arg;
            container = null;
         }

         if (!node.getKind().equals(ElementKind.PROPERTY) && !node.getKind().equals(ElementKind.BEAN)) {
            paramViolations.computeIfAbsent(
                  parameterNode, p -> new MethodValidationAdapter.ParamValidationResultBuilder(target, parameter, value, container, index, key)
               )
               .addViolation(violation);
         } else {
            nestedViolations.computeIfAbsent(parameterNode, k -> new MethodValidationAdapter.ParamErrorsBuilder(parameter, value, container, index, key))
               .addViolation(violation);
         }
      }

      List<ParameterValidationResult> resultList = new ArrayList<>();
      paramViolations.forEach((param, builder) -> resultList.add(builder.build()));
      nestedViolations.forEach((keyx, builder) -> resultList.add(builder.build()));
      resultList.sort(resultComparator);
      return MethodValidationResult.create(target, method, resultList, crossParamErrors != null ? crossParamErrors : Collections.emptyList());
   }

   private MethodParameter initMethodParameter(Method method, int index) {
      MethodParameter parameter = new MethodParameter(method, index);
      parameter.initParameterNameDiscovery(this.parameterNameDiscoverer);
      return parameter;
   }

   private MessageSourceResolvable createMessageSourceResolvable(Object target, MethodParameter parameter, ConstraintViolation<Object> violation) {
      String objectName = Conventions.getVariableName(target) + "#" + parameter.getExecutable().getName();
      String paramName = parameter.getParameterName() != null ? parameter.getParameterName() : "";
      Class<?> parameterType = parameter.getParameterType();
      ConstraintDescriptor<?> descriptor = violation.getConstraintDescriptor();
      String code = descriptor.getAnnotation().annotationType().getSimpleName();
      String[] codes = this.messageCodesResolver.resolveMessageCodes(code, objectName, paramName, parameterType);
      Object[] arguments = this.validatorAdapter.get().getArgumentsForConstraint(objectName, paramName, descriptor);
      return new MethodValidationAdapter.ViolationMessageSourceResolvable(codes, arguments, violation.getMessage(), violation);
   }

   private BindingResult createBindingResult(MethodParameter parameter, @Nullable Object argument) {
      String objectName = this.objectNameResolver.resolveName(parameter, argument);
      BeanPropertyBindingResult result = new BeanPropertyBindingResult(argument, objectName);
      result.setMessageCodesResolver(this.messageCodesResolver);
      return result;
   }

   private MessageSourceResolvable createCrossParamError(Object target, Method method, ConstraintViolation<Object> violation) {
      String objectName = Conventions.getVariableName(target) + "#" + method.getName();
      ConstraintDescriptor<?> descriptor = violation.getConstraintDescriptor();
      String code = descriptor.getAnnotation().annotationType().getSimpleName();
      String[] codes = this.messageCodesResolver.resolveMessageCodes(code, objectName);
      Object[] arguments = this.validatorAdapter.get().getArgumentsForConstraint(objectName, "", descriptor);
      return new MethodValidationAdapter.ViolationMessageSourceResolvable(codes, arguments, violation.getMessage(), violation);
   }

   private static class DefaultObjectNameResolver implements MethodValidationAdapter.ObjectNameResolver {
      @Override
      public String resolveName(MethodParameter parameter, @Nullable Object value) {
         String objectName = null;
         if (parameter.getParameterIndex() != -1) {
            objectName = parameter.getParameterName();
         } else {
            try {
               Method method = parameter.getMethod();
               if (method != null) {
                  Class<?> containingClass = parameter.getContainingClass();
                  Class<?> resolvedType = GenericTypeResolver.resolveReturnType(method, containingClass);
                  objectName = Conventions.getVariableNameForReturnType(method, resolvedType, value);
               }
            } catch (IllegalArgumentException var7) {
            }
         }

         if (objectName == null) {
            int index = parameter.getParameterIndex();
            objectName = parameter.getExecutable().getName() + (index != -1 ? ".arg" + index : ".returnValue");
         }

         return objectName;
      }
   }

   public interface ObjectNameResolver {
      String resolveName(MethodParameter parameter, @Nullable Object value);
   }

   private final class ParamErrorsBuilder {
      private final MethodParameter parameter;
      private final @Nullable Object bean;
      private final @Nullable Object container;
      private final @Nullable Integer containerIndex;
      private final @Nullable Object containerKey;
      private final Errors errors;
      private final Set<ConstraintViolation<Object>> violations = new LinkedHashSet<>();

      public ParamErrorsBuilder(
         MethodParameter param, @Nullable Object bean, @Nullable Object container, @Nullable Integer containerIndex, @Nullable Object containerKey
      ) {
         this.parameter = param;
         this.bean = bean;
         this.container = container;
         this.containerIndex = containerIndex;
         this.containerKey = containerKey;
         this.errors = MethodValidationAdapter.this.createBindingResult(param, this.bean);
      }

      public void addViolation(ConstraintViolation<Object> violation) {
         this.violations.add(violation);
      }

      public ParameterErrors build() {
         MethodValidationAdapter.this.validatorAdapter.get().processConstraintViolations(this.violations, this.errors);
         return new ParameterErrors(this.parameter, this.bean, this.errors, this.container, this.containerIndex, this.containerKey);
      }
   }

   private final class ParamValidationResultBuilder {
      private final Object target;
      private final MethodParameter parameter;
      private final @Nullable Object value;
      private final @Nullable Object container;
      private final @Nullable Integer containerIndex;
      private final @Nullable Object containerKey;
      private final List<MessageSourceResolvable> resolvableErrors = new ArrayList<>();

      public ParamValidationResultBuilder(
         Object target,
         MethodParameter parameter,
         @Nullable Object value,
         @Nullable Object container,
         @Nullable Integer containerIndex,
         @Nullable Object containerKey
      ) {
         this.target = target;
         this.parameter = parameter;
         this.value = value;
         this.container = container;
         this.containerIndex = containerIndex;
         this.containerKey = containerKey;
      }

      public void addViolation(ConstraintViolation<Object> violation) {
         this.resolvableErrors.add(MethodValidationAdapter.this.createMessageSourceResolvable(this.target, this.parameter, violation));
      }

      public ParameterValidationResult build() {
         return new ParameterValidationResult(
            this.parameter, this.value, this.resolvableErrors, this.container, this.containerIndex, this.containerKey, (error, sourceType) -> {
               Assert.isTrue(sourceType.equals(ConstraintViolation.class), "Unexpected source type");
               return ((MethodValidationAdapter.ViolationMessageSourceResolvable)error).getViolation();
            }
         );
      }
   }

   private static final class ResultComparator implements Comparator<ParameterValidationResult> {
      public int compare(ParameterValidationResult result1, ParameterValidationResult result2) {
         int index1 = result1.getMethodParameter().getParameterIndex();
         int index2 = result2.getMethodParameter().getParameterIndex();
         int i = Integer.compare(index1, index2);
         if (i != 0) {
            return i;
         }

         if (result1 instanceof ParameterErrors errors1 && result2 instanceof ParameterErrors errors2) {
            Integer containerIndex1 = errors1.getContainerIndex();
            Integer containerIndex2 = errors2.getContainerIndex();
            if (containerIndex1 != null && containerIndex2 != null) {
               i = Integer.compare(containerIndex1, containerIndex2);
               if (i != 0) {
                  return i;
               }
            }

            return this.compareKeys(errors1, errors2);
         } else {
            return 0;
         }
      }

      private <E> int compareKeys(ParameterErrors errors1, ParameterErrors errors2) {
         Object key1 = errors1.getContainerKey();
         Object key2 = errors2.getContainerKey();
         return key1 instanceof Comparable && key2 instanceof Comparable ? ((Comparable)key1).compareTo(key2) : 0;
      }
   }

   private class ViolationMessageSourceResolvable extends DefaultMessageSourceResolvable {
      private final transient ConstraintViolation<Object> violation;

      public ViolationMessageSourceResolvable(String[] codes, Object[] arguments, String defaultMessage, ConstraintViolation<Object> violation) {
         super(codes, arguments, defaultMessage);
         this.violation = violation;
      }

      public ConstraintViolation<Object> getViolation() {
         return this.violation;
      }

      @Override
      public boolean shouldRenderDefaultMessage() {
         return MethodValidationAdapter.this.validatorAdapter.get().requiresMessageFormat(this.violation);
      }
   }
}
