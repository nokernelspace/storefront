package org.springframework.aop;

@FunctionalInterface
public interface ClassFilter {
   ClassFilter TRUE = TrueClassFilter.INSTANCE;

   boolean matches(Class<?> clazz);
}
