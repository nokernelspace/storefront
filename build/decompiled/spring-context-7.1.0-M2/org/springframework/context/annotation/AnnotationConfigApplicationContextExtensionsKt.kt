package org.springframework.context.annotation

@Deprecated(message = "Use regular apply method instead.", replaceWith = @ReplaceWith(expression = "AnnotationConfigApplicationContext().apply(configure)", imports = []))
public fun AnnotationConfigApplicationContext(configure: (AnnotationConfigApplicationContext) -> Unit): AnnotationConfigApplicationContext {
   val var1: AnnotationConfigApplicationContext = AnnotationConfigApplicationContext()
   configure(var1)
   return var1
}
