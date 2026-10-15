package org.springframework.web.context.support;

import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource.StubPropertySource;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.RequestScope;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.SessionScope;
import org.springframework.web.context.request.WebRequest;

public abstract class WebApplicationContextUtils {
   private static final boolean JSF_PRESENT = ClassUtils.isPresent("jakarta.faces.context.FacesContext", RequestContextHolder.class.getClassLoader());

   public static WebApplicationContext getRequiredWebApplicationContext(ServletContext sc) throws IllegalStateException {
      WebApplicationContext wac = getWebApplicationContext(sc);
      if (wac == null) {
         throw new IllegalStateException("No WebApplicationContext found: no ContextLoaderListener registered?");
      } else {
         return wac;
      }
   }

   public static @Nullable WebApplicationContext getWebApplicationContext(ServletContext sc) {
      return getWebApplicationContext(sc, WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
   }

   public static @Nullable WebApplicationContext getWebApplicationContext(ServletContext sc, String attrName) {
      Assert.notNull(sc, "ServletContext must not be null");
      Object attr = sc.getAttribute(attrName);
      if (attr == null) {
         return null;
      } else if (attr instanceof RuntimeException runtimeException) {
         throw runtimeException;
      } else if (attr instanceof Error error) {
         throw error;
      } else if (attr instanceof Exception exception) {
         throw new IllegalStateException(exception);
      } else if (attr instanceof WebApplicationContext wac) {
         return wac;
      } else {
         throw new IllegalStateException("Context attribute is not of type WebApplicationContext: " + attr);
      }
   }

   public static @Nullable WebApplicationContext findWebApplicationContext(ServletContext sc) {
      WebApplicationContext wac = getWebApplicationContext(sc);
      if (wac == null) {
         Enumeration<String> attrNames = sc.getAttributeNames();

         while (attrNames.hasMoreElements()) {
            String attrName = attrNames.nextElement();
            if (sc.getAttribute(attrName) instanceof WebApplicationContext currentWac) {
               if (wac != null) {
                  throw new IllegalStateException("No unique WebApplicationContext found: more than one DispatcherServlet registered with publishContext=true?");
               }

               wac = currentWac;
            }
         }
      }

      return wac;
   }

   public static void registerWebApplicationScopes(ConfigurableListableBeanFactory beanFactory) {
      registerWebApplicationScopes(beanFactory, null);
   }

   public static void registerWebApplicationScopes(ConfigurableListableBeanFactory beanFactory, @Nullable ServletContext sc) {
      beanFactory.registerScope("request", new RequestScope());
      beanFactory.registerScope("session", new SessionScope());
      if (sc != null) {
         ServletContextScope appScope = new ServletContextScope(sc);
         beanFactory.registerScope("application", appScope);
         sc.setAttribute(ServletContextScope.class.getName(), appScope);
      }

      beanFactory.registerResolvableDependency(ServletRequest.class, new WebApplicationContextUtils.RequestObjectFactory());
      beanFactory.registerResolvableDependency(ServletResponse.class, new WebApplicationContextUtils.ResponseObjectFactory());
      beanFactory.registerResolvableDependency(HttpSession.class, new WebApplicationContextUtils.SessionObjectFactory());
      beanFactory.registerResolvableDependency(WebRequest.class, new WebApplicationContextUtils.WebRequestObjectFactory());
      if (JSF_PRESENT) {
         WebApplicationContextUtils.FacesDependencyRegistrar.registerFacesDependencies(beanFactory);
      }
   }

   public static void registerEnvironmentBeans(ConfigurableListableBeanFactory bf, @Nullable ServletContext sc) {
      registerEnvironmentBeans(bf, sc, null);
   }

   public static void registerEnvironmentBeans(
      ConfigurableListableBeanFactory bf, @Nullable ServletContext servletContext, @Nullable ServletConfig servletConfig
   ) {
      if (servletContext != null && !bf.containsBean("servletContext")) {
         bf.registerSingleton("servletContext", servletContext);
      }

      if (servletConfig != null && !bf.containsBean("servletConfig")) {
         bf.registerSingleton("servletConfig", servletConfig);
      }

      if (!bf.containsBean("contextParameters")) {
         Map<String, String> parameterMap = new HashMap<>();
         if (servletContext != null) {
            Enumeration<?> paramNameEnum = servletContext.getInitParameterNames();

            while (paramNameEnum.hasMoreElements()) {
               String paramName = (String)paramNameEnum.nextElement();
               parameterMap.put(paramName, servletContext.getInitParameter(paramName));
            }
         }

         if (servletConfig != null) {
            Enumeration<?> paramNameEnum = servletConfig.getInitParameterNames();

            while (paramNameEnum.hasMoreElements()) {
               String paramName = (String)paramNameEnum.nextElement();
               parameterMap.put(paramName, servletConfig.getInitParameter(paramName));
            }
         }

         bf.registerSingleton("contextParameters", Collections.unmodifiableMap(parameterMap));
      }

      if (!bf.containsBean("contextAttributes")) {
         Map<String, Object> attributeMap = new HashMap<>();
         if (servletContext != null) {
            Enumeration<?> attrNameEnum = servletContext.getAttributeNames();

            while (attrNameEnum.hasMoreElements()) {
               String attrName = (String)attrNameEnum.nextElement();
               attributeMap.put(attrName, servletContext.getAttribute(attrName));
            }
         }

         bf.registerSingleton("contextAttributes", Collections.unmodifiableMap(attributeMap));
      }
   }

   public static void initServletPropertySources(MutablePropertySources propertySources, ServletContext servletContext) {
      initServletPropertySources(propertySources, servletContext, null);
   }

   public static void initServletPropertySources(MutablePropertySources sources, @Nullable ServletContext servletContext, @Nullable ServletConfig servletConfig) {
      Assert.notNull(sources, "'propertySources' must not be null");
      String name = "servletContextInitParams";
      if (servletContext != null && sources.get(name) instanceof StubPropertySource) {
         sources.replace(name, new ServletContextPropertySource(name, servletContext));
      }

      name = "servletConfigInitParams";
      if (servletConfig != null && sources.get(name) instanceof StubPropertySource) {
         sources.replace(name, new ServletConfigPropertySource(name, servletConfig));
      }
   }

   private static ServletRequestAttributes currentRequestAttributes() {
      if (RequestContextHolder.currentRequestAttributes() instanceof ServletRequestAttributes servletRequestAttributes) {
         return servletRequestAttributes;
      } else {
         throw new IllegalStateException("Current request is not a servlet request");
      }
   }

   private static class FacesDependencyRegistrar {
      public static void registerFacesDependencies(ConfigurableListableBeanFactory beanFactory) {
         beanFactory.registerResolvableDependency(FacesContext.class, new ObjectFactory<FacesContext>() {
            public FacesContext getObject() {
               return FacesContext.getCurrentInstance();
            }

            @Override
            public String toString() {
               return "Current JSF FacesContext";
            }
         });
         beanFactory.registerResolvableDependency(ExternalContext.class, new ObjectFactory<ExternalContext>() {
            public ExternalContext getObject() {
               return FacesContext.getCurrentInstance().getExternalContext();
            }

            @Override
            public String toString() {
               return "Current JSF ExternalContext";
            }
         });
      }
   }

   private static class RequestObjectFactory implements ObjectFactory<ServletRequest>, Serializable {
      public ServletRequest getObject() {
         return WebApplicationContextUtils.currentRequestAttributes().getRequest();
      }

      @Override
      public String toString() {
         return "Current HttpServletRequest";
      }
   }

   private static class ResponseObjectFactory implements ObjectFactory<ServletResponse>, Serializable {
      public ServletResponse getObject() {
         ServletResponse response = WebApplicationContextUtils.currentRequestAttributes().getResponse();
         if (response == null) {
            throw new IllegalStateException("Current servlet response not available - consider using RequestContextFilter instead of RequestContextListener");
         } else {
            return response;
         }
      }

      @Override
      public String toString() {
         return "Current HttpServletResponse";
      }
   }

   private static class SessionObjectFactory implements ObjectFactory<HttpSession>, Serializable {
      public HttpSession getObject() {
         return WebApplicationContextUtils.currentRequestAttributes().getRequest().getSession();
      }

      @Override
      public String toString() {
         return "Current HttpSession";
      }
   }

   private static class WebRequestObjectFactory implements ObjectFactory<WebRequest>, Serializable {
      public WebRequest getObject() {
         ServletRequestAttributes requestAttr = WebApplicationContextUtils.currentRequestAttributes();
         return new ServletWebRequest(requestAttr.getRequest(), requestAttr.getResponse());
      }

      @Override
      public String toString() {
         return "Current ServletWebRequest";
      }
   }
}
