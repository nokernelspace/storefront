package org.springframework.context;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.io.support.ResourcePatternResolver;

public interface ApplicationContext
   extends EnvironmentCapable,
   ListableBeanFactory,
   HierarchicalBeanFactory,
   MessageSource,
   ApplicationEventPublisher,
   ResourcePatternResolver {
   String getId();

   String getApplicationName();

   String getDisplayName();

   long getStartupDate();

   @Nullable ApplicationContext getParent();

   AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException;
}
