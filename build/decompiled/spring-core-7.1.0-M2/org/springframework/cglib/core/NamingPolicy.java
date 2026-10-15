package org.springframework.cglib.core;

public interface NamingPolicy {
   String getClassName(String prefix, String source, Object key, Predicate names);

   @Override
   boolean equals(Object o);
}
