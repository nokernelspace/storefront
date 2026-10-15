package org.springframework.web.server

import kotlin.coroutines.CoroutineContext
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.MonoKt
import reactor.core.publisher.Mono

public abstract class CoWebExceptionHandler : WebExceptionHandler {
   public override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> {
      val context: CoroutineContext = exchange.getAttributes().get(CoWebFilter.COROUTINE_CONTEXT_ATTRIBUTE) as CoroutineContext
      var var10000: CoroutineContext = context
      if (context == null) {
         var10000 = Dispatchers.getUnconfined() as CoroutineContext
      }

      val var4: Mono = MonoKt.mono(var10000, {
         // $VF: Could not decompile lambda - root function was not found. Is this a suspend lambda?
      } as Function2).then()
      return var4
   }

   protected abstract suspend fun coHandle(exchange: ServerWebExchange, ex: Throwable) {
   }
}
