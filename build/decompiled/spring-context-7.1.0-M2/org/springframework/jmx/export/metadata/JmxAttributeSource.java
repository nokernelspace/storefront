package org.springframework.jmx.export.metadata;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;

public interface JmxAttributeSource {
   @Nullable ManagedResource getManagedResource(Class<?> clazz) throws InvalidMetadataException;

   @Nullable ManagedAttribute getManagedAttribute(Method method) throws InvalidMetadataException;

   @Nullable ManagedMetric getManagedMetric(Method method) throws InvalidMetadataException;

   @Nullable ManagedOperation getManagedOperation(Method method) throws InvalidMetadataException;

   @Nullable ManagedOperationParameter[] getManagedOperationParameters(Method method) throws InvalidMetadataException;

   @Nullable ManagedNotification[] getManagedNotifications(Class<?> clazz) throws InvalidMetadataException;
}
