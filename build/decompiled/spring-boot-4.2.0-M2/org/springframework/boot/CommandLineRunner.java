package org.springframework.boot;

@FunctionalInterface
public interface CommandLineRunner extends Runner {
   void run(String... args) throws Exception;
}
