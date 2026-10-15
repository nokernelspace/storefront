package org.springframework.scripting.groovy;

import groovy.lang.GroovyObject;

@FunctionalInterface
public interface GroovyObjectCustomizer {
   void customize(GroovyObject goo);
}
