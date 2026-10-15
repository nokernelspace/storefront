package org.springframework.beans.factory.xml;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface NamespaceHandlerResolver {
   @Nullable NamespaceHandler resolve(String namespaceUri);
}
