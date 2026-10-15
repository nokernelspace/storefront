package org.springframework.web.method;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotatedMethod;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotationPredicates;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.annotation.ValidationAnnotationUtils;
import org.springframework.web.bind.annotation.ResponseStatus;

public class HandlerMethod extends AnnotatedMethod {
   protected static final Log logger = LogFactory.getLog(HandlerMethod.class);
   private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class[0];
   private final Object bean;
   private final @Nullable BeanFactory beanFactory;
   private final @Nullable MessageSource messageSource;
   private final Class<?> beanType;
   private final boolean validateArguments;
   private final boolean validateReturnValue;
   private Class<?> @Nullable [] validationGroups;
   private @Nullable HttpStatusCode responseStatus;
   private @Nullable String responseStatusReason;
   private @Nullable HandlerMethod resolvedFromHandlerMethod;
   private volatile @Nullable HandlerMethod resolvedBeanHandlerMethod;
   private final String description;

   public HandlerMethod(Object bean, Method method) {
      this(bean, method, null);
   }

   protected HandlerMethod(Object bean, Method method, @Nullable MessageSource messageSource) {
      super(method);
      this.bean = bean;
      this.beanFactory = null;
      this.messageSource = messageSource;
      this.beanType = ClassUtils.getUserClass(bean);
      this.validateArguments = false;
      this.validateReturnValue = false;
      this.evaluateResponseStatus();
      this.description = initDescription(this.beanType, method);
   }

   public HandlerMethod(Object bean, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
      super(bean.getClass().getMethod(methodName, parameterTypes));
      this.bean = bean;
      this.beanFactory = null;
      this.messageSource = null;
      this.beanType = ClassUtils.getUserClass(bean);
      this.validateArguments = false;
      this.validateReturnValue = false;
      this.evaluateResponseStatus();
      this.description = initDescription(this.beanType, this.getMethod());
   }

   public HandlerMethod(String beanName, BeanFactory beanFactory, Method method) {
      this(beanName, beanFactory, null, method);
   }

   public HandlerMethod(String beanName, BeanFactory beanFactory, @Nullable MessageSource messageSource, Method method) {
      super(method);
      Assert.hasText(beanName, "Bean name is required");
      Assert.notNull(beanFactory, "BeanFactory is required");
      this.bean = beanName;
      this.beanFactory = beanFactory;
      this.messageSource = messageSource;
      Class<?> beanType = beanFactory.getType(beanName);
      if (beanType == null) {
         throw new IllegalStateException("Cannot resolve bean type for bean with name '" + beanName + "'");
      }

      this.beanType = ClassUtils.getUserClass(beanType);
      this.validateArguments = false;
      this.validateReturnValue = false;
      this.evaluateResponseStatus();
      this.description = initDescription(this.beanType, method);
   }

   protected HandlerMethod(HandlerMethod handlerMethod) {
      this(handlerMethod, null, false);
   }

   protected HandlerMethod(HandlerMethod handlerMethod, @Nullable Object handler, boolean initValidateFlags) {
      super(handlerMethod);
      this.bean = handler != null ? handler : handlerMethod.bean;
      this.beanFactory = handlerMethod.beanFactory;
      this.messageSource = handlerMethod.messageSource;
      this.beanType = handlerMethod.beanType;
      this.validateArguments = initValidateFlags
         ? HandlerMethod.MethodValidationInitializer.checkArguments(this.beanType, this.getMethodParameters())
         : handlerMethod.validateArguments;
      this.validateReturnValue = initValidateFlags
         ? HandlerMethod.MethodValidationInitializer.checkReturnValue(this.beanType, this.getBridgedMethod())
         : handlerMethod.validateReturnValue;
      this.validationGroups = handler == null || !this.shouldValidateArguments() && !this.shouldValidateReturnValue()
         ? handlerMethod.validationGroups
         : ValidationAnnotationUtils.determineValidationGroups(handler, this.getBridgedMethod());
      this.responseStatus = handlerMethod.responseStatus;
      this.responseStatusReason = handlerMethod.responseStatusReason;
      this.resolvedFromHandlerMethod = handlerMethod.resolvedFromHandlerMethod != null ? handlerMethod.resolvedFromHandlerMethod : handlerMethod;
      this.description = handlerMethod.toString();
   }

