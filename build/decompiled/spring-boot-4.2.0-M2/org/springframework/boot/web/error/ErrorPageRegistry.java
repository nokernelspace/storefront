package org.springframework.boot.web.error;

@FunctionalInterface
public interface ErrorPageRegistry {
   void addErrorPages(ErrorPage... errorPages);
}
