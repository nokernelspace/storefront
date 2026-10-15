package org.springframework.web.method.support;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import kotlin.Unit;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.KType;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.jvm.KCallablesJvm;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.core.CoroutinesUtils;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.method.MethodValidator;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;

public class InvocableHandlerMethod extends HandlerMethod {
   private static final Object[] EMPTY_ARGS = new Object[0];
   private static final boolean KOTLIN_REFLECT_PRESENT = KotlinDetector.isKotlinReflectPresent();
   private HandlerMethodArgumentResolverComposite resolvers = new HandlerMethodArgumentResolverComposite();
   private ParameterNameDiscoverer parameterNameDiscoverer = DefaultParameterNameDiscoverer.getSharedInstance();
   private @Nullable WebDataBinderFactory dataBinderFactory;
   private @Nullable MethodValidator methodValidator;

   public InvocableHandlerMethod(HandlerMethod handlerMethod) {
      super(handlerMethod);
   }

   public InvocableHandlerMethod(Object bean, Method method) {
      super(bean, method);
   }

   protected InvocableHandlerMethod(Object bean, Method method, @Nullable MessageSource messageSource) {
      super(bean, method, messageSource);
   }

   public InvocableHandlerMethod(Object bean, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
      super(bean, methodName, parameterTypes);
   }

   public void setHandlerMethodArgumentResolvers(HandlerMethodArgumentResolverComposite argumentResolvers) {
      this.resolvers = argumentResolvers;
   }