   private void evaluateResponseStatus() {
      ResponseStatus annotation = (ResponseStatus)this.getMethodAnnotation(ResponseStatus.class);
      if (annotation == null) {
         annotation = (ResponseStatus)AnnotatedElementUtils.findMergedAnnotation(this.getBeanType(), ResponseStatus.class);
      }

      if (annotation != null) {
         String reason = annotation.reason();
         String resolvedReason = StringUtils.hasText(reason) && this.messageSource != null
            ? this.messageSource.getMessage(reason, null, reason, LocaleContextHolder.getLocale())
            : reason;
         this.responseStatus = annotation.code();
         this.responseStatusReason = resolvedReason;
         if (StringUtils.hasText(this.responseStatusReason) && this.getMethod().getReturnType() != void.class && logger.isWarnEnabled()) {
            logger.warn("Return value of [" + this.getMethod() + "] will be ignored since @ResponseStatus 'reason' attribute is set.");
         }
      }
   }

   private static String initDescription(Class<?> beanType, Method method) {
      StringJoiner joiner = new StringJoiner(", ", "(", ")");

      for (Class<?> paramType : method.getParameterTypes()) {
         joiner.add(paramType.getSimpleName());
      }

      return beanType.getName() + "#" + method.getName() + joiner;
   }

   public Object getBean() {
      return this.bean;
   }

   public Class<?> getBeanType() {
      return this.beanType;
   }

   protected Class<?> getContainingClass() {
      return this.beanType;
   }

   public boolean shouldValidateArguments() {
      return this.validateArguments;
   }

   public boolean shouldValidateReturnValue() {
      return this.validateReturnValue;
   }

   public Class<?>[] getValidationGroups() {
      return this.validationGroups != null ? this.validationGroups : EMPTY_CLASS_ARRAY;
   }

   protected @Nullable HttpStatusCode getResponseStatus() {
      return this.responseStatus;
   }

   protected @Nullable String getResponseStatusReason() {
      return this.responseStatusReason;
   }

   public @Nullable HandlerMethod getResolvedFromHandlerMethod() {
      return this.resolvedFromHandlerMethod;
   }

   public HandlerMethod createWithValidateFlags() {
      return new HandlerMethod(this, null, true);
   }

   public HandlerMethod createWithResolvedBean() {
      HandlerMethod resolvedBeanHandlerMethod = this.resolvedBeanHandlerMethod;
      if (resolvedBeanHandlerMethod != null) {
         return resolvedBeanHandlerMethod;
      }

      if (this.bean instanceof String beanName) {
         Assert.state(this.beanFactory != null, "Cannot resolve bean name without BeanFactory");
         Object handler = this.beanFactory.getBean(beanName);
         HandlerMethod handlerMethod = new HandlerMethod(this, handler, false);
         if (this.beanFactory.isSingleton(beanName)) {
            this.resolvedBeanHandlerMethod = handlerMethod;
         }

         return handlerMethod;
      } else {
         return this;
      }
   }

   public String getShortLogMessage() {
      return this.getBeanType().getName() + "#" + this.getMethod().getName() + "[" + this.getMethod().getParameterCount() + " args]";
   }

   public boolean equals(@Nullable Object other) {
      return this == other || super.equals(other) && other instanceof HandlerMethod otherMethod && this.bean.equals(otherMethod.bean);
   }

   public int hashCode() {
      return this.bean.hashCode() * 31 + super.hashCode();
   }

   public String toString() {
      return this.description;
   }

