package org.springframework.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Objects;
import kotlin.Unit;
import kotlin.coroutines.CoroutineContext;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.KType;
import kotlin.reflect.full.KCallables;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.full.KClassifiers;
import kotlin.reflect.full.KTypes;
import kotlin.reflect.jvm.KCallablesJvm;
import kotlin.reflect.jvm.ReflectJvmMapping;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Deferred;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.reactor.MonoKt;
import kotlinx.coroutines.reactor.ReactorFlowKt;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;

public abstract class CoroutinesUtils {
   private static final KType flowType = KClassifiers.getStarProjectedType(JvmClassMappingKt.getKotlinClass(Flow.class));
   private static final KType monoType = KClassifiers.getStarProjectedType(JvmClassMappingKt.getKotlinClass(Mono.class));
   private static final KType publisherType = KClassifiers.getStarProjectedType(JvmClassMappingKt.getKotlinClass(Publisher.class));

   public static <T> Mono<T> deferredToMono(Deferred<T> source) {
      return MonoKt.mono(Dispatchers.getUnconfined(), (scope, continuation) -> source.await(continuation));
   }

   public static <T> Deferred<T> monoToDeferred(Mono<T> source) {
      return BuildersKt.async(
         GlobalScope.INSTANCE, Dispatchers.getUnconfined(), CoroutineStart.DEFAULT, (scope, continuation) -> MonoKt.awaitSingleOrNull(source, continuation)
      );
   }

   public static Publisher<?> invokeSuspendingFunction(Method method, Object target, @Nullable Object... args) {
      CoroutineContext context = (CoroutineContext)(Hooks.isAutomaticContextPropagationEnabled()
         ? Dispatchers.getUnconfined().plus(new PropagationContextElement())
         : Dispatchers.getUnconfined());
      return invokeSuspendingFunction(context, method, target, args);
   }

   public static Publisher<?> invokeSuspendingFunction(CoroutineContext context, Method method, @Nullable Object target, @Nullable Object... args) {
      Assert.isTrue(KotlinDetector.isSuspendingFunction(method), "Method must be a suspending function");
      KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
      Assert.notNull(function, () -> "Failed to get Kotlin function for method: " + method);
      if (!KCallablesJvm.isAccessible(function)) {
         KCallablesJvm.setAccessible(function, true);
      }

      Mono<Object> mono = MonoKt.mono(
            context,
            (scope, continuation) -> {
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

               return KCallables.callSuspendBy(function, argMap, continuation);
            }
         )
         .filter(result -> result != Unit.INSTANCE)
         .onErrorMap(InvocationTargetException.class, InvocationTargetException::getTargetException);
      KType returnType = function.getReturnType();
      if (KTypes.isSubtypeOf(returnType, flowType)) {
         return mono.flatMapMany(CoroutinesUtils::asFlux);
      } else if (KTypes.isSubtypeOf(returnType, publisherType)) {
         return (Publisher<?>)(KTypes.isSubtypeOf(returnType, monoType) ? mono.flatMap(o -> (Mono)o) : mono.flatMapMany(o -> (Publisher)o));
      } else {
         return mono;
      }
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

   private static Flux<?> asFlux(Object flow) {
      return ReactorFlowKt.asFlux((Flow)flow);
   }
}
