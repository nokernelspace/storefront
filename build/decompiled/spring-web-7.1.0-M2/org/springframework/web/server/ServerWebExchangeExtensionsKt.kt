package org.springframework.web.server

import java.security.Principal
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.functions.Function2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactor.MonoKt
import org.springframework.http.codec.multipart.Part
import org.springframework.util.MultiValueMap
import org.springframework.web.server.ServerWebExchange.Builder

public suspend fun ServerWebExchange.awaitFormData(): MultiValueMap<String, String> {
   // $VF: Couldn't be decompiled
   // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
   // java.lang.IllegalStateException: Anonymous class does not have Class Kotlin metadata
   //   at org.vineflower.kotlin.KotlinWriter.writeClassDefinition(KotlinWriter.java:742)
   //   at org.vineflower.kotlin.KotlinWriter.writeClass(KotlinWriter.java:309)
   //   at org.vineflower.kotlin.expr.KNewExprent.toJava(KNewExprent.java:178)
   //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.getCastedExprent(ExprProcessor.java:1054)
   //   at org.jetbrains.java.decompiler.modules.decompiler.exps.AssignmentExprent.toJava(AssignmentExprent.java:161)
   //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.listToJava(ExprProcessor.java:925)
   //   at org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement.toJava(BasicBlockStatement.java:87)
   //
   // Bytecode:
   // 00: aload 1
   // 01: instanceof org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitFormData$1
   // 04: ifeq 24
   // 07: aload 1
   // 08: checkcast org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitFormData$1
   // 0b: astore 3
   // 0c: aload 3
   // 0d: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitFormData$1.label I
   // 10: ldc -2147483648
   // 12: iand
   // 13: ifeq 24
   // 16: aload 3
   // 17: dup
   // 18: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitFormData$1.label I
   // 1b: ldc -2147483648
   // 1d: isub
   // 1e: putfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitFormData$1.label I
   // 21: goto 2d
   // 24: new org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitFormData$1
   // 27: dup
   // 28: aload 1
   // 29: invokespecial org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitFormData$1.<init> (Lkotlin/coroutines/Continuation;)V
   // 2c: astore 3
   // 2d: aload 3
   // 2e: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitFormData$1.result Ljava/lang/Object;
   // 31: astore 2
   // 32: invokestatic kotlin/coroutines/intrinsics/IntrinsicsKt.getCOROUTINE_SUSPENDED ()Ljava/lang/Object;
   // 35: astore 4
   // 37: aload 3
   // 38: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitFormData$1.label I
   // 3b: tableswitch 83 0 1 21 63
   // 50: aload 2
   // 51: invokestatic kotlin/ResultKt.throwOnFailure (Ljava/lang/Object;)V
   // 54: aload 0
   // 55: invokeinterface org/springframework/web/server/ServerWebExchange.getFormData ()Lreactor/core/publisher/Mono; 1
   // 5a: dup
   // 5b: ldc "getFormData(...)"
   // 5d: invokestatic kotlin/jvm/internal/Intrinsics.checkNotNullExpressionValue (Ljava/lang/Object;Ljava/lang/String;)V
   // 60: aload 3
   // 61: aload 3
   // 62: aload 0
   // 63: invokestatic kotlin/coroutines/jvm/internal/SpillingKt.nullOutSpilledVariable (Ljava/lang/Object;)Ljava/lang/Object;
   // 66: putfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitFormData$1.L$0 Ljava/lang/Object;
   // 69: aload 3
   // 6a: bipush 1
   // 6b: putfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitFormData$1.label I
   // 6e: invokestatic kotlinx/coroutines/reactor/MonoKt.awaitSingle (Lreactor/core/publisher/Mono;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;
   // 71: dup
   // 72: aload 4
   // 74: if_acmpne 87
   // 77: aload 4
   // 79: areturn
   // 7a: aload 3
   // 7b: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitFormData$1.L$0 Ljava/lang/Object;
   // 7e: checkcast org/springframework/web/server/ServerWebExchange
   // 81: astore 0
   // 82: aload 2
   // 83: invokestatic kotlin/ResultKt.throwOnFailure (Ljava/lang/Object;)V
   // 86: aload 2
   // 87: dup
   // 88: ldc "awaitSingle(...)"
   // 8a: invokestatic kotlin/jvm/internal/Intrinsics.checkNotNullExpressionValue (Ljava/lang/Object;Ljava/lang/String;)V
   // 8d: areturn
   // 8e: new java/lang/IllegalStateException
   // 91: dup
   // 92: ldc "call to 'resume' before 'invoke' with coroutine"
   // 94: invokespecial java/lang/IllegalStateException.<init> (Ljava/lang/String;)V
   // 97: athrow
}

