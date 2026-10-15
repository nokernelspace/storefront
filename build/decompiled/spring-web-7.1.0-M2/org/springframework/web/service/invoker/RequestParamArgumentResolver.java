package org.springframework.web.service.invoker;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;

public class RequestParamArgumentResolver extends AbstractNamedValueArgumentResolver {
   private boolean favorSingleValue;

   public RequestParamArgumentResolver(ConversionService conversionService) {
      super(conversionService);
   }

   public void setFavorSingleValue(boolean favorSingleValue) {
      this.favorSingleValue = favorSingleValue;
   }

   public boolean isFavorSingleValue() {
      return this.favorSingleValue;
   }

   @Override
   protected AbstractNamedValueArgumentResolver.@Nullable NamedValueInfo createNamedValueInfo(MethodParameter parameter, HttpRequestValues.Metadata metadata) {
      RequestParam annot = (RequestParam)parameter.getParameterAnnotation(RequestParam.class);
      return annot == null
         ? null
         : new AbstractNamedValueArgumentResolver.NamedValueInfo(
            annot.name(), annot.required(), annot.defaultValue(), "request parameter", this.supportsMultipleValues(parameter, metadata)
         );
   }

   @Override
   protected AbstractNamedValueArgumentResolver.NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
      throw new UnsupportedOperationException();
   }

   protected boolean supportsMultipleValues(MethodParameter parameter, HttpRequestValues.Metadata metadata) {
      return !this.isFavorSingleValue() || this.isFormOrMultipartContent(metadata);
   }

   protected boolean isFormOrMultipartContent(HttpRequestValues.Metadata metadata) {
      MediaType mediaType = metadata.getContentType();
      return mediaType != null && (mediaType.getType().equals("multipart") || mediaType.isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED));
   }

   @Override
   protected void addRequestValue(String name, Object value, MethodParameter parameter, HttpRequestValues.Builder requestValues) {
      requestValues.addRequestParameter(name, (String)value);
   }
}
