package org.springframework.web.method.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;

public class HandlerMethodArgumentResolverComposite implements HandlerMethodArgumentResolver {
   private final List<HandlerMethodArgumentResolver> argumentResolvers = new ArrayList<>();
   private final Map<MethodParameter, HandlerMethodArgumentResolver> argumentResolverCache = new ConcurrentHashMap<>(256);

   public HandlerMethodArgumentResolverComposite addResolver(HandlerMethodArgumentResolver resolver) {
      this.argumentResolvers.add(resolver);
      return this;
   }

   public HandlerMethodArgumentResolverComposite addResolvers(@Nullable HandlerMethodArgumentResolver... resolvers) {
      if (resolvers != null) {
         Collections.addAll(this.argumentResolvers, resolvers);
      }

      return this;
   }

   public HandlerMethodArgumentResolverComposite addResolvers(@Nullable List<? extends HandlerMethodArgumentResolver> resolvers) {
      if (resolvers != null) {
         this.argumentResolvers.addAll(resolvers);
      }

      return this;
   }

   public List<HandlerMethodArgumentResolver> getResolvers() {
      return Collections.unmodifiableList(this.argumentResolvers);
   }

   public void clear() {
      this.argumentResolvers.clear();
      this.argumentResolverCache.clear();
   }

   @Override
   public boolean supportsParameter(MethodParameter parameter) {
      return this.getArgumentResolver(parameter) != null;
   }

   @Override
   public @Nullable Object resolveArgument(
      MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer, NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory
   ) throws Exception {
      HandlerMethodArgumentResolver resolver = this.getArgumentResolver(parameter);
      if (resolver == null) {
         throw new IllegalArgumentException(
            "Unsupported parameter type [" + parameter.getParameterType().getName() + "]. supportsParameter should be called first."
         );
      } else {
         return resolver.resolveArgument(parameter, mavContainer, webRequest, binderFactory);
      }
   }

   public @Nullable HandlerMethodArgumentResolver getArgumentResolver(MethodParameter parameter) {
      HandlerMethodArgumentResolver result = this.argumentResolverCache.get(parameter);
      if (result == null) {
         for (HandlerMethodArgumentResolver resolver : this.argumentResolvers) {
            if (resolver.supportsParameter(parameter)) {
               result = resolver;
               this.argumentResolverCache.put(parameter, result);
               break;
            }
         }
      }

      return result;
   }
}
