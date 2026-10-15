package org.springframework.javapoet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor8;

public final class AnnotationSpec {
   public static final String VALUE = "value";
   private final TypeName type;
   private final Map<String, List<CodeBlock>> members;

   private AnnotationSpec(AnnotationSpec.Builder builder) {
      this.type = builder.type;
      this.members = Util.immutableMultimap(builder.members);
   }

   public TypeName type() {
      return this.type;
   }

   public Map<String, List<CodeBlock>> members() {
      return this.members;
   }

   void emit(CodeWriter codeWriter, boolean inline) throws IOException {
      String whitespace = inline ? "" : "\n";
      String memberSeparator = inline ? ", " : ",\n";
      if (this.members.isEmpty()) {
         codeWriter.emit("@$T", this.type);
      } else if (this.members.size() == 1 && this.members.containsKey("value")) {
         codeWriter.emit("@$T(", this.type);
         this.emitAnnotationValues(codeWriter, whitespace, memberSeparator, this.members.get("value"));
         codeWriter.emit(")");
      } else {
         codeWriter.emit("@$T(" + whitespace, this.type);
         codeWriter.indent(2);
         Iterator<Entry<String, List<CodeBlock>>> i = this.members.entrySet().iterator();

         while (i.hasNext()) {
            Entry<String, List<CodeBlock>> entry = i.next();
            codeWriter.emit("$L = ", entry.getKey());
            this.emitAnnotationValues(codeWriter, whitespace, memberSeparator, entry.getValue());
            if (i.hasNext()) {
               codeWriter.emit(memberSeparator);
            }
         }

         codeWriter.unindent(2);
         codeWriter.emit(whitespace + ")");
      }
   }

   private void emitAnnotationValues(CodeWriter codeWriter, String whitespace, String memberSeparator, List<CodeBlock> values) throws IOException {
      if (values.size() == 1) {
         codeWriter.indent(2);
         codeWriter.emit(values.get(0));
         codeWriter.unindent(2);
      } else {
         codeWriter.emit("{" + whitespace);
         codeWriter.indent(2);
         boolean first = true;

         for (CodeBlock codeBlock : values) {
            if (!first) {
               codeWriter.emit(memberSeparator);
            }

            codeWriter.emit(codeBlock);
            first = false;
         }

         codeWriter.unindent(2);
         codeWriter.emit(whitespace + "}");
      }
   }

   public static AnnotationSpec get(Annotation annotation) {
      return get(annotation, false);
   }

   public static AnnotationSpec get(Annotation annotation, boolean includeDefaultValues) {
      AnnotationSpec.Builder builder = builder(annotation.annotationType());

      try {
         Method[] methods = annotation.annotationType().getDeclaredMethods();
         Arrays.sort(methods, Comparator.comparing(Method::getName));

         for (Method method : methods) {
            Object value = method.invoke(annotation);
            if (includeDefaultValues || !Objects.deepEquals(value, method.getDefaultValue())) {
               if (value.getClass().isArray()) {
                  for (int i = 0; i < Array.getLength(value); i++) {
                     builder.addMemberForValue(method.getName(), Array.get(value, i));
                  }
               } else if (value instanceof Annotation annotationValue) {
                  builder.addMember(method.getName(), "$L", get(annotationValue));
               } else {
                  builder.addMemberForValue(method.getName(), value);
               }
            }
         }
      } catch (Exception e) {
         throw new RuntimeException("Reflecting " + annotation + " failed!", e);
      }

      return builder.build();
   }

   public static AnnotationSpec get(AnnotationMirror annotation) {
      TypeElement element = (TypeElement)annotation.getAnnotationType().asElement();
      AnnotationSpec.Builder builder = builder(ClassName.get(element));
      AnnotationSpec.Visitor visitor = new AnnotationSpec.Visitor(builder);

      for (ExecutableElement executableElement : annotation.getElementValues().keySet()) {
         String name = executableElement.getSimpleName().toString();
         AnnotationValue value = annotation.getElementValues().get(executableElement);
         value.accept(visitor, name);
      }

      return builder.build();
   }

