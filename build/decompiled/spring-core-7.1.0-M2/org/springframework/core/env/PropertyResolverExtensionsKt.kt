package org.springframework.core.env

public operator fun PropertyResolver.get(key: String): String? {
   return `$this$get`.getProperty(key)
}
