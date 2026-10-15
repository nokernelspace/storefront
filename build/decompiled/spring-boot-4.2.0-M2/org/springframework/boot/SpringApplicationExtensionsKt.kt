@file:SourceDebugExtension(["SMAP\nSpringApplicationExtensions.kt\nKotlin\n*S Kotlin\n*F\n+ 1 SpringApplicationExtensions.kt\norg/springframework/boot/SpringApplicationExtensionsKt\n+ 2 _Arrays.kt\nkotlin/collections/ArraysKt___ArraysKt\n+ 3 ArraysJVM.kt\nkotlin/collections/ArraysKt__ArraysJVMKt\n*L\n1#1,81:1\n12850#2:82\n13197#2,3:83\n37#3,2:86\n*S KotlinDebug\n*F\n+ 1 SpringApplicationExtensions.kt\norg/springframework/boot/SpringApplicationExtensionsKt\n*L\n78#1:82\n78#1:83,3\n78#1:86,2\n*E\n"])

package org.springframework.boot

import java.util.ArrayList
import java.util.Arrays
import kotlin.jvm.internal.SourceDebugExtension
import kotlin.reflect.KClass
import org.springframework.boot.SpringApplication.Augmented

public fun Augmented.with(vararg types: KClass<*>): Augmented {
   val `destination$iv$iv`: java.util.Collection = ArrayList(types.length)

   for (`item$iv$iv` in types) {
      `destination$iv$iv`.add(JvmClassMappingKt.getJavaClass((KClass)`item$iv$iv`))
   }

   val var2: Array<Class> = (`destination$iv$iv` as java.util.List).toArray(arrayOfNulls(0))
   val var10000: SpringApplication.Augmented = `$this$with`.with(Arrays.copyOf(var2, var2.length))
   return var10000
}