   public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
      this.parameterNameDiscoverer = parameterNameDiscoverer;
   }

   public void setDataBinderFactory(WebDataBinderFactory dataBinderFactory) {
      this.dataBinderFactory = dataBinderFactory;
   }

   public void setMethodValidator(@Nullable MethodValidator methodValidator) {
      this.methodValidator = methodValidator;
   }

   public Object invokeForRequest(NativeWebRequest request, ModelAndViewContainer mavContainer, Object... providedArgs) throws Exception {
      Object[] args = this.getMethodArgumentValues(request, mavContainer, providedArgs);
      if (logger.isTraceEnabled()) {
         logger.trace("Arguments: " + Arrays.toString(args));
      }

      if (this.shouldValidateArguments() && this.methodValidator != null) {
         this.methodValidator.applyArgumentValidation(this.getBean(), this.getBridgedMethod(), this.getMethodParameters(), args, this.getValidationGroups());
      }

      Object returnValue = this.doInvoke(args);
      if (this.shouldValidateReturnValue() && this.methodValidator != null) {
         this.methodValidator
            .applyReturnValueValidation(this.getBean(), this.getBridgedMethod(), this.getReturnType(), returnValue, this.getValidationGroups());
      }

      return returnValue;
   }

   protected Object[] getMethodArgumentValues(NativeWebRequest request, ModelAndViewContainer mavContainer, Object... providedArgs) throws Exception {
      MethodParameter[] parameters = this.getMethodParameters();
      if (ObjectUtils.isEmpty(parameters)) {
         return EMPTY_ARGS;
      }

      Object[] args = new Object[parameters.length];

      for (int i = 0; i < parameters.length; i++) {
         MethodParameter parameter = parameters[i];
         parameter.initParameterNameDiscovery(this.parameterNameDiscoverer);
         args[i] = findProvidedArgument(parameter, providedArgs);
         if (args[i] == null) {
            if (parameter.getParameterType().equals(HandlerMethod.class) && parameter.isOptional()) {
               args[i] = null;
            } else {
               if (!this.resolvers.supportsParameter(parameter)) {
                  throw new IllegalStateException(formatArgumentError(parameter, "No suitable resolver"));
               }

               try {
                  args[i] = this.resolvers.resolveArgument(parameter, mavContainer, request, this.dataBinderFactory);
               } catch (Exception ex) {
                  if (logger.isDebugEnabled()) {
                     String exMsg = ex.getMessage();
                     if (exMsg != null && !exMsg.contains(parameter.getExecutable().toGenericString())) {
                        logger.debug(formatArgumentError(parameter, exMsg));
                     }
                  }

                  throw ex;
               }
            }
         }
      }

      return args;
   }

   protected @Nullable Object doInvoke(@Nullable Object... args) throws Exception {
      Method method = this.getBridgedMethod();

      try {
         if (!KOTLIN_REFLECT_PRESENT || !KotlinDetector.isKotlinType(method.getDeclaringClass())) {
            return method.invoke(this.getBean(), args);
         } else {
            return KotlinDetector.isSuspendingFunction(method)
               ? this.invokeSuspendingFunction(method, this.getBean(), args)
               : InvocableHandlerMethod.KotlinDelegate.invokeFunction(method, this.getBean(), args);
         }
      } catch (IllegalArgumentException ex) {
         this.assertTargetBean(method, this.getBean(), args);
         String text = ex.getMessage() != null && !(ex.getCause() instanceof NullPointerException) ? ex.getMessage() : "Illegal argument";
         throw new IllegalStateException(this.formatInvokeError(text, args), ex);
      } catch (InvocationTargetException ex) {
         Throwable targetException = ex.getCause();
         if (targetException instanceof RuntimeException runtimeException) {
            throw runtimeException;
         } else if (targetException instanceof Error error) {
            throw error;
         } else if (targetException instanceof Exception exception) {
            throw exception;
         } else {
            throw new IllegalStateException(this.formatInvokeError("Invocation failure", args), targetException);
         }
      }
   }

   protected Object invokeSuspendingFunction(Method method, Object target, @Nullable Object[] args) {
      Object result = CoroutinesUtils.invokeSuspendingFunction(method, target, args);
      return result instanceof Mono<?> mono ? mono.handle(InvocableHandlerMethod.KotlinDelegate::handleResult) : result;
   }

   private static class KotlinDelegate {
      public static @Nullable Object invokeFunction(Method method, Object target, @Nullable Object[] args) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
         KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
         if (function == null) {
            return method.invoke(target, args);
         }

         if (!KCallablesJvm.isAccessible(function)) {
            KCallablesJvm.setAccessible(function, true);
         }

         Map<KParameter, Object> argMap = CollectionUtils.newHashMap(args.length + 1);
         int index = 0;

         for (KParameter parameter : function.getParameters()) {
            switch (parameter.getKind()) {
               case INSTANCE:
                  argMap.put(parameter, target);
                  break;
               case VALUE:
               case EXTENSION_RECEIVER:
                  Object arg = args[index];
                  if (!parameter.isOptional() || arg != null) {
                     KType type = parameter.getType();
                     if ((!type.isMarkedNullable() || arg != null)
                        && type.getClassifier() instanceof KClass<?> kClass
                        && KotlinDetector.isInlineClass(JvmClassMappingKt.getJavaClass(kClass))
                        && !JvmClassMappingKt.getJavaClass(kClass).isInstance(arg)) {
                        arg = box(kClass, arg);
                     }

                     argMap.put(parameter, arg);
                  }

                  index++;
            }
         }

         Object result = function.callBy(argMap);
         if (result != null && KotlinDetector.isInlineClass(result.getClass())) {
            result = unbox(result);
         }

         return result == Unit.INSTANCE ? null : result;
      }

      private static Object box(KClass<?> kClass, @Nullable Object arg) {
         KFunction<?> constructor = Objects.requireNonNull(KClasses.getPrimaryConstructor(kClass));
         KType type = ((KParameter)constructor.getParameters().get(0)).getType();
         if ((!type.isMarkedNullable() || arg != null)
            && type.getClassifier() instanceof KClass<?> parameterClass
            && KotlinDetector.isInlineClass(JvmClassMappingKt.getJavaClass(parameterClass))
            && !JvmClassMappingKt.getJavaClass(parameterClass).isInstance(arg)) {
            arg = box(parameterClass, arg);
         }

         if (!KCallablesJvm.isAccessible(constructor)) {
            KCallablesJvm.setAccessible(constructor, true);
         }

         return constructor.call(new Object[]{arg});
      }

      private static void handleResult(Object result, SynchronousSink<Object> sink) {
         if (KotlinDetector.isInlineClass(result.getClass())) {
            try {
               Object unboxed = unbox(result);
               if (unboxed != Unit.INSTANCE) {
                  sink.next(unboxed);
               }

               sink.complete();
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException ex) {
               sink.error(ex);
            }
         } else {
            sink.next(result);
            sink.complete();
         }
      }

      private static Object unbox(Object result) throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
         Object unboxed = result.getClass().getDeclaredMethod("unbox-impl").invoke(result);
         return KotlinDetector.isInlineClass(unboxed.getClass()) ? unbox(unboxed) : unboxed;
      }
   }
}
