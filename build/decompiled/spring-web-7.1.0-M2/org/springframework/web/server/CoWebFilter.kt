package org.springframework.web.server

import kotlin.coroutines.CoroutineContext
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.MonoKt
import org.jetbrains.annotations.NotNull
import reactor.core.publisher.Mono

public abstract class CoWebFilter : WebFilter {
   public override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
      val context: CoroutineContext = exchange.getAttributes().get(COROUTINE_CONTEXT_ATTRIBUTE) as CoroutineContext
      var var10000: CoroutineContext = context
      if (context == null) {
         var10000 = Dispatchers.getUnconfined() as CoroutineContext
      }

      val var4: Mono = MonoKt.mono(var10000, {
         // $VF: Could not decompile lambda - root function was not found. Is this a suspend lambda?
      } as Function2).then()
      return var4
   }

   protected abstract suspend fun filter(exchange: ServerWebExchange, chain: CoWebFilterChain) {
   }

   public companion object {
      @JvmField
      @NotNull
      public final val COROUTINE_CONTEXT_ATTRIBUTE: String
   }
}
