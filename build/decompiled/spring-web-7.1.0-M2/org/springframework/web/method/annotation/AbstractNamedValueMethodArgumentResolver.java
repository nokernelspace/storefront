package org.springframework.web.method.annotation;

import jakarta.servlet.ServletException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.KParameter.Kind;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestScope;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public abstract class AbstractNamedValueMethodArgumentResolver implements HandlerMethodArgumentResolver {
   private static final boolean KOTLIN_REFLECT_PRESENT = KotlinDetector.isKotlinReflectPresent();
   private final @Nullable ConfigurableBeanFactory configurableBeanFactory;
   private final @Nullable BeanExpressionContext expressionContext;
   private final Map<MethodParameter, AbstractNamedValueMethodArgumentResolver.NamedValueInfo> namedValueInfoCache = new ConcurrentHashMap<>(256);

   public AbstractNamedValueMethodArgumentResolver() {
      this.configurableBeanFactory = null;
      this.expressionContext = null;
   }

   public AbstractNamedValueMethodArgumentResolver(@Nullable ConfigurableBeanFactory beanFactory) {
      this.configurableBeanFactory = beanFactory;
      this.expressionContext = beanFactory != null ? new BeanExpressionContext(beanFactory, new RequestScope()) : null;
   }

   @Override
   public final @Nullable Object resolveArgument(
      MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer, NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory
   ) throws Exception {
      AbstractNamedValueMethodArgumentResolver.NamedValueInfo namedValueInfo = this.getNamedValueInfo(parameter);
      MethodParameter nestedParameter = parameter.nestedIfOptional();
      boolean hasDefaultValue = KOTLIN_REFLECT_PRESENT
         && KotlinDetector.isKotlinType(parameter.getDeclaringClass())
         && AbstractNamedValueMethodArgumentResolver.KotlinDelegate.hasDefaultValue(nestedParameter);
      Object resolvedName = this.resolveEmbeddedValuesAndExpressions(namedValueInfo.name);
      if (resolvedName == null) {
         throw new IllegalArgumentException("Specified name must not resolve to null: [" + namedValueInfo.name + "]");
      }

      Object arg = this.resolveName(resolvedName.toString(), nestedParameter, webRequest);
      if (arg == null) {
         if (namedValueInfo.defaultValue != null) {
            arg = this.resolveEmbeddedValuesAndExpressions(namedValueInfo.defaultValue);
         } else if (namedValueInfo.required && !nestedParameter.isOptional()) {
            this.handleMissingValue(resolvedName.toString(), nestedParameter, webRequest);
         }

         if (!hasDefaultValue) {
            arg = this.handleNullValue(resolvedName.toString(), arg, nestedParameter.getNestedParameterType());
         }
      } else if ("".equals(arg) && namedValueInfo.defaultValue != null) {
         arg = this.resolveEmbeddedValuesAndExpressions(namedValueInfo.defaultValue);
      }

      if (binderFactory != null && (arg != null || !hasDefaultValue)) {
         arg = convertIfNecessary(parameter, webRequest, binderFactory, namedValueInfo, arg);
         if (arg == null) {
            if (namedValueInfo.defaultValue != null) {
               arg = this.resolveEmbeddedValuesAndExpressions(namedValueInfo.defaultValue);
               arg = convertIfNecessary(parameter, webRequest, binderFactory, namedValueInfo, arg);
            } else if (namedValueInfo.required && !nestedParameter.isOptional()) {
               this.handleMissingValueAfterConversion(resolvedName.toString(), nestedParameter, webRequest);
            }
         }
      }

      this.handleResolvedValue(arg, namedValueInfo.name, parameter, mavContainer, webRequest);
      return arg;
   }

   private AbstractNamedValueMethodArgumentResolver.NamedValueInfo getNamedValueInfo(MethodParameter parameter) {
      AbstractNamedValueMethodArgumentResolver.NamedValueInfo namedValueInfo = this.namedValueInfoCache.get(parameter);
      if (namedValueInfo == null) {
         AbstractNamedValueMethodArgumentResolver.NamedValueInfo var3 = this.createNamedValueInfo(parameter);
         namedValueInfo = this.updateNamedValueInfo(parameter, var3);
         this.namedValueInfoCache.put(parameter, namedValueInfo);
      }

      return namedValueInfo;
   }

   protected abstract AbstractNamedValueMethodArgumentResolver.NamedValueInfo createNamedValueInfo(MethodParameter parameter);

   private AbstractNamedValueMethodArgumentResolver.NamedValueInfo updateNamedValueInfo(
      MethodParameter parameter, AbstractNamedValueMethodArgumentResolver.NamedValueInfo info
   ) {
      String name = info.name;
      if (info.name.isEmpty()) {
         name = parameter.getParameterName();
         if (name == null) {
            throw new IllegalArgumentException(
               "Name for argument of type [%s] not specified, and parameter name information not available via reflection. Ensure that the compiler uses the '-parameters' flag."
                  .formatted(parameter.getNestedParameterType().getName())
            );
         }
      }

      String defaultValue = "\n\t\t\n\t\t\n\ue000\ue001\ue002\n\t\t\t\t\n".equals(info.defaultValue) ? null : info.defaultValue;
      return new AbstractNamedValueMethodArgumentResolver.NamedValueInfo(name, info.required, defaultValue);
   }

   private @Nullable Object resolveEmbeddedValuesAndExpressions(String value) {
      if (this.configurableBeanFactory != null && this.expressionContext != null) {
         String placeholdersResolved = this.configurableBeanFactory.resolveEmbeddedValue(value);
         BeanExpressionResolver exprResolver = this.configurableBeanFactory.getBeanExpressionResolver();
         return exprResolver == null ? value : exprResolver.evaluate(placeholdersResolved, this.expressionContext);
      } else {
         return value;
      }
   }

   protected abstract @Nullable Object resolveName(String name, MethodParameter parameter, NativeWebRequest request) throws Exception;

   protected void handleMissingValue(String name, MethodParameter parameter, NativeWebRequest request) throws Exception {
      this.handleMissingValue(name, parameter);
   }

   protected void handleMissingValue(String name, MethodParameter parameter) throws ServletException {
      throw new ServletRequestBindingException(
         "Missing argument '" + name + "' for method parameter of type " + parameter.getNestedParameterType().getSimpleName()
      );
   }

   protected void handleMissingValueAfterConversion(String name, MethodParameter parameter, NativeWebRequest request) throws Exception {
      this.handleMissingValue(name, parameter, request);
   }

   private @Nullable Object handleNullValue(String name, @Nullable Object value, Class<?> paramType) {
      if (value == null) {
         if (paramType == boolean.class) {
            return Boolean.FALSE;
         }

         if (paramType.isPrimitive()) {
            throw new IllegalStateException(
               "Optional "
                  + paramType.getSimpleName()
                  + " parameter '"
                  + name
                  + "' is present but cannot be translated into a null value due to being declared as a primitive type. Consider declaring it as object wrapper for the corresponding primitive type."
            );
         }
      }

      return value;
   }

   private static @Nullable Object convertIfNecessary(
      MethodParameter parameter,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory,
      AbstractNamedValueMethodArgumentResolver.NamedValueInfo namedValueInfo,
      @Nullable Object arg
   ) throws Exception {
      WebDataBinder binder = binderFactory.createBinder(webRequest, null, namedValueInfo.name);
      Class<?> parameterType = parameter.getParameterType();
      if (KotlinDetector.isInlineClass(parameterType)) {
         Constructor<?> ctor = BeanUtils.findPrimaryConstructor(parameterType);
         if (ctor != null) {
            parameterType = ctor.getParameterTypes()[0];
         }
      }

      try {
         return binder.convertIfNecessary(arg, parameterType, parameter);
      } catch (ConversionNotSupportedException ex) {
         throw new MethodArgumentConversionNotSupportedException(arg, ex.getRequiredType(), namedValueInfo.name, parameter, ex.getCause());
      } catch (TypeMismatchException ex) {
         throw new MethodArgumentTypeMismatchException(arg, ex.getRequiredType(), namedValueInfo.name, parameter, ex.getCause());
      }
   }

   protected void handleResolvedValue(
      @Nullable Object arg, String name, MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer, NativeWebRequest webRequest
   ) {
   }

   private static class KotlinDelegate {
      public static boolean hasDefaultValue(MethodParameter parameter) {
         Method method = parameter.getMethod();
         if (method == null) {
            return false;
         }

         KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
         if (function != null) {
            int index = 0;

            for (KParameter kParameter : function.getParameters()) {
               if (Kind.VALUE.equals(kParameter.getKind()) && parameter.getParameterIndex() == index++) {
                  return kParameter.isOptional();
               }
            }
         }

         return false;
      }
   }

   protected static class NamedValueInfo {
      private final String name;
      private final boolean required;
      private final @Nullable String defaultValue;

      public NamedValueInfo(String name, boolean required, @Nullable String defaultValue) {
         this.name = name;
         this.required = required;
         this.defaultValue = defaultValue;
      }
   }
}
