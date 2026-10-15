package org.springframework.web.method.annotation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ExceptionDepthComparator;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentLruCache;
import org.springframework.util.ReflectionUtils.MethodFilter;
import org.springframework.web.bind.annotation.ExceptionHandler;

public class ExceptionHandlerMethodResolver {
   private static final MethodFilter EXCEPTION_HANDLER_METHODS = method -> AnnotatedElementUtils.hasAnnotation(method, ExceptionHandler.class);
   private static final ExceptionHandlerMappingInfo NO_MATCHING_EXCEPTION_HANDLER;
   private final Map<ExceptionHandlerMethodResolver.ExceptionMapping, ExceptionHandlerMappingInfo> mappedMethods = new HashMap<>(16);
   private final ConcurrentLruCache<ExceptionHandlerMethodResolver.ExceptionMapping, ExceptionHandlerMappingInfo> lookupCache = new ConcurrentLruCache(
      24, cacheKey -> this.getMappedMethod(cacheKey.exceptionType(), cacheKey.mediaType())
   );

   public ExceptionHandlerMethodResolver(Class<?> handlerType) {
      for (Method method : MethodIntrospector.selectMethods(handlerType, EXCEPTION_HANDLER_METHODS)) {
         ExceptionHandlerMappingInfo mappingInfo = this.detectExceptionMappings(method);

         for (Class<? extends Throwable> exceptionType : mappingInfo.getExceptionTypes()) {
            for (MediaType producibleType : mappingInfo.getProducibleTypes()) {
               this.addExceptionMapping(new ExceptionHandlerMethodResolver.ExceptionMapping(exceptionType, producibleType), mappingInfo);
            }

            if (mappingInfo.getProducibleTypes().isEmpty()) {
               this.addExceptionMapping(new ExceptionHandlerMethodResolver.ExceptionMapping(exceptionType, MediaType.ALL), mappingInfo);
            }
         }
      }
   }

   private ExceptionHandlerMappingInfo detectExceptionMappings(Method method) {
      ExceptionHandler exceptionHandler = this.readExceptionHandlerAnnotation(method);
      List<Class<? extends Throwable>> exceptions = new ArrayList<>(Arrays.asList(exceptionHandler.exception()));
      if (exceptions.isEmpty()) {
         for (Class<?> paramType : method.getParameterTypes()) {
            if (Throwable.class.isAssignableFrom(paramType)) {
               exceptions.add((Class<? extends Throwable>)paramType);
            }
         }
      }

      if (exceptions.isEmpty()) {
         throw new IllegalStateException("No exception types mapped to " + method);
      }

      Set<MediaType> mediaTypes = new LinkedHashSet<>();

      for (String mediaType : exceptionHandler.produces()) {
         try {
            mediaTypes.add(MediaType.parseMediaType(mediaType));
         } catch (InvalidMediaTypeException exc) {
            throw new IllegalStateException("Invalid media type [" + mediaType + "] declared on @ExceptionHandler for " + method, exc);
         }
      }

      return new ExceptionHandlerMappingInfo(Set.copyOf(exceptions), mediaTypes, method);
   }

   private ExceptionHandler readExceptionHandlerAnnotation(Method method) {
      ExceptionHandler ann = (ExceptionHandler)AnnotatedElementUtils.findMergedAnnotation(method, ExceptionHandler.class);
      Assert.state(ann != null, "No ExceptionHandler annotation");
      return ann;
   }

   private void addExceptionMapping(ExceptionHandlerMethodResolver.ExceptionMapping mapping, ExceptionHandlerMappingInfo mappingInfo) {
      ExceptionHandlerMappingInfo oldMapping = this.mappedMethods.put(mapping, mappingInfo);
      if (oldMapping != null && !oldMapping.getHandlerMethod().equals(mappingInfo.getHandlerMethod())) {
         throw new IllegalStateException(
            "Ambiguous @ExceptionHandler method mapped for [" + mapping + "]: {" + oldMapping.getHandlerMethod() + ", " + mappingInfo.getHandlerMethod() + "}"
         );
      }
   }

   public boolean hasExceptionMappings() {
      return !this.mappedMethods.isEmpty();
   }

   public @Nullable Method resolveMethod(Exception exception) {
      ExceptionHandlerMappingInfo mappingInfo = this.resolveExceptionMapping(exception, MediaType.ALL);
      return mappingInfo != null ? mappingInfo.getHandlerMethod() : null;
   }

