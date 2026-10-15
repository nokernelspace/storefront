package org.springframework.aop.framework;

import kotlin.coroutines.Continuation;
import kotlinx.coroutines.reactive.ReactiveFlowKt;
import kotlinx.coroutines.reactor.MonoKt;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

abstract class CoroutinesUtils {
   static Object asFlow(@Nullable Object publisher) {
      if (publisher instanceof Publisher<?> rsPublisher) {
         return ReactiveFlowKt.asFlow(rsPublisher);
      } else {
         throw new IllegalArgumentException("Not a Reactive Streams Publisher: " + publisher);
      }
   }

   static @Nullable Object awaitSingleOrNull(@Nullable Object value, Object continuation) {
      return MonoKt.awaitSingleOrNull(value instanceof Mono mono ? mono : Mono.justOrEmpty(value), (Continuation)continuation);
   }
}
