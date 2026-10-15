package org.springframework.context.support

import java.util.function.Supplier
import kotlin.jvm.functions.Function0
import kotlin.jvm.internal.SourceDebugExtension

// $VF: Class flags could not be determined
@SourceDebugExtension(["SMAP\nBeanDefinitionDsl.kt\nKotlin\n*S Kotlin\n*F\n+ 1 BeanDefinitionDsl.kt\norg/springframework/context/support/BeanDefinitionDsl$bean$1\n+ 2 BeanDefinitionDsl.kt\norg/springframework/context/support/BeanDefinitionDsl\n*L\n1#1,1226:1\n296#2:1227\n*E\n"])
internal class `BeanDefinitionDsl$bean$$inlined$bean$1`<T> : Supplier {
   fun `BeanDefinitionDsl$bean$$inlined$bean$1`(`$receiver`: BeanDefinitionDsl, var2: Function0) {
      this.this$0 = `$receiver`
      this.$f$inlined = var2
   }

   override final fun get(): T {
      BeanDefinitionDsl.BeanSupplierContext(this.this$0.context)
      this.$f$inlined()
   }
}