   public @Nullable Method resolveMethodByThrowable(Throwable exception) {
      ExceptionHandlerMappingInfo mappingInfo = this.resolveExceptionMapping(exception, MediaType.ALL);
      return mappingInfo != null ? mappingInfo.getHandlerMethod() : null;
   }

   public @Nullable ExceptionHandlerMappingInfo resolveExceptionMapping(Throwable exception, MediaType mediaType) {
      ExceptionHandlerMappingInfo mappingInfo = this.resolveExceptionMappingByExceptionType((Class<? extends Throwable>)exception.getClass(), mediaType);
      if (mappingInfo == null) {
         Throwable cause = exception.getCause();
         if (cause != null) {
            mappingInfo = this.resolveExceptionMapping(cause, mediaType);
         }
      }

      return mappingInfo;
   }

   public @Nullable Method resolveMethodByExceptionType(Class<? extends Throwable> exceptionType) {
      ExceptionHandlerMappingInfo mappingInfo = this.resolveExceptionMappingByExceptionType(exceptionType, MediaType.ALL);
      return mappingInfo != null ? mappingInfo.getHandlerMethod() : null;
   }

   public @Nullable ExceptionHandlerMappingInfo resolveExceptionMappingByExceptionType(Class<? extends Throwable> exceptionType, MediaType mediaType) {
      ExceptionHandlerMappingInfo mappingInfo = (ExceptionHandlerMappingInfo)this.lookupCache
         .get(new ExceptionHandlerMethodResolver.ExceptionMapping(exceptionType, mediaType));
      return mappingInfo != NO_MATCHING_EXCEPTION_HANDLER ? mappingInfo : null;
   }

   private ExceptionHandlerMappingInfo getMappedMethod(Class<? extends Throwable> exceptionType, MediaType mediaType) {
      List<ExceptionHandlerMethodResolver.ExceptionMapping> matches = new ArrayList<>();

      for (ExceptionHandlerMethodResolver.ExceptionMapping mappingInfo : this.mappedMethods.keySet()) {
         if (mappingInfo.exceptionType().isAssignableFrom(exceptionType) && mappingInfo.mediaType().isCompatibleWith(mediaType)) {
            matches.add(mappingInfo);
         }
      }

      if (!matches.isEmpty()) {
         if (matches.size() > 1) {
            matches.sort(new ExceptionHandlerMethodResolver.ExceptionMappingComparator(exceptionType, mediaType));
         }

         return Objects.requireNonNull(this.mappedMethods.get(matches.get(0)));
      } else {
         return NO_MATCHING_EXCEPTION_HANDLER;
      }
   }

   private void noMatchingExceptionHandler() {
   }

   static {
      try {
         NO_MATCHING_EXCEPTION_HANDLER = new ExceptionHandlerMappingInfo(
            Set.of(), Set.of(), ExceptionHandlerMethodResolver.class.getDeclaredMethod("noMatchingExceptionHandler")
         );
      } catch (NoSuchMethodException ex) {
         throw new IllegalStateException("Expected method not found: " + ex);
      }
   }

   private record ExceptionMapping(Class<? extends Throwable> exceptionType, MediaType mediaType) {
      @Override
      public String toString() {
         return "ExceptionHandler{exceptionType=" + this.exceptionType.getCanonicalName() + ", mediaType=" + this.mediaType + "}";
      }
   }

   private static class ExceptionMappingComparator implements Comparator<ExceptionHandlerMethodResolver.ExceptionMapping> {
      private final ExceptionDepthComparator exceptionDepthComparator;
      private final MediaType mediaType;

      public ExceptionMappingComparator(Class<? extends Throwable> exceptionType, MediaType mediaType) {
         this.exceptionDepthComparator = new ExceptionDepthComparator(exceptionType);
         this.mediaType = mediaType;
      }

      public int compare(ExceptionHandlerMethodResolver.ExceptionMapping o1, ExceptionHandlerMethodResolver.ExceptionMapping o2) {
         int result = this.exceptionDepthComparator.compare(o1.exceptionType(), o2.exceptionType());
         if (result != 0) {
            return result;
         } else if (o1.mediaType.equals(this.mediaType)) {
            return -1;
         } else if (o2.mediaType.equals(this.mediaType)) {
            return 1;
         } else if (o1.mediaType.equals(o2.mediaType)) {
            return 0;
         } else {
            return o1.mediaType.isMoreSpecific(o2.mediaType) ? -1 : 1;
         }
      }
   }
}
