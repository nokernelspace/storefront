@file:SourceDebugExtension(["SMAP\nProxyHintsExtensions.kt\nKotlin\n*S Kotlin\n*F\n+ 1 ProxyHintsExtensions.kt\norg/springframework/aot/hint/ProxyHintsExtensionsKt\n+ 2 _Arrays.kt\nkotlin/collections/ArraysKt___ArraysKt\n+ 3 ArraysJVM.kt\nkotlin/collections/ArraysKt__ArraysJVMKt\n*L\n1#1,29:1\n12850#2:30\n13197#2,3:31\n37#3,2:34\n*S KotlinDebug\n*F\n+ 1 ProxyHintsExtensions.kt\norg/springframework/aot/hint/ProxyHintsExtensionsKt\n*L\n28#1:30\n28#1:31,3\n28#1:34,2\n*E\n"])

package org.springframework.aot.hint

import java.util.ArrayList
import java.util.Arrays
import kotlin.jvm.internal.SourceDebugExtension
import kotlin.reflect.KClass

public fun ProxyHints.registerJdkProxy(vararg proxiedInterfaces: KClass<*>): ProxyHints {
   val `destination$iv$iv`: java.util.Collection = ArrayList(proxiedInterfaces.length)

   for (`item$iv$iv` in proxiedInterfaces) {
      `destination$iv$iv`.add(JvmClassMappingKt.getJavaClass((KClass)`item$iv$iv`))
   }

   val var2: Array<Class> = (`destination$iv$iv` as java.util.List).toArray(arrayOfNulls(0))
   return `$this$registerJdkProxy`.registerJdkProxy(Arrays.copyOf(var2, var2.length))
}
