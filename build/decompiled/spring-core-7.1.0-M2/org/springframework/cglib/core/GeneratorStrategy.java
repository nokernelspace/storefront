package org.springframework.cglib.core;

public interface GeneratorStrategy {
   byte[] generate(ClassGenerator cg) throws Exception;

   @Override
   boolean equals(Object o);
}
