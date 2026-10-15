package org.springframework.aot.nativex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.ConditionalHint;
import org.springframework.aot.hint.ExecutableHint;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.FieldHint;
import org.springframework.aot.hint.JavaSerializationHint;
import org.springframework.aot.hint.JdkProxyHint;
import org.springframework.aot.hint.LambdaHint;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.MemberHint;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeHint;
import org.springframework.aot.hint.TypeReference;

class ReflectionHintsAttributes {
   private static final Comparator<JdkProxyHint> JDK_PROXY_HINT_COMPARATOR = (left, right) -> {
      String leftSignature = left.getProxiedInterfaces().stream().map(TypeReference::getCanonicalName).collect(Collectors.joining(","));
      String rightSignature = right.getProxiedInterfaces().stream().map(TypeReference::getCanonicalName).collect(Collectors.joining(","));
      return leftSignature.compareTo(rightSignature);
   };
   private static final Comparator<LambdaHint> LAMBDA_HINT_COMPARATOR = Comparator.comparing(LambdaHint::getDeclaringClass)
      .thenComparing(LambdaHint::getDeclaringMethod, Comparator.nullsFirst(Comparator.comparing(LambdaHint.DeclaringMethod::name)));

   public List<Map<String, Object>> reflection(RuntimeHints hints) {
      List<Map<String, Object>> reflectionHints = new ArrayList<>(this.reflectionHints(hints));
      reflectionHints.addAll(hints.proxies().jdkProxyHints().sorted(JDK_PROXY_HINT_COMPARATOR).map(this::toAttributes).toList());
      return reflectionHints;
   }

   private List<Map<String, Object>> reflectionHints(RuntimeHints hints) {
      Map<TypeReference, Map<String, Object>> allTypeHints = hints.reflection()
         .typeHints()
         .map(this::toAttributes)
         .collect(Collectors.toMap(attributes -> Objects.requireNonNull((TypeReference)attributes.get("type")), attributes -> attributes));
      hints.serialization()
         .javaSerializationHints()
         .forEach(hint -> allTypeHints.merge(hint.getType(), this.toAttributes(hint), (currentAttributes, newAttributes) -> {
            this.handleSerializable((Map<String, Object>)currentAttributes, true);
            return currentAttributes;
         }));
      return Stream.concat(
            allTypeHints.entrySet().stream().sorted(Entry.comparingByKey()).map(Entry::getValue),
            hints.reflection().lambdaHints().sorted(LAMBDA_HINT_COMPARATOR).map(this::toAttributes)
         )
         .toList();
   }

   public List<Map<String, Object>> jni(RuntimeHints hints) {
      List<Map<String, Object>> jniHints = new ArrayList<>();
      jniHints.addAll(hints.jni().typeHints().sorted(Comparator.comparing(TypeHint::getType)).map(this::toAttributes).toList());
      return jniHints;
   }

   private Map<String, Object> toAttributes(TypeHint hint) {
      Map<String, Object> attributes = new LinkedHashMap<>();
      attributes.put("type", hint.getType());
      this.handleCondition(attributes, hint);
      this.handleCategories(attributes, hint.getMemberCategories());
      this.handleFields(attributes, hint.fields());
      this.handleExecutables(attributes, Stream.concat(hint.constructors(), hint.methods()).sorted().toList());
      this.handleSerializable(attributes, hint.hasJavaSerialization());
      return attributes;
   }

   private Map<String, Object> toAttributes(LambdaHint hint) {
      Map<String, Object> attributes = new LinkedHashMap<>();
      Map<String, Object> lambdaAttributes = new LinkedHashMap<>();
      lambdaAttributes.put("declaringClass", hint.getDeclaringClass());
      LambdaHint.DeclaringMethod declaringMethod = hint.getDeclaringMethod();
      if (declaringMethod != null) {
         Map<String, Object> methodAttributes = new LinkedHashMap<>();
         methodAttributes.put("name", declaringMethod.name());
         methodAttributes.put("parameterTypes", declaringMethod.parameterTypes());
         lambdaAttributes.put("declaringMethod", methodAttributes);
      }

      lambdaAttributes.put("interfaces", hint.getInterfaces());
      attributes.put("lambda", lambdaAttributes);
      return Map.of("type", attributes);
   }

   private Map<String, Object> toAttributes(JavaSerializationHint serializationHint) {
      LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
      this.handleCondition(attributes, serializationHint);
      attributes.put("type", serializationHint.getType());
      this.handleSerializable(attributes, true);
      return attributes;
   }

   private void handleCondition(Map<String, Object> attributes, ConditionalHint hint) {
      if (hint.getReachableType() != null) {
         attributes.put("condition", Map.of("typeReached", hint.getReachableType()));
      }
   }

   private void handleFields(Map<String, Object> attributes, Stream<FieldHint> fields) {
      this.addIfNotEmpty(
         attributes,
         "fields",
         fields.sorted(Comparator.comparing(MemberHint::getName, String::compareToIgnoreCase)).map(fieldHint -> Map.of("name", fieldHint.getName())).toList()
      );
   }

   private void handleExecutables(Map<String, Object> attributes, List<ExecutableHint> hints) {
      this.addIfNotEmpty(attributes, "methods", hints.stream().filter(h -> h.getMode().equals(ExecutableMode.INVOKE)).map(this::toAttributes).toList());
   }

   private Map<String, Object> toAttributes(ExecutableHint hint) {
      Map<String, Object> attributes = new LinkedHashMap<>();
      attributes.put("name", hint.getName());
      attributes.put("parameterTypes", hint.getParameterTypes());
      return attributes;
   }

   private void handleCategories(Map<String, Object> attributes, Set<MemberCategory> categories) {
      categories.stream().sorted().forEach(category -> {
         switch (category) {
            case ACCESS_PUBLIC_FIELDS:
            case PUBLIC_FIELDS:
               attributes.put("allPublicFields", true);
               break;
            case ACCESS_DECLARED_FIELDS:
            case DECLARED_FIELDS:
               attributes.put("allDeclaredFields", true);
               break;
            case INVOKE_PUBLIC_CONSTRUCTORS:
               attributes.put("allPublicConstructors", true);
               break;
            case INVOKE_DECLARED_CONSTRUCTORS:
               attributes.put("allDeclaredConstructors", true);
               break;
            case INVOKE_PUBLIC_METHODS:
               attributes.put("allPublicMethods", true);
               break;
            case INVOKE_DECLARED_METHODS:
               attributes.put("allDeclaredMethods", true);
               break;
            case PUBLIC_CLASSES:
               attributes.put("allPublicClasses", true);
               break;
            case DECLARED_CLASSES:
               attributes.put("allDeclaredClasses", true);
               break;
            case UNSAFE_ALLOCATED:
               attributes.put("unsafeAllocated", true);
         }
      });
   }

   private void addIfNotEmpty(Map<String, Object> attributes, String name, @Nullable Object value) {
      if (value != null && value instanceof Collection<?> collection && !collection.isEmpty()) {
         attributes.put(name, value);
      }
   }

   private Map<String, Object> toAttributes(JdkProxyHint hint) {
      Map<String, Object> attributes = new LinkedHashMap<>();
      this.handleCondition(attributes, hint);
      attributes.put("type", Map.of("proxy", hint.getProxiedInterfaces()));
      this.handleSerializable(attributes, hint.hasJavaSerialization());
      return attributes;
   }

   private void handleSerializable(Map<String, Object> attributes, boolean serializable) {
      if (serializable) {
         attributes.put("serializable", serializable);
      }
   }
}
