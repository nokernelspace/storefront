package org.springframework.beans.factory

import kotlin.jvm.internal.SourceDebugExtension
import kotlin.reflect.KClass
import org.springframework.beans.factory.BeanRegistry.SupplierContext
import org.springframework.core.env.Environment

@BeanRegistrarDslMarker
public open class BeanRegistrarDsl(init: (BeanRegistrarDsl) -> Unit) : BeanRegistrar {
   private final val init: (BeanRegistrarDsl) -> Unit

   @PublishedApi
   internal final lateinit var registry: BeanRegistry

   public final lateinit var env: Environment
      internal set

   init {
      this.init = init
   }

   public fun profile(expression: String, init: (BeanRegistrarDsl) -> Unit) {
      if (this.env.matchesProfiles(arrayOf(expression))) {
         init(this)
      }
   }

   public fun register(registrar: BeanRegistrar) {
      this.registry.register(registrar)
   }

   public fun registerAlias(name: String, alias: String) {
      this.registry.registerAlias(name, alias)
   }

   public fun containsBean(name: String): Boolean {
      return this.registry.containsBean(name)
   }

   public fun containsBean(beanType: KClass<*>): Boolean {
      return this.registry.containsBean(JvmClassMappingKt.getJavaClass(beanType))
   }

   public override fun register(registry: BeanRegistry, env: Environment) {
      this.registry = registry
      this.env = env
      this.init(this)
   }

   @BeanRegistrarDslMarker
   @SourceDebugExtension(["SMAP\nBeanRegistrarDsl.kt\nKotlin\n*S Kotlin\n*F\n+ 1 BeanRegistrarDsl.kt\norg/springframework/beans/factory/BeanRegistrarDsl$SupplierContextDsl\n*L\n1#1,428:1\n415#1:429\n*S KotlinDebug\n*F\n+ 1 BeanRegistrarDsl.kt\norg/springframework/beans/factory/BeanRegistrarDsl$SupplierContextDsl\n*L\n404#1:429\n*E\n"])
   public open class SupplierContextDsl<T>(context: SupplierContext, env: Environment) {
      @PublishedApi
      internal final val context: SupplierContext

      public final val env: Environment

      init {
         this.context = context
         this.env = env
      }
   }
}
