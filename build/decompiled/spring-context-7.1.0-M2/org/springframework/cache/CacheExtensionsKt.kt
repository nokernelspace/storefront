package org.springframework.cache

import org.springframework.cache.Cache.ValueWrapper

public operator fun Cache.get(key: Any): ValueWrapper? {
   return `$this$get`.get(key)
}

public operator fun Cache.set(key: Any, value: Any?) {
   `$this$set`.put(key, value)
}
