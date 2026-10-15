package org.springframework.web.client;

@FunctionalInterface
public interface ApiVersionFormatter {
   String formatVersion(Object version);
}
