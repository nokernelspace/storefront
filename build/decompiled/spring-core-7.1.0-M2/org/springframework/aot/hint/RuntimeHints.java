package org.springframework.aot.hint;

public class RuntimeHints {
   private final ReflectionHints reflection = new ReflectionHints();
   private final ResourceHints resources = new ResourceHints();
   private final SerializationHints serialization = new SerializationHints();
   private final ProxyHints proxies = new ProxyHints();
   private final ReflectionHints jni = new ReflectionHints();

   public ReflectionHints reflection() {
      return this.reflection;
   }

   public ResourceHints resources() {
      return this.resources;
   }

   @Deprecated(since = "7.0.6", forRemoval = true)
   public SerializationHints serialization() {
      return this.serialization;
   }

   public ProxyHints proxies() {
      return this.proxies;
   }

   public ReflectionHints jni() {
      return this.jni;
   }
}
