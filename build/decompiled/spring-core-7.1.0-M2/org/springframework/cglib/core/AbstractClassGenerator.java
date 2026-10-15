package org.springframework.cglib.core;

import java.lang.ref.WeakReference;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import org.springframework.asm.ClassReader;
import org.springframework.cglib.core.internal.Function;
import org.springframework.cglib.core.internal.LoadingCache;

public abstract class AbstractClassGenerator<T> implements ClassGenerator {
   private static final ThreadLocal CURRENT = new ThreadLocal();
   private static volatile Map<ClassLoader, AbstractClassGenerator.ClassLoaderData> CACHE = new WeakHashMap<>();
   private static final boolean DEFAULT_USE_CACHE = Boolean.parseBoolean(System.getProperty("cglib.useCache", "true"));
   private static final boolean inNativeImage;
   private GeneratorStrategy strategy = DefaultGeneratorStrategy.INSTANCE;
   private NamingPolicy namingPolicy = DefaultNamingPolicy.INSTANCE;
   private AbstractClassGenerator.Source source;
   private ClassLoader classLoader;
   private Class contextClass;
   private String namePrefix;
   private Object key;
   private boolean useCache = DEFAULT_USE_CACHE;
   private String className;
   private boolean attemptLoad;

   protected T wrapCachedClass(Class klass) {
      return (T)(new WeakReference<Class>(klass));
   }

   protected Object unwrapCachedValue(T cached) {
      return ((WeakReference)cached).get();
   }

   protected AbstractClassGenerator(AbstractClassGenerator.Source source) {
      this.source = source;
   }

   protected void setNamePrefix(String namePrefix) {
      this.namePrefix = namePrefix;
   }

   protected final String getClassName() {
      return this.className;
   }

   private void setClassName(String className) {
      this.className = className;
   }

   private String generateClassName(Predicate nameTestPredicate) {
      return this.namingPolicy.getClassName(this.namePrefix, this.source.name, this.key, nameTestPredicate);
   }

   public void setClassLoader(ClassLoader classLoader) {
      this.classLoader = classLoader;
   }

   public void setContextClass(Class contextClass) {
      this.contextClass = contextClass;
   }

   public void setNamingPolicy(NamingPolicy namingPolicy) {
      if (namingPolicy == null) {
         namingPolicy = DefaultNamingPolicy.INSTANCE;
      }

      this.namingPolicy = namingPolicy;
   }

   public NamingPolicy getNamingPolicy() {
      return this.namingPolicy;
   }

   public void setUseCache(boolean useCache) {
      this.useCache = useCache;
   }

   public boolean getUseCache() {
      return this.useCache;
   }

   public void setAttemptLoad(boolean attemptLoad) {
      this.attemptLoad = attemptLoad;
   }

   public boolean getAttemptLoad() {
      return this.attemptLoad;
   }

   public void setStrategy(GeneratorStrategy strategy) {
      if (strategy == null) {
         strategy = DefaultGeneratorStrategy.INSTANCE;
      }

      this.strategy = strategy;
   }

   public GeneratorStrategy getStrategy() {
      return this.strategy;
   }

   public static AbstractClassGenerator getCurrent() {
      return (AbstractClassGenerator)CURRENT.get();
   }

   public ClassLoader getClassLoader() {
      ClassLoader t = this.classLoader;
      if (t == null) {
         t = this.getDefaultClassLoader();
      }

      if (t == null) {
         t = this.getClass().getClassLoader();
      }

      if (t == null) {
         t = Thread.currentThread().getContextClassLoader();
      }

      if (t == null) {
         throw new IllegalStateException("Cannot determine classloader");
      } else {
         return t;
      }
   }

   protected abstract ClassLoader getDefaultClassLoader();

   protected ProtectionDomain getProtectionDomain() {
      return null;
   }

