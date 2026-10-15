package org.springframework.web.method.annotation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.UriComponentsContributor;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.support.MultipartResolutionDelegate;
import org.springframework.web.util.UriComponentsBuilder;

public class RequestParamMethodArgumentResolver extends AbstractNamedValueMethodArgumentResolver implements UriComponentsContributor {
   private static final TypeDescriptor STRING_TYPE_DESCRIPTOR = TypeDescriptor.valueOf(String.class);
   private final boolean useDefaultResolution;

   public RequestParamMethodArgumentResolver(boolean useDefaultResolution) {
      this.useDefaultResolution = useDefaultResolution;
   }

   public RequestParamMethodArgumentResolver(@Nullable ConfigurableBeanFactory beanFactory, boolean useDefaultResolution) {
      super(beanFactory);
      this.useDefaultResolution = useDefaultResolution;
   }

   @Override
   public boolean supportsParameter(MethodParameter parameter) {
      if (parameter.hasParameterAnnotation(RequestParam.class)) {
         if (!Map.class.isAssignableFrom(parameter.nestedIfOptional().getNestedParameterType())) {
            return true;
         }

         RequestParam requestParam = (RequestParam)parameter.getParameterAnnotation(RequestParam.class);
         return requestParam != null && StringUtils.hasText(requestParam.name());
      } else if (parameter.hasParameterAnnotation(RequestPart.class)) {
         return false;
      } else {
         parameter = parameter.nestedIfOptional();
         if (MultipartResolutionDelegate.isMultipartArgument(parameter)) {
            return true;
         } else {
            return this.useDefaultResolution ? BeanUtils.isSimpleProperty(parameter.getNestedParameterType()) : false;
         }
      }
   }

   @Override
   protected AbstractNamedValueMethodArgumentResolver.NamedValueInfo createNamedValueInfo(MethodParameter parameter) {
      RequestParam ann = (RequestParam)parameter.getParameterAnnotation(RequestParam.class);
      return ann != null
         ? new RequestParamMethodArgumentResolver.RequestParamNamedValueInfo(ann)
         : new RequestParamMethodArgumentResolver.RequestParamNamedValueInfo();
   }

   @Override
   protected @Nullable Object resolveName(String name, MethodParameter parameter, NativeWebRequest request) throws Exception {
      HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest.class);
      if (servletRequest != null) {
         Object mpArg = MultipartResolutionDelegate.resolveMultipartArgument(name, parameter, servletRequest);
         if (mpArg != MultipartResolutionDelegate.UNRESOLVABLE) {
            return mpArg;
         }
      }

      Object arg = null;
      MultipartRequest multipartRequest = request.getNativeRequest(MultipartRequest.class);
      if (multipartRequest != null) {
         List<MultipartFile> files = multipartRequest.getFiles(name);
         if (!files.isEmpty()) {
            arg = files.size() == 1 ? files.get(0) : files;
         }
      }

      if (arg == null) {
         String[] paramValues = request.getParameterValues(name);
         if (paramValues == null) {
            paramValues = request.getParameterValues(name + "[]");
         }

         if (paramValues != null) {
            arg = paramValues.length == 1 ? paramValues[0] : paramValues;
         }
      }

      return arg;
   }

   @Override
   protected void handleMissingValue(String name, MethodParameter parameter, NativeWebRequest request) throws Exception {
      this.handleMissingValueInternal(name, parameter, request, false);
   }

   @Override
   protected void handleMissingValueAfterConversion(String name, MethodParameter parameter, NativeWebRequest request) throws Exception {
      this.handleMissingValueInternal(name, parameter, request, true);
   }

   protected void handleMissingValueInternal(String name, MethodParameter parameter, NativeWebRequest request, boolean missingAfterConversion) throws Exception {
      HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest.class);
      if (MultipartResolutionDelegate.isMultipartArgument(parameter)) {
         if (servletRequest != null && MultipartResolutionDelegate.isMultipartRequest(servletRequest)) {
            throw new MissingServletRequestPartException(name);
         } else {
            throw new MultipartException("Current request is not a multipart request");
         }
      } else {
         throw new MissingServletRequestParameterException(name, parameter, missingAfterConversion);
      }
   }

   @Override
   public void contributeMethodArgument(
      MethodParameter parameter, @Nullable Object value, UriComponentsBuilder builder, Map<String, Object> uriVariables, ConversionService conversionService
   ) {
      Class<?> paramType = parameter.getNestedParameterType();
      if (!Map.class.isAssignableFrom(paramType) && MultipartFile.class != paramType && Part.class != paramType) {
         RequestParam requestParam = (RequestParam)parameter.getParameterAnnotation(RequestParam.class);
         String name = requestParam != null && StringUtils.hasLength(requestParam.name()) ? requestParam.name() : parameter.getParameterName();
         Assert.state(name != null, "Unresolvable parameter name");
         parameter = parameter.nestedIfOptional();
         if (value instanceof Optional<?> optional) {
            value = optional.orElse(null);
         }

         if (value == null) {
            if (requestParam != null && (!requestParam.required() || !requestParam.defaultValue().equals("\n\t\t\n\t\t\n\ue000\ue001\ue002\n\t\t\t\t\n"))) {
               return;
            }

            builder.queryParam(name);
         } else if (value instanceof Collection) {
            for (Object element : (Collection)value) {
               element = this.formatUriValue(conversionService, TypeDescriptor.nested(parameter, 1), element);
               builder.queryParam(name, element);
            }
         } else {
            builder.queryParam(name, this.formatUriValue(conversionService, new TypeDescriptor(parameter), value));
         }
      }
   }

   protected @Nullable String formatUriValue(@Nullable ConversionService cs, @Nullable TypeDescriptor sourceType, @Nullable Object value) {
      if (value == null) {
         return null;
      } else if (value instanceof String string) {
         return string;
      } else {
         return cs != null ? (String)cs.convert(value, sourceType, STRING_TYPE_DESCRIPTOR) : value.toString();
      }
   }

   private static class RequestParamNamedValueInfo extends AbstractNamedValueMethodArgumentResolver.NamedValueInfo {
      public RequestParamNamedValueInfo() {
         super("", false, "\n\t\t\n\t\t\n\ue000\ue001\ue002\n\t\t\t\t\n");
      }

      public RequestParamNamedValueInfo(RequestParam annotation) {
         super(annotation.name(), annotation.required(), annotation.defaultValue());
      }
   }
}
