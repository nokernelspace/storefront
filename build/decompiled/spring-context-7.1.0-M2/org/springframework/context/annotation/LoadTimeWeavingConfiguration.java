package org.springframework.context.annotation;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.weaving.AspectJWeavingEnabler;
import org.springframework.context.weaving.DefaultContextLoadTimeWeaver;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.instrument.classloading.LoadTimeWeaver;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
@Role(2)
public class LoadTimeWeavingConfiguration implements ImportAware, BeanClassLoaderAware {
   private @Nullable AnnotationAttributes enableLTW;
   private @Nullable LoadTimeWeavingConfigurer ltwConfigurer;
   private @Nullable ClassLoader beanClassLoader;

   @Override
   public void setImportMetadata(AnnotationMetadata importMetadata) {
      this.enableLTW = AnnotationConfigUtils.attributesFor(importMetadata, EnableLoadTimeWeaving.class);
      if (this.enableLTW == null) {
         throw new IllegalArgumentException("@EnableLoadTimeWeaving is not present on importing class " + importMetadata.getClassName());
      }
   }

   @Autowired(required = false)
   public void setLoadTimeWeavingConfigurer(LoadTimeWeavingConfigurer ltwConfigurer) {
      this.ltwConfigurer = ltwConfigurer;
   }

   public void setBeanClassLoader(ClassLoader beanClassLoader) {
      this.beanClassLoader = beanClassLoader;
   }

   @Bean(name = "loadTimeWeaver")
   @Role(2)
   public LoadTimeWeaver loadTimeWeaver() {
      Assert.state(this.beanClassLoader != null, "No ClassLoader set");
      LoadTimeWeaver loadTimeWeaver = null;
      if (this.ltwConfigurer != null) {
         loadTimeWeaver = this.ltwConfigurer.getLoadTimeWeaver();
      }

      if (loadTimeWeaver == null) {
         loadTimeWeaver = new DefaultContextLoadTimeWeaver(this.beanClassLoader);
      }

      if (this.enableLTW != null) {
         EnableLoadTimeWeaving.AspectJWeaving aspectJWeaving = (EnableLoadTimeWeaving.AspectJWeaving)this.enableLTW.getEnum("aspectjWeaving");
         switch (aspectJWeaving) {
            case DISABLED:
            default:
               break;
            case AUTODETECT:
               if (this.beanClassLoader.getResource("META-INF/aop.xml") != null) {
                  AspectJWeavingEnabler.enableAspectJWeaving(loadTimeWeaver, this.beanClassLoader);
               }
               break;
            case ENABLED:
               AspectJWeavingEnabler.enableAspectJWeaving(loadTimeWeaver, this.beanClassLoader);
         }
      }

      return loadTimeWeaver;
   }
}