   protected Object create(Object key) {
      try {
         ClassLoader loader = this.getClassLoader();
         Map<ClassLoader, AbstractClassGenerator.ClassLoaderData> cache = CACHE;
         AbstractClassGenerator.ClassLoaderData data = cache.get(loader);
         if (data == null) {
            synchronized (AbstractClassGenerator.class) {
               cache = CACHE;
               data = cache.get(loader);
               if (data == null) {
                  Map<ClassLoader, AbstractClassGenerator.ClassLoaderData> newCache = new WeakHashMap<>(cache);
                  data = new AbstractClassGenerator.ClassLoaderData(loader);
                  newCache.put(loader, data);
                  CACHE = newCache;
               }
            }
         }

         this.key = key;
         Object obj = data.get(this, this.getUseCache());
         return obj instanceof Class<?> clazz ? this.firstInstance(clazz) : this.nextInstance(obj);
      } catch (RuntimeException | Error ex) {
         throw ex;
      } catch (Exception ex) {
         throw new CodeGenerationException(ex);
      }
   }

   protected Class generate(AbstractClassGenerator.ClassLoaderData data) {
      Object save = CURRENT.get();
      CURRENT.set(this);

      try {
         ClassLoader classLoader = data.getClassLoader();
         if (classLoader == null) {
            throw new IllegalStateException(
               "ClassLoader is null while trying to define class "
                  + this.getClassName()
                  + ". It seems that the loader has been expired from a weak reference somehow. Please file an issue at cglib's issue tracker."
            );
         }

         synchronized (classLoader) {
            String name = this.generateClassName(data.getUniqueNamePredicate());
            data.reserveName(name);
            this.setClassName(name);
         }

         if (this.attemptLoad) {
            try {
               Class gen;
               synchronized (classLoader) {
                  gen = ReflectUtils.loadClass(this.getClassName(), classLoader);
               }

               return gen;
            } catch (ClassNotFoundException var21) {
            }
         }

         if (inNativeImage) {
            throw new UnsupportedOperationException(
               "CGLIB runtime enhancement not supported on native image. Make sure to enable Spring AOT processing to pre-generate '"
                  + this.getClassName()
                  + "' at build time."
            );
         }

         byte[] b = this.strategy.generate(this);
         String className = ClassNameReader.getClassName(new ClassReader(b));
         ProtectionDomain protectionDomain = this.getProtectionDomain();
         Class gen;
         synchronized (classLoader) {
            gen = ReflectUtils.defineClass(className, b, classLoader, protectionDomain, this.contextClass);
         }

         return gen;
      } catch (RuntimeException | Error ex) {
         throw ex;
      } catch (Exception ex) {
         throw new CodeGenerationException(ex);
      } finally {
         CURRENT.set(save);
      }
   }

   protected abstract Object firstInstance(Class type) throws Exception;

   protected abstract Object nextInstance(Object instance) throws Exception;

   static {
      String imageCode = System.getProperty("org.graalvm.nativeimage.imagecode");
      inNativeImage = "buildtime".equals(imageCode) || "runtime".equals(imageCode);
   }

   protected static class ClassLoaderData {
      private final Set<String> reservedClassNames = new HashSet<>();
      private final LoadingCache<AbstractClassGenerator, Object, Object> generatedClasses;
      private final WeakReference<ClassLoader> classLoader;
      private final Predicate uniqueNamePredicate = this.reservedClassNames::contains;
      private static final Function<AbstractClassGenerator, Object> GET_KEY = gen -> gen.key;

      public ClassLoaderData(ClassLoader classLoader) {
         if (classLoader == null) {
            throw new IllegalArgumentException("classLoader == null is not yet supported");
         }

         this.classLoader = new WeakReference<>(classLoader);
         Function<AbstractClassGenerator, Object> load = gen -> {
            Class klass = gen.generate(this);
            return gen.wrapCachedClass(klass);
         };
         this.generatedClasses = new LoadingCache<>(GET_KEY, load);
      }

      public ClassLoader getClassLoader() {
         return this.classLoader.get();
      }

      public void reserveName(String name) {
         this.reservedClassNames.add(name);
      }

      public Predicate getUniqueNamePredicate() {
         return this.uniqueNamePredicate;
      }

      public Object get(AbstractClassGenerator gen, boolean useCache) {
         Object value = null;
         if (useCache) {
            Object cachedValue = this.generatedClasses.get(gen);
            value = gen.unwrapCachedValue(cachedValue);
         }

         if (value == null) {
            value = gen.generate(this);
         }

         return value;
      }
   }

   protected static class Source {
      String name;

      public Source(String name) {
         this.name = name;
      }
   }
}