   public static AnnotationSpec.Builder builder(ClassName type) {
      Util.checkNotNull(type, "type == null");
      return new AnnotationSpec.Builder(type);
   }

   public static AnnotationSpec.Builder builder(Class<?> type) {
      return builder(ClassName.get(type));
   }

   public AnnotationSpec.Builder toBuilder() {
      AnnotationSpec.Builder builder = new AnnotationSpec.Builder(this.type);

      for (Entry<String, List<CodeBlock>> entry : this.members.entrySet()) {
         builder.members.put(entry.getKey(), new ArrayList<>(entry.getValue()));
      }

      return builder;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o == null) {
         return false;
      } else {
         return this.getClass() != o.getClass() ? false : this.toString().equals(o.toString());
      }
   }

   @Override
   public int hashCode() {
      return this.toString().hashCode();
   }

   @Override
   public String toString() {
      StringBuilder out = new StringBuilder();

      try {
         CodeWriter codeWriter = new CodeWriter(out);
         codeWriter.emit("$L", this);
         return out.toString();
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   public static final class Builder {
      private final TypeName type;
      private final Map<String, List<CodeBlock>> members = new LinkedHashMap<>();

      private Builder(TypeName type) {
         this.type = type;
      }

      public AnnotationSpec.Builder addMember(String name, String format, Object... args) {
         return this.addMember(name, CodeBlock.of(format, args));
      }

      public AnnotationSpec.Builder addMember(String name, CodeBlock codeBlock) {
         List<CodeBlock> values = this.members.computeIfAbsent(name, _k -> new ArrayList<>());
         values.add(codeBlock);
         return this;
      }

      AnnotationSpec.Builder addMemberForValue(String memberName, Object value) {
         Util.checkNotNull(memberName, "memberName == null");
         Util.checkNotNull(value, "value == null, constant non-null value expected for %s", memberName);
         Util.checkArgument(SourceVersion.isName(memberName), "not a valid name: %s", memberName);
         if (value instanceof Class) {
            return this.addMember(memberName, "$T.class", value);
         } else if (value instanceof Enum<?> enumValue) {
            return this.addMember(memberName, "$T.$L", value.getClass(), enumValue.name());
         } else if (value instanceof String stringValue) {
            return this.addMember(memberName, "$S", stringValue);
         } else if (value instanceof Float floatValue) {
            return this.addMember(memberName, "$Lf", floatValue);
         } else if (value instanceof Long longValue) {
            return this.addMember(memberName, "$LL", longValue);
         } else {
            return value instanceof Character charValue
               ? this.addMember(memberName, "'$L'", Util.characterLiteralWithoutSingleQuotes(charValue))
               : this.addMember(memberName, "$L", value);
         }
      }

      public AnnotationSpec build() {
         for (String name : this.members.keySet()) {
            Util.checkNotNull(name, "name == null");
            Util.checkArgument(SourceVersion.isName(name), "not a valid name: %s", name);
         }

         return new AnnotationSpec(this);
      }
   }

   private static class Visitor extends SimpleAnnotationValueVisitor8<AnnotationSpec.Builder, String> {
      private final AnnotationSpec.Builder builder;

      Visitor(AnnotationSpec.Builder builder) {
         super(builder);
         this.builder = builder;
      }

      protected AnnotationSpec.Builder defaultAction(Object o, String name) {
         return this.builder.addMemberForValue(name, o);
      }

      public AnnotationSpec.Builder visitAnnotation(AnnotationMirror a, String name) {
         return this.builder.addMember(name, "$L", AnnotationSpec.get(a));
      }

      public AnnotationSpec.Builder visitEnumConstant(VariableElement c, String name) {
         return this.builder.addMember(name, "$T.$L", c.asType(), c.getSimpleName());
      }

      public AnnotationSpec.Builder visitType(TypeMirror t, String name) {
         return this.builder.addMember(name, "$T.class", t);
      }

      public AnnotationSpec.Builder visitArray(List<? extends AnnotationValue> values, String name) {
         for (AnnotationValue value : values) {
            value.accept(this, name);
         }

         return this.builder;
      }
   }
}
