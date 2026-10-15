package org.springframework.web.accept;

@FunctionalInterface
public interface ApiVersionParser<V extends Comparable<V>> {
   V parseVersion(String version);
}
