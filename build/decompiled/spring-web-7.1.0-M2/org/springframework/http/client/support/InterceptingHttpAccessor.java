package org.springframework.http.client.support;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.InterceptingClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

@Deprecated(since = "7.1", forRemoval = true)
public abstract class InterceptingHttpAccessor extends HttpAccessor {
   private final List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
   private volatile @Nullable ClientHttpRequestFactory interceptingRequestFactory;

   public void setInterceptors(List<ClientHttpRequestInterceptor> interceptors) {
      Assert.noNullElements(interceptors, "'interceptors' must not contain null elements");
      if (this.interceptors != interceptors) {
         this.interceptors.clear();
         this.interceptors.addAll(interceptors);
         AnnotationAwareOrderComparator.sort(this.interceptors);
      }
   }

   public List<ClientHttpRequestInterceptor> getInterceptors() {
      return this.interceptors;
   }

   @Override
   public void setRequestFactory(ClientHttpRequestFactory requestFactory) {
      super.setRequestFactory(requestFactory);
      this.interceptingRequestFactory = null;
   }

   @Override
   public ClientHttpRequestFactory getRequestFactory() {
      List<ClientHttpRequestInterceptor> interceptors = this.getInterceptors();
      if (!CollectionUtils.isEmpty(interceptors)) {
         ClientHttpRequestFactory factory = this.interceptingRequestFactory;
         if (factory == null) {
            factory = new InterceptingClientHttpRequestFactory(super.getRequestFactory(), interceptors, this.getBufferingPredicate());
            this.interceptingRequestFactory = factory;
         }

         return factory;
      } else {
         return super.getRequestFactory();
      }
   }
}
