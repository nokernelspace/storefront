package org.springframework.aot.hint.predicate;

public abstract class RuntimeHintsPredicates {
   private static final ReflectionHintsPredicates reflection = new ReflectionHintsPredicates();
   private static final ResourceHintsPredicates resource = new ResourceHintsPredicates();
   private static final SerializationHintsPredicates serialization = new SerializationHintsPredicates();
   private static final ProxyHintsPredicates proxies = new ProxyHintsPredicates();

   private RuntimeHintsPredicates() {
   }

   public static ReflectionHintsPredicates reflection() {
      return reflection;
   }

   public static ResourceHintsPredicates resource() {
      return resource;
   }

   @Deprecated(since = "7.0.6", forRemoval = true)
   public static SerializationHintsPredicates serialization() {
      return serialization;
   }

   public static ProxyHintsPredicates proxies() {
      return proxies;
   }
}
