package org.springframework.boot.util;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

public final class LambdaSafe {
   private static final @Nullable Method CLASS_GET_MODULE = ReflectionUtils.findMethod(Class.class, "getModule");
   private static final @Nullable Method MODULE_GET_NAME = CLASS_GET_MODULE != null
      ? ReflectionUtils.findMethod(CLASS_GET_MODULE.getReturnType(), "getName")
      : null;

   private LambdaSafe() {
   }

   public static <C, A> LambdaSafe.Callback<C, A> callback(
      Class<C> callbackType, C callbackInstance, A argument, @Nullable Object @Nullable ... additionalArguments
   ) {
      Assert.notNull(callbackType, "'callbackType' must not be null");
      Assert.notNull(callbackInstance, "'callbackInstance' must not be null");
      return new LambdaSafe.Callback<>(callbackType, callbackInstance, argument, additionalArguments);
   }

   public static <C, A> LambdaSafe.Callbacks<C, A> callbacks(
      Class<C> callbackType, Collection<? extends C> callbackInstances, A argument, Object... additionalArguments
   ) {
      Assert.notNull(callbackType, "'callbackType' must not be null");
      Assert.notNull(callbackInstances, "'callbackInstances' must not be null");
      return new LambdaSafe.Callbacks<>(callbackType, callbackInstances, argument, additionalArguments);
   }

   public static final class Callback<C, A> extends LambdaSafe.LambdaSafeCallback<C, A, LambdaSafe.Callback<C, A>> {
      private final C callbackInstance;

      private Callback(Class<C> callbackType, C callbackInstance, A argument, @Nullable Object @Nullable [] additionalArguments) {
         super(callbackType, argument, additionalArguments);
         this.callbackInstance = callbackInstance;
      }

      public void invoke(Consumer<C> invoker) {
         Supplier<Void> supplier = () -> {
            invoker.accept(this.callbackInstance);
            return null;
         };
         this.invoke(this.callbackInstance, supplier);
      }

      public <R> LambdaSafe.InvocationResult<R> invokeAnd(Function<C, R> invoker) {
         Supplier<R> supplier = () -> invoker.apply(this.callbackInstance);
         return this.invoke(this.callbackInstance, supplier);
      }
   }

   public static final class Callbacks<C, A> extends LambdaSafe.LambdaSafeCallback<C, A, LambdaSafe.Callbacks<C, A>> {
      private final Collection<? extends C> callbackInstances;

      private Callbacks(Class<C> callbackType, Collection<? extends C> callbackInstances, A argument, Object[] additionalArguments) {
         super(callbackType, argument, additionalArguments);
         this.callbackInstances = callbackInstances;
      }

      public void invoke(Consumer<C> invoker) {
         this.callbackInstances.forEach(callbackInstance -> {
            Supplier<Void> supplier = () -> {
               invoker.accept((C)callbackInstance);
               return null;
            };
            this.invoke((C)callbackInstance, supplier);
         });
      }

      public <R> Stream<R> invokeAnd(Function<C, @Nullable R> invoker) {
         Function<C, LambdaSafe.InvocationResult<R>> mapper = callbackInstance -> {
            Supplier<R> supplier = () -> invoker.apply((C)callbackInstance);
            return this.invoke(callbackInstance, supplier);
         };
         return this.callbackInstances.stream().map(mapper).filter(LambdaSafe.InvocationResult::hasResult).map(LambdaSafe.InvocationResult::get);
      }
   }

   @FunctionalInterface
   public interface Filter<C, A> {
      boolean match(Class<C> callbackType, C callbackInstance, A argument, @Nullable Object @Nullable [] additionalArguments);

      static <C, A> LambdaSafe.Filter<C, A> allowAll() {
         return (callbackType, callbackInstance, argument, additionalArguments) -> true;
      }
   }

   private static final class GenericTypeFilter<C, A> implements LambdaSafe.Filter<C, A> {
      @Override
      public boolean match(Class<C> callbackType, C callbackInstance, A argument, @Nullable Object @Nullable [] additionalArguments) {
         ResolvableType type = ResolvableType.forClass(callbackType, callbackInstance.getClass());
         if (type.getGenerics().length != 1) {
            return true;
         }

         Class<?> generic = type.resolveGeneric(new int[0]);
         return generic != null ? generic.isInstance(argument) : true;
      }
   }

   public static final class InvocationResult<R> {
      private static final LambdaSafe.InvocationResult<?> NONE = new LambdaSafe.InvocationResult(null);
      private final @Nullable R value;

      private InvocationResult(@Nullable R value) {
         this.value = value;
      }

