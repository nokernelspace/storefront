package org.springframework.web.method.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.web.util.UriComponentsBuilder;

public class CompositeUriComponentsContributor implements UriComponentsContributor {
   private final List<Object> contributors;
   private final ConversionService conversionService;

   public CompositeUriComponentsContributor(UriComponentsContributor... contributors) {
      this.contributors = Arrays.asList(contributors);
      this.conversionService = new DefaultFormattingConversionService();
   }

   public CompositeUriComponentsContributor(Collection<?> contributors) {
      this(contributors, null);
   }

   public CompositeUriComponentsContributor(@Nullable Collection<?> contributors, @Nullable ConversionService cs) {
      this.contributors = contributors != null ? new ArrayList<>((Collection<? extends Object>)contributors) : Collections.emptyList();
      this.conversionService = (ConversionService)(cs != null ? cs : new DefaultFormattingConversionService());
   }

   public boolean hasContributors() {
      return !this.contributors.isEmpty();
   }

   @Override
   public boolean supportsParameter(MethodParameter parameter) {
      for (Object contributor : this.contributors) {
         if (contributor instanceof UriComponentsContributor ucc) {
            if (ucc.supportsParameter(parameter)) {
               return true;
            }
         } else if (contributor instanceof HandlerMethodArgumentResolver resolver && resolver.supportsParameter(parameter)) {
            return false;
         }
      }

      return false;
   }

   @Override
   public void contributeMethodArgument(
      MethodParameter parameter, Object value, UriComponentsBuilder builder, Map<String, Object> uriVariables, ConversionService conversionService
   ) {
      for (Object contributor : this.contributors) {
         if (contributor instanceof UriComponentsContributor ucc) {
            if (ucc.supportsParameter(parameter)) {
               ucc.contributeMethodArgument(parameter, value, builder, uriVariables, conversionService);
               break;
            }
         } else if (contributor instanceof HandlerMethodArgumentResolver resolver && resolver.supportsParameter(parameter)) {
            break;
         }
      }
   }

   public void contributeMethodArgument(MethodParameter parameter, Object value, UriComponentsBuilder builder, Map<String, Object> uriVariables) {
      this.contributeMethodArgument(parameter, value, builder, uriVariables, this.conversionService);
   }
}
