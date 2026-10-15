package org.springframework.context.support

@Deprecated(message = "Use BeanRegistrarDsl instead")
public fun beans(init: (BeanDefinitionDsl) -> Unit): BeanDefinitionDsl {
   return BeanDefinitionDsl(init, null, 2, null)
}