public suspend fun ServerWebExchange.awaitMultipartData(): MultiValueMap<String, Part> {
   // $VF: Couldn't be decompiled
   // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
   // java.lang.IllegalStateException: Anonymous class does not have Class Kotlin metadata
   //   at org.vineflower.kotlin.KotlinWriter.writeClassDefinition(KotlinWriter.java:742)
   //   at org.vineflower.kotlin.KotlinWriter.writeClass(KotlinWriter.java:309)
   //   at org.vineflower.kotlin.expr.KNewExprent.toJava(KNewExprent.java:178)
   //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.getCastedExprent(ExprProcessor.java:1054)
   //   at org.jetbrains.java.decompiler.modules.decompiler.exps.AssignmentExprent.toJava(AssignmentExprent.java:161)
   //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.listToJava(ExprProcessor.java:925)
   //   at org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement.toJava(BasicBlockStatement.java:87)
   //
   // Bytecode:
   // 00: aload 1
   // 01: instanceof org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitMultipartData$1
   // 04: ifeq 24
   // 07: aload 1
   // 08: checkcast org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitMultipartData$1
   // 0b: astore 3
   // 0c: aload 3
   // 0d: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitMultipartData$1.label I
   // 10: ldc -2147483648
   // 12: iand
   // 13: ifeq 24
   // 16: aload 3
   // 17: dup
   // 18: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitMultipartData$1.label I
   // 1b: ldc -2147483648
   // 1d: isub
   // 1e: putfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitMultipartData$1.label I
   // 21: goto 2d
   // 24: new org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitMultipartData$1
   // 27: dup
   // 28: aload 1
   // 29: invokespecial org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitMultipartData$1.<init> (Lkotlin/coroutines/Continuation;)V
   // 2c: astore 3
   // 2d: aload 3
   // 2e: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitMultipartData$1.result Ljava/lang/Object;
   // 31: astore 2
   // 32: invokestatic kotlin/coroutines/intrinsics/IntrinsicsKt.getCOROUTINE_SUSPENDED ()Ljava/lang/Object;
   // 35: astore 4
   // 37: aload 3
   // 38: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitMultipartData$1.label I
   // 3b: tableswitch 83 0 1 21 63
   // 50: aload 2
   // 51: invokestatic kotlin/ResultKt.throwOnFailure (Ljava/lang/Object;)V
   // 54: aload 0
   // 55: invokeinterface org/springframework/web/server/ServerWebExchange.getMultipartData ()Lreactor/core/publisher/Mono; 1
   // 5a: dup
   // 5b: ldc "getMultipartData(...)"
   // 5d: invokestatic kotlin/jvm/internal/Intrinsics.checkNotNullExpressionValue (Ljava/lang/Object;Ljava/lang/String;)V
   // 60: aload 3
   // 61: aload 3
   // 62: aload 0
   // 63: invokestatic kotlin/coroutines/jvm/internal/SpillingKt.nullOutSpilledVariable (Ljava/lang/Object;)Ljava/lang/Object;
   // 66: putfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitMultipartData$1.L$0 Ljava/lang/Object;
   // 69: aload 3
   // 6a: bipush 1
   // 6b: putfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitMultipartData$1.label I
   // 6e: invokestatic kotlinx/coroutines/reactor/MonoKt.awaitSingle (Lreactor/core/publisher/Mono;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;
   // 71: dup
   // 72: aload 4
   // 74: if_acmpne 87
   // 77: aload 4
   // 79: areturn
   // 7a: aload 3
   // 7b: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitMultipartData$1.L$0 Ljava/lang/Object;
   // 7e: checkcast org/springframework/web/server/ServerWebExchange
   // 81: astore 0
   // 82: aload 2
   // 83: invokestatic kotlin/ResultKt.throwOnFailure (Ljava/lang/Object;)V
   // 86: aload 2
   // 87: dup
   // 88: ldc "awaitSingle(...)"
   // 8a: invokestatic kotlin/jvm/internal/Intrinsics.checkNotNullExpressionValue (Ljava/lang/Object;Ljava/lang/String;)V
   // 8d: areturn
   // 8e: new java/lang/IllegalStateException
   // 91: dup
   // 92: ldc "call to 'resume' before 'invoke' with coroutine"
   // 94: invokespecial java/lang/IllegalStateException.<init> (Ljava/lang/String;)V
   // 97: athrow
}

