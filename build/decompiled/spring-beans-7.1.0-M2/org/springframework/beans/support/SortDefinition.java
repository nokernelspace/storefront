package org.springframework.beans.support;

@Deprecated(since = "7.0.3", forRemoval = true)
public interface SortDefinition {
   String getProperty();

   boolean isIgnoreCase();

   boolean isAscending();
}
