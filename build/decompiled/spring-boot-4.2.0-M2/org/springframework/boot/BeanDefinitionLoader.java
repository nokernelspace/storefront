package org.springframework.boot;

import groovy.lang.Closure;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader;
import org.springframework.beans.factory.support.AbstractBeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.type.filter.AbstractTypeHierarchyTraversingFilter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

class BeanDefinitionLoader {
   private static final Pattern GROOVY_CLOSURE_PATTERN = Pattern.compile(".*\\$_.*closure.*");
   private final Object[] sources;
   private final AnnotatedBeanDefinitionReader annotatedReader;
   private final AbstractBeanDefinitionReader xmlReader;
   private final @Nullable BeanDefinitionReader groovyReader;
   private final ClassPathBeanDefinitionScanner scanner;
   private @Nullable ResourceLoader resourceLoader;

   BeanDefinitionLoader(BeanDefinitionRegistry registry, Object... sources) {
      Assert.notNull(registry, "'registry' must not be null");
      Assert.notEmpty(sources, "'sources' must not be empty");
      this.sources = sources;
      this.annotatedReader = new AnnotatedBeanDefinitionReader(registry);
      this.xmlReader = new XmlBeanDefinitionReader(registry);
      this.groovyReader = this.isGroovyPresent() ? new GroovyBeanDefinitionReader(registry) : null;
      this.scanner = new ClassPathBeanDefinitionScanner(registry);
      this.scanner.addExcludeFilter(new BeanDefinitionLoader.ClassExcludeFilter(sources));
   }