public suspend fun <T : Principal> ServerWebExchange.awaitPrincipal(): T {
   // $VF: Couldn't be decompiled
   // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
   // java.lang.IllegalStateException: Anonymous class does not have Class Kotlin metadata
   //   at org.vineflower.kotlin.KotlinWriter.writeClassDefinition(KotlinWriter.java:742)
   //   at org.vineflower.kotlin.KotlinWriter.writeClass(KotlinWriter.java:309)
   //   at org.vineflower.kotlin.expr.KNewExprent.toJava(KNewExprent.java:178)
   //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.getCastedExprent(ExprProcessor.java:1054)
   //   at org.jetbrains.java.decompiler.modules.decompiler.exps.AssignmentExprent.toJava(AssignmentExprent.java:161)
   //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.listToJava(ExprProcessor.java:925)
   //   at org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement.toJava(BasicBlockStatement.java:87)
   //
   // Bytecode:
   // 00: aload 1
   // 01: instanceof org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitPrincipal$1
   // 04: ifeq 24
   // 07: aload 1
   // 08: checkcast org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitPrincipal$1
   // 0b: astore 3
   // 0c: aload 3
   // 0d: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitPrincipal$1.label I
   // 10: ldc -2147483648
   // 12: iand
   // 13: ifeq 24
   // 16: aload 3
   // 17: dup
   // 18: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitPrincipal$1.label I
   // 1b: ldc -2147483648
   // 1d: isub
   // 1e: putfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitPrincipal$1.label I
   // 21: goto 2d
   // 24: new org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitPrincipal$1
   // 27: dup
   // 28: aload 1
   // 29: invokespecial org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitPrincipal$1.<init> (Lkotlin/coroutines/Continuation;)V
   // 2c: astore 3
   // 2d: aload 3
   // 2e: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitPrincipal$1.result Ljava/lang/Object;
   // 31: astore 2
   // 32: invokestatic kotlin/coroutines/intrinsics/IntrinsicsKt.getCOROUTINE_SUSPENDED ()Ljava/lang/Object;
   // 35: astore 4
   // 37: aload 3
   // 38: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitPrincipal$1.label I
   // 3b: tableswitch 83 0 1 21 63
   // 50: aload 2
   // 51: invokestatic kotlin/ResultKt.throwOnFailure (Ljava/lang/Object;)V
   // 54: aload 0
   // 55: invokeinterface org/springframework/web/server/ServerWebExchange.getPrincipal ()Lreactor/core/publisher/Mono; 1
   // 5a: dup
   // 5b: ldc "getPrincipal(...)"
   // 5d: invokestatic kotlin/jvm/internal/Intrinsics.checkNotNullExpressionValue (Ljava/lang/Object;Ljava/lang/String;)V
   // 60: aload 3
   // 61: aload 3
   // 62: aload 0
   // 63: invokestatic kotlin/coroutines/jvm/internal/SpillingKt.nullOutSpilledVariable (Ljava/lang/Object;)Ljava/lang/Object;
   // 66: putfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitPrincipal$1.L$0 Ljava/lang/Object;
   // 69: aload 3
   // 6a: bipush 1
   // 6b: putfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitPrincipal$1.label I
   // 6e: invokestatic kotlinx/coroutines/reactor/MonoKt.awaitSingle (Lreactor/core/publisher/Mono;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;
   // 71: dup
   // 72: aload 4
   // 74: if_acmpne 87
   // 77: aload 4
   // 79: areturn
   // 7a: aload 3
   // 7b: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitPrincipal$1.L$0 Ljava/lang/Object;
   // 7e: checkcast org/springframework/web/server/ServerWebExchange
   // 81: astore 0
   // 82: aload 2
   // 83: invokestatic kotlin/ResultKt.throwOnFailure (Ljava/lang/Object;)V
   // 86: aload 2
   // 87: dup
   // 88: ldc "awaitSingle(...)"
   // 8a: invokestatic kotlin/jvm/internal/Intrinsics.checkNotNullExpressionValue (Ljava/lang/Object;Ljava/lang/String;)V
   // 8d: areturn
   // 8e: new java/lang/IllegalStateException
   // 91: dup
   // 92: ldc "call to 'resume' before 'invoke' with coroutine"
   // 94: invokespecial java/lang/IllegalStateException.<init> (Ljava/lang/String;)V
   // 97: athrow
}

