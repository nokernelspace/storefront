package org.springframework.web.method.annotation;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.annotation.ValidationAnnotationUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.bind.support.WebRequestDataBinder;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.ModelAndViewContainer;

public class ModelAttributeMethodProcessor implements HandlerMethodArgumentResolver, HandlerMethodReturnValueHandler {
   protected final Log logger = LogFactory.getLog(this.getClass());
   private final boolean annotationNotRequired;

   public ModelAttributeMethodProcessor(boolean annotationNotRequired) {
      this.annotationNotRequired = annotationNotRequired;
   }

   @Override
   public boolean supportsParameter(MethodParameter parameter) {
      return parameter.hasParameterAnnotation(ModelAttribute.class) || this.annotationNotRequired && !BeanUtils.isSimpleProperty(parameter.getParameterType());
   }

   @Override
   public final @Nullable Object resolveArgument(
      MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer, NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory
   ) throws Exception {
      Assert.state(mavContainer != null, "ModelAttributeMethodProcessor requires ModelAndViewContainer");
      Assert.state(binderFactory != null, "ModelAttributeMethodProcessor requires WebDataBinderFactory");
      String name = ModelFactory.getNameForParameter(parameter);
      ModelAttribute ann = (ModelAttribute)parameter.getParameterAnnotation(ModelAttribute.class);
      if (ann != null) {
         mavContainer.setBinding(name, ann.binding());
      }

      BindingResult bindingResult = null;
      Object attribute;
      if (mavContainer.containsAttribute(name)) {
         attribute = mavContainer.getModel().get(name);
         if (attribute == null || ObjectUtils.unwrapOptional(attribute) == null) {
            bindingResult = binderFactory.createBinder(webRequest, null, name).getBindingResult();
            attribute = wrapAsOptionalIfNecessary(parameter, null);
         }
      } else {
         try {
            attribute = this.createAttribute(name, parameter, binderFactory, webRequest);
         } catch (MethodArgumentNotValidException ex) {
            if (this.isBindExceptionRequired(parameter)) {
               throw ex;
            }

            attribute = wrapAsOptionalIfNecessary(parameter, ex.getTarget());
            bindingResult = ex.getBindingResult();
         }
      }

      if (bindingResult == null) {
         ResolvableType type = ResolvableType.forMethodParameter(parameter);
         WebDataBinder binder = binderFactory.createBinder(webRequest, attribute, name, type);
         if (attribute == null) {
            this.constructAttribute(binder, webRequest);
            attribute = wrapAsOptionalIfNecessary(parameter, binder.getTarget());
         }

         if (!binder.getBindingResult().hasErrors()) {
            if (!mavContainer.isBindingDisabled(name)) {
               this.bindRequestParameters(binder, webRequest);
            }

            this.validateIfApplicable(binder, parameter);
         }

         if (binder.getBindingResult().hasErrors() && this.isBindExceptionRequired(binder, parameter)) {
            throw new MethodArgumentNotValidException(parameter, binder.getBindingResult());
         }

         if (!parameter.getParameterType().isInstance(attribute)) {
            attribute = binder.convertIfNecessary(binder.getTarget(), parameter.getParameterType(), parameter);
         }

         bindingResult = binder.getBindingResult();
      }

      Map<String, Object> bindingResultModel = bindingResult.getModel();
      mavContainer.removeAttributes(bindingResultModel);
      mavContainer.addAllAttributes(bindingResultModel);
      return attribute;
   }

   private static @Nullable Object wrapAsOptionalIfNecessary(MethodParameter parameter, @Nullable Object target) {
      return parameter.getParameterType() == Optional.class ? Optional.ofNullable(target) : target;
   }

   protected @Nullable Object createAttribute(String attributeName, MethodParameter parameter, WebDataBinderFactory binderFactory, NativeWebRequest request) throws Exception {
      return null;
   }

   protected void constructAttribute(WebDataBinder binder, NativeWebRequest request) {
      ((WebRequestDataBinder)binder).construct(request);
   }

   protected void bindRequestParameters(WebDataBinder binder, NativeWebRequest request) {
      ((WebRequestDataBinder)binder).bind(request);
   }

   protected void validateIfApplicable(WebDataBinder binder, MethodParameter parameter) {
      for (Annotation ann : parameter.getParameterAnnotations()) {
         Object[] validationHints = ValidationAnnotationUtils.determineValidationHints(ann);
         if (validationHints != null) {
            binder.validate(validationHints);
            break;
         }
      }
   }

   protected boolean isBindExceptionRequired(WebDataBinder binder, MethodParameter parameter) {
      return this.isBindExceptionRequired(parameter);
   }

   protected boolean isBindExceptionRequired(MethodParameter parameter) {
      int i = parameter.getParameterIndex();
      Class<?>[] paramTypes = parameter.getExecutable().getParameterTypes();
      boolean hasBindingResult = paramTypes.length > i + 1 && Errors.class.isAssignableFrom(paramTypes[i + 1]);
      return !hasBindingResult;
   }

   @Override
   public boolean supportsReturnType(MethodParameter returnType) {
      return returnType.hasMethodAnnotation(ModelAttribute.class) || this.annotationNotRequired && !BeanUtils.isSimpleProperty(returnType.getParameterType());
   }

   @Override
   public void handleReturnValue(@Nullable Object returnValue, MethodParameter returnType, ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {
      if (returnValue != null) {
         String name = ModelFactory.getNameForReturnValue(returnValue, returnType);
         mavContainer.addAttribute(name, returnValue);
      }
   }
}