   void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
      this.annotatedReader.setBeanNameGenerator(beanNameGenerator);
      this.scanner.setBeanNameGenerator(beanNameGenerator);
      this.xmlReader.setBeanNameGenerator(beanNameGenerator);
   }

   void setResourceLoader(ResourceLoader resourceLoader) {
      this.resourceLoader = resourceLoader;
      this.scanner.setResourceLoader(resourceLoader);
      this.xmlReader.setResourceLoader(resourceLoader);
   }

   void setEnvironment(ConfigurableEnvironment environment) {
      this.annotatedReader.setEnvironment(environment);
      this.scanner.setEnvironment(environment);
      this.xmlReader.setEnvironment(environment);
   }

   void load() {
      for (Object source : this.sources) {
         this.load(source);
      }
   }

   private void load(Object source) {
      Assert.notNull(source, "'source' must not be null");
      if (source instanceof Class<?> type) {
         this.load(type);
      } else if (source instanceof Resource resource) {
         this.load(resource);
      } else if (source instanceof Package pack) {
         this.load(pack);
      } else if (source instanceof CharSequence sequence) {
         this.load(sequence);
      } else {
         throw new IllegalArgumentException("Invalid source type " + source.getClass());
      }
   }

   private void load(Class<?> source) {
      if (this.groovyReader != null && BeanDefinitionLoader.GroovyBeanDefinitionSource.class.isAssignableFrom(source)) {
         BeanDefinitionLoader.GroovyBeanDefinitionSource loader = (BeanDefinitionLoader.GroovyBeanDefinitionSource)BeanUtils.instantiateClass(
            source, BeanDefinitionLoader.GroovyBeanDefinitionSource.class
         );
         ((GroovyBeanDefinitionReader)this.groovyReader).beans(loader.getBeans());
      }

      if (this.isEligible(source)) {
         this.annotatedReader.register(new Class[]{source});
      }
   }

   private void load(Resource source) {
      String filename = source.getFilename();
      Assert.state(filename != null, "Source has no filename");
      if (filename.endsWith(".groovy")) {
         if (this.groovyReader == null) {
            throw new BeanDefinitionStoreException("Cannot load Groovy beans without Groovy on classpath");
         }

         this.groovyReader.loadBeanDefinitions(source);
      } else {
         this.xmlReader.loadBeanDefinitions(source);
      }
   }

   private void load(Package source) {
      this.scanner.scan(new String[]{source.getName()});
   }

   private void load(CharSequence source) {
      String resolvedSource = this.scanner.getEnvironment().resolvePlaceholders(source.toString());

      try {
         this.load(ClassUtils.forName(resolvedSource, null));
      } catch (IllegalArgumentException | ClassNotFoundException var4) {
         if (!this.loadAsResources(resolvedSource)) {
            Package packageResource = this.findPackage(resolvedSource);
            if (packageResource != null) {
               this.load(packageResource);
            } else {
               throw new IllegalArgumentException("Invalid source '" + resolvedSource + "'");
            }
         }
      }
   }

   private boolean loadAsResources(String resolvedSource) {
      boolean foundCandidate = false;
      Resource[] resources = this.findResources(resolvedSource);

      for (Resource resource : resources) {
         if (this.isLoadCandidate(resource)) {
            foundCandidate = true;
            this.load(resource);
         }
      }

      return foundCandidate;
   }

   private boolean isGroovyPresent() {
      return ClassUtils.isPresent("groovy.lang.MetaClass", null);
   }

   private Resource[] findResources(String source) {
      ResourceLoader loader = (ResourceLoader)(this.resourceLoader != null ? this.resourceLoader : new PathMatchingResourcePatternResolver());

      try {
         return loader instanceof ResourcePatternResolver resolver ? resolver.getResources(source) : new Resource[]{loader.getResource(source)};
      } catch (IOException ex) {
         throw new IllegalStateException("Error reading source '" + source + "'");
      }
   }

   private boolean isLoadCandidate(@Nullable Resource resource) {
      if (resource != null && resource.exists()) {
         if (resource instanceof ClassPathResource classPathResource) {
            String path = classPathResource.getPath();
            if (path.indexOf(46) == -1) {
               try {
                  return this.getClass().getClassLoader().getDefinedPackage(path) == null;
               } catch (Exception var5) {
               }
            }
         }

         return true;
      } else {
         return false;
      }
   }

   private @Nullable Package findPackage(CharSequence source) {
      Package pkg = this.getClass().getClassLoader().getDefinedPackage(source.toString());
      if (pkg != null) {
         return pkg;
      }

      try {
         ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(this.getClass().getClassLoader());
         Resource[] resources = resolver.getResources(ClassUtils.convertClassNameToResourcePath(source.toString()) + "/*.class");
         Resource[] var5 = resources;
         int var6 = var5.length;
         byte var7 = 0;
         if (var7 < var6) {
            Resource resource = var5[var7];
            String filename = resource.getFilename();
            Assert.state(filename != null, "No filename available");
            String className = StringUtils.stripFilenameExtension(filename);
            this.load(Class.forName(source + "." + className));
         }
      } catch (Exception var11) {
      }

      return this.getClass().getClassLoader().getDefinedPackage(source.toString());
   }

   private boolean isEligible(Class<?> type) {
      return !type.isAnonymousClass() && !this.isGroovyClosure(type) && !this.hasNoConstructors(type);
   }

   private boolean isGroovyClosure(Class<?> type) {
      return GROOVY_CLOSURE_PATTERN.matcher(type.getName()).matches();
   }

   private boolean hasNoConstructors(Class<?> type) {
      Constructor<?>[] constructors = type.getDeclaredConstructors();
      return ObjectUtils.isEmpty(constructors);
   }

   private static class ClassExcludeFilter extends AbstractTypeHierarchyTraversingFilter {
      private final Set<String> classNames = new HashSet<>();

      ClassExcludeFilter(Object... sources) {
         super(false, false);

         for (Object source : sources) {
            if (source instanceof Class<?> classSource) {
               this.classNames.add(classSource.getName());
            }
         }
      }

      protected boolean matchClassName(String className) {
         return this.classNames.contains(className);
      }
   }

   @FunctionalInterface
   protected interface GroovyBeanDefinitionSource {
      Closure<?> getBeans();
   }
}