public suspend fun ServerWebExchange.awaitSession(): WebSession {
   // $VF: Couldn't be decompiled
   // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
   // java.lang.IllegalStateException: Anonymous class does not have Class Kotlin metadata
   //   at org.vineflower.kotlin.KotlinWriter.writeClassDefinition(KotlinWriter.java:742)
   //   at org.vineflower.kotlin.KotlinWriter.writeClass(KotlinWriter.java:309)
   //   at org.vineflower.kotlin.expr.KNewExprent.toJava(KNewExprent.java:178)
   //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.getCastedExprent(ExprProcessor.java:1054)
   //   at org.jetbrains.java.decompiler.modules.decompiler.exps.AssignmentExprent.toJava(AssignmentExprent.java:161)
   //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.listToJava(ExprProcessor.java:925)
   //   at org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement.toJava(BasicBlockStatement.java:87)
   //
   // Bytecode:
   // 00: aload 1
   // 01: instanceof org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitSession$1
   // 04: ifeq 24
   // 07: aload 1
   // 08: checkcast org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitSession$1
   // 0b: astore 3
   // 0c: aload 3
   // 0d: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitSession$1.label I
   // 10: ldc -2147483648
   // 12: iand
   // 13: ifeq 24
   // 16: aload 3
   // 17: dup
   // 18: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitSession$1.label I
   // 1b: ldc -2147483648
   // 1d: isub
   // 1e: putfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitSession$1.label I
   // 21: goto 2d
   // 24: new org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitSession$1
   // 27: dup
   // 28: aload 1
   // 29: invokespecial org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitSession$1.<init> (Lkotlin/coroutines/Continuation;)V
   // 2c: astore 3
   // 2d: aload 3
   // 2e: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitSession$1.result Ljava/lang/Object;
   // 31: astore 2
   // 32: invokestatic kotlin/coroutines/intrinsics/IntrinsicsKt.getCOROUTINE_SUSPENDED ()Ljava/lang/Object;
   // 35: astore 4
   // 37: aload 3
   // 38: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitSession$1.label I
   // 3b: tableswitch 83 0 1 21 63
   // 50: aload 2
   // 51: invokestatic kotlin/ResultKt.throwOnFailure (Ljava/lang/Object;)V
   // 54: aload 0
   // 55: invokeinterface org/springframework/web/server/ServerWebExchange.getSession ()Lreactor/core/publisher/Mono; 1
   // 5a: dup
   // 5b: ldc "getSession(...)"
   // 5d: invokestatic kotlin/jvm/internal/Intrinsics.checkNotNullExpressionValue (Ljava/lang/Object;Ljava/lang/String;)V
   // 60: aload 3
   // 61: aload 3
   // 62: aload 0
   // 63: invokestatic kotlin/coroutines/jvm/internal/SpillingKt.nullOutSpilledVariable (Ljava/lang/Object;)Ljava/lang/Object;
   // 66: putfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitSession$1.L$0 Ljava/lang/Object;
   // 69: aload 3
   // 6a: bipush 1
   // 6b: putfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitSession$1.label I
   // 6e: invokestatic kotlinx/coroutines/reactor/MonoKt.awaitSingle (Lreactor/core/publisher/Mono;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;
   // 71: dup
   // 72: aload 4
   // 74: if_acmpne 87
   // 77: aload 4
   // 79: areturn
   // 7a: aload 3
   // 7b: getfield org/springframework/web/server/ServerWebExchangeExtensionsKt$awaitSession$1.L$0 Ljava/lang/Object;
   // 7e: checkcast org/springframework/web/server/ServerWebExchange
   // 81: astore 0
   // 82: aload 2
   // 83: invokestatic kotlin/ResultKt.throwOnFailure (Ljava/lang/Object;)V
   // 86: aload 2
   // 87: dup
   // 88: ldc "awaitSingle(...)"
   // 8a: invokestatic kotlin/jvm/internal/Intrinsics.checkNotNullExpressionValue (Ljava/lang/Object;Ljava/lang/String;)V
   // 8d: areturn
   // 8e: new java/lang/IllegalStateException
   // 91: dup
   // 92: ldc "call to 'resume' before 'invoke' with coroutine"
   // 94: invokespecial java/lang/IllegalStateException.<init> (Ljava/lang/String;)V
   // 97: athrow
}

public fun Builder.principal(supplier: (Continuation<Principal>) -> Any?): Builder {
   val var10000: ServerWebExchange.Builder = `$this$principal`.principal(MonoKt.mono(Dispatchers.getUnconfined() as CoroutineContext, {
      // $VF: Could not decompile lambda - root function was not found. Is this a suspend lambda?
   } as Function2))
   return var10000
}
