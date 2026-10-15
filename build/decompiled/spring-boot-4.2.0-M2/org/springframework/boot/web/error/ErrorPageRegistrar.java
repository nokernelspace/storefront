package org.springframework.boot.web.error;

@FunctionalInterface
public interface ErrorPageRegistrar {
   void registerErrorPages(ErrorPageRegistry registry);
}