   protected void assertTargetBean(Method method, Object targetBean, @Nullable Object[] args) {
      Class<?> methodDeclaringClass = method.getDeclaringClass();
      Class<?> targetBeanClass = targetBean.getClass();
      if (!methodDeclaringClass.isAssignableFrom(targetBeanClass)) {
         String text = "The mapped handler method class '"
            + methodDeclaringClass.getName()
            + "' is not an instance of the actual target bean class '"
            + targetBeanClass.getName()
            + "'. If the target bean requires proxying (for example, due to @Transactional), please use class-based proxying.";
         throw new IllegalStateException(this.formatInvokeError(text, args));
      }
   }

   protected String formatInvokeError(String text, @Nullable Object[] args) {
      String formattedArgs = IntStream.range(0, args.length).mapToObj(i -> {
         Object arg = args[i];
         return arg != null ? "[" + i + "] [type=" + arg.getClass().getName() + "] [value=" + arg + "]" : "[" + i + "] [null]";
      }).collect(Collectors.joining(",\n"));
      return text
         + "\nHandler ["
         + this.getBeanType().getName()
         + "]\nMethod ["
         + this.getBridgedMethod().toGenericString()
         + "] with argument values:\n"
         + formattedArgs;
   }

   private static class MethodValidationInitializer {
      private static final boolean BEAN_VALIDATION_PRESENT = ClassUtils.isPresent("jakarta.validation.Validator", HandlerMethod.class.getClassLoader());
      private static final Predicate<MergedAnnotation<? extends Annotation>> CONSTRAINT_PREDICATE = MergedAnnotationPredicates.typeIn(
         new String[]{"jakarta.validation.Constraint"}
      );
      private static final Predicate<MergedAnnotation<? extends Annotation>> VALID_PREDICATE = MergedAnnotationPredicates.typeIn(
         new String[]{"jakarta.validation.Valid"}
      );

      public static boolean checkArguments(Class<?> beanType, MethodParameter[] parameters) {
         if (BEAN_VALIDATION_PRESENT && AnnotationUtils.findAnnotation(beanType, Validated.class) == null) {
            for (MethodParameter param : parameters) {
               MergedAnnotations merged = MergedAnnotations.from(param.getParameterAnnotations());
               if (merged.stream().anyMatch(CONSTRAINT_PREDICATE)) {
                  return true;
               }

               Class<?> type = param.getParameterType();
               if (merged.stream().anyMatch(VALID_PREDICATE) && isIndexOrKeyBasedContainer(type)) {
                  return true;
               }

               merged = MergedAnnotations.from(getContainerElementAnnotations(param));
               if (merged.stream().anyMatch(CONSTRAINT_PREDICATE.or(VALID_PREDICATE))) {
                  return true;
               }
            }
         }

         return false;
      }

      public static boolean checkReturnValue(Class<?> beanType, Method method) {
         if (BEAN_VALIDATION_PRESENT && AnnotationUtils.findAnnotation(beanType, Validated.class) == null) {
            MergedAnnotations merged = MergedAnnotations.from(method, SearchStrategy.TYPE_HIERARCHY);
            return merged.stream().anyMatch(CONSTRAINT_PREDICATE.or(VALID_PREDICATE));
         } else {
            return false;
         }
      }

      private static boolean isIndexOrKeyBasedContainer(Class<?> type) {
         return List.class.isAssignableFrom(type) || Object[].class.isAssignableFrom(type) || Map.class.isAssignableFrom(type);
      }

      private static Annotation[] getContainerElementAnnotations(MethodParameter param) {
         List<Annotation> result = null;
         int i = param.getParameterIndex();
         Method method = param.getMethod();
         if (method != null && method.getAnnotatedParameterTypes()[i] instanceof AnnotatedParameterizedType apt) {
            for (AnnotatedType type : apt.getAnnotatedActualTypeArguments()) {
               for (Annotation annot : type.getAnnotations()) {
                  result = result != null ? result : new ArrayList<>();
                  result.add(annot);
               }
            }
         }

         result = result != null ? result : Collections.emptyList();
         return result.toArray(new Annotation[0]);
      }
   }
}
