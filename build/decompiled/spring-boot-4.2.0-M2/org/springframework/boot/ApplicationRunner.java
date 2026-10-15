package org.springframework.boot;

@FunctionalInterface
public interface ApplicationRunner extends Runner {
   void run(ApplicationArguments args) throws Exception;
}
