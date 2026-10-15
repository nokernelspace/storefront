package org.springframework.cache

public operator fun CacheManager.get(name: String): Cache? {
   return `$this$get`.getCache(name)
}
