package org.springframework.http.codec;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.springframework.beans.BeanUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.ClassUtils;

final class CodecConfigurerFactory {
   private static final String DEFAULT_CONFIGURERS_PATH = "CodecConfigurer.properties";
   private static final Map<Class<?>, Class<?>> defaultCodecConfigurers = new HashMap<>(4);

   private CodecConfigurerFactory() {
   }

   public static <T extends CodecConfigurer> T create(Class<T> ifc) {
      Class<?> impl = defaultCodecConfigurers.get(ifc);
      if (impl == null) {
         throw new IllegalStateException("No default codec configurer found for " + ifc);
      } else {
         return (T)BeanUtils.instantiateClass(impl);
      }
   }

   static {
      try {
         Properties props = PropertiesLoaderUtils.loadProperties(new ClassPathResource("CodecConfigurer.properties", CodecConfigurerFactory.class));

         for (String ifcName : props.stringPropertyNames()) {
            String implName = props.getProperty(ifcName);
            Class<?> ifc = ClassUtils.forName(ifcName, CodecConfigurerFactory.class.getClassLoader());
            Class<?> impl = ClassUtils.forName(implName, CodecConfigurerFactory.class.getClassLoader());
            defaultCodecConfigurers.put(ifc, impl);
         }
      } catch (IOException | ClassNotFoundException ex) {
         throw new IllegalStateException(ex);
      }
   }
}
