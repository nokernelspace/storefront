package org.springframework.context.event;

import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.ResolvableType;

public class SourceFilteringListener implements GenericApplicationListener {
   private final Object source;
   private @Nullable GenericApplicationListener delegate;

   public SourceFilteringListener(Object source, ApplicationListener<?> delegate) {
      this.source = source;
      this.delegate = delegate instanceof GenericApplicationListener gal ? gal : new GenericApplicationListenerAdapter(delegate);
   }

   protected SourceFilteringListener(Object source) {
      this.source = source;
   }

   @Override
   public void onApplicationEvent(ApplicationEvent event) {
      if (event.getSource() == this.source) {
         this.onApplicationEventInternal(event);
      }
   }

   @Override
   public boolean supportsEventType(ResolvableType eventType) {
      return this.delegate == null || this.delegate.supportsEventType(eventType);
   }

   @Override
   public boolean supportsSourceType(@Nullable Class<?> sourceType) {
      return sourceType != null && sourceType.isInstance(this.source);
   }

   @Override
   public int getOrder() {
      return this.delegate != null ? this.delegate.getOrder() : Integer.MAX_VALUE;
   }

   @Override
   public String getListenerId() {
      return this.delegate != null ? this.delegate.getListenerId() : "";
   }

   protected void onApplicationEventInternal(ApplicationEvent event) {
      if (this.delegate == null) {
         throw new IllegalStateException("Must specify a delegate object or override the onApplicationEventInternal method");
      }

      this.delegate.onApplicationEvent(event);
   }
}
