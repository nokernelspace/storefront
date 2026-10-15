package org.springframework.context.support

@Deprecated(message = "Use regular apply method instead.", replaceWith = @ReplaceWith(expression = "GenericApplicationContext().apply(configure)", imports = []))
public fun GenericApplicationContext(configure: (GenericApplicationContext) -> Unit): GenericApplicationContext {
   val var1: GenericApplicationContext = GenericApplicationContext()
   configure(var1)
   return var1
}