      public boolean hasResult() {
         return this != NONE;
      }

      public @Nullable R get() {
         return this.value;
      }

      public @Nullable R get(@Nullable R fallback) {
         return this != NONE ? this.value : fallback;
      }

      public static <R> LambdaSafe.InvocationResult<R> of(@Nullable R value) {
         return new LambdaSafe.InvocationResult<>(value);
      }

      public static <R> LambdaSafe.InvocationResult<R> noResult() {
         return (LambdaSafe.InvocationResult<R>)NONE;
      }
   }

   protected abstract static class LambdaSafeCallback<C, A, SELF extends LambdaSafe.LambdaSafeCallback<C, A, SELF>> {
      private final Class<C> callbackType;
      private final A argument;
      private final @Nullable Object @Nullable [] additionalArguments;
      private Log logger;
      private LambdaSafe.Filter<C, A> filter = new LambdaSafe.GenericTypeFilter<>();

      LambdaSafeCallback(Class<C> callbackType, A argument, @Nullable Object @Nullable [] additionalArguments) {
         this.callbackType = callbackType;
         this.argument = argument;
         this.additionalArguments = additionalArguments;
         this.logger = LogFactory.getLog(callbackType);
      }

      public SELF withLogger(Class<?> loggerSource) {
         return this.withLogger(LogFactory.getLog(loggerSource));
      }

      public SELF withLogger(Log logger) {
         Assert.notNull(logger, "'logger' must not be null");
         this.logger = logger;
         return this.self();
      }

      public SELF withFilter(LambdaSafe.Filter<C, A> filter) {
         Assert.notNull(filter, "'filter' must not be null");
         this.filter = filter;
         return this.self();
      }

      protected final <R> LambdaSafe.InvocationResult<R> invoke(C callbackInstance, Supplier<@Nullable R> supplier) {
         if (this.filter.match(this.callbackType, callbackInstance, this.argument, this.additionalArguments)) {
            try {
               return LambdaSafe.InvocationResult.of(supplier.get());
            } catch (ClassCastException ex) {
               if (!this.isLambdaGenericProblem(ex)) {
                  throw ex;
               }
            }

            this.logNonMatchingType(callbackInstance, ex);
         }

         return LambdaSafe.InvocationResult.noResult();
      }

      private boolean isLambdaGenericProblem(ClassCastException ex) {
         return ex.getMessage() == null || this.startsWithArgumentClassName(ex.getMessage());
      }

      private boolean startsWithArgumentClassName(String message) {
         Predicate<Object> startsWith = argument -> this.startsWithArgumentClassName(message, argument);
         return startsWith.test(this.argument) || this.additionalArgumentsStartsWith(startsWith);
      }

      private boolean additionalArgumentsStartsWith(Predicate<@Nullable Object> startsWith) {
         return this.additionalArguments == null ? false : Stream.of(this.additionalArguments).anyMatch(startsWith);
      }

      private boolean startsWithArgumentClassName(String message, @Nullable Object argument) {
         if (argument == null) {
            return false;
         } else {
            Class<?> argumentType = argument.getClass();
            if (message.startsWith(argumentType.getName())) {
               return true;
            } else if (message.startsWith(argumentType.toString())) {
               return true;
            } else {
               int moduleSeparatorIndex = message.indexOf(47);
               if (moduleSeparatorIndex != -1 && message.startsWith(argumentType.getName(), moduleSeparatorIndex + 1)) {
                  return true;
               } else if (LambdaSafe.CLASS_GET_MODULE != null && LambdaSafe.MODULE_GET_NAME != null) {
                  Object module = ReflectionUtils.invokeMethod(LambdaSafe.CLASS_GET_MODULE, argumentType);
                  Object moduleName = ReflectionUtils.invokeMethod(LambdaSafe.MODULE_GET_NAME, module);
                  return message.startsWith(moduleName + "/" + argumentType.getName());
               } else {
                  return false;
               }
            }
         }
      }

      private void logNonMatchingType(C callback, ClassCastException ex) {
         if (this.logger.isDebugEnabled()) {
            Class<?> expectedType = ResolvableType.forClass(this.callbackType).resolveGeneric(new int[0]);
            String expectedTypeName = expectedType != null ? ClassUtils.getShortName(expectedType) + " type" : "type";
            String message = "Non-matching " + expectedTypeName + " for callback " + ClassUtils.getShortName(this.callbackType) + ": " + callback;
            this.logger.debug(message, ex);
         }
      }

      private SELF self() {
         return (SELF)this;
      }
   }
}
