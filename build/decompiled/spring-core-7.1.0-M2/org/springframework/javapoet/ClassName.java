package org.springframework.javapoet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.SimpleElementVisitor8;

public final class ClassName extends TypeName implements Comparable<ClassName> {
   public static final ClassName OBJECT = get(Object.class);
   static final ClassName BOXED_VOID = get(Void.class);
   static final ClassName BOXED_BOOLEAN = get(Boolean.class);
   static final ClassName BOXED_BYTE = get(Byte.class);
   static final ClassName BOXED_SHORT = get(Short.class);
   static final ClassName BOXED_INT = get(Integer.class);
   static final ClassName BOXED_LONG = get(Long.class);
   static final ClassName BOXED_CHAR = get(Character.class);
   static final ClassName BOXED_FLOAT = get(Float.class);
   static final ClassName BOXED_DOUBLE = get(Double.class);
   private static final String NO_PACKAGE = "";
   private final String packageName;
   private final ClassName enclosingClassName;
   private final String simpleName;
   private List<String> simpleNames;
   private final String canonicalName;

   private ClassName(String packageName, ClassName enclosingClassName, String simpleName) {
      this(packageName, enclosingClassName, simpleName, Collections.emptyList());
   }

   private ClassName(String packageName, ClassName enclosingClassName, String simpleName, List<AnnotationSpec> annotations) {
      super(annotations);
      this.packageName = Objects.requireNonNull(packageName, "packageName == null");
      this.enclosingClassName = enclosingClassName;
      this.simpleName = simpleName;
      this.canonicalName = enclosingClassName != null
         ? enclosingClassName.canonicalName + "." + simpleName
         : (packageName.isEmpty() ? simpleName : packageName + "." + simpleName);
      Util.checkArgument(SourceVersion.isName(simpleName), "not a valid name: %s", simpleName);
   }

   public ClassName annotated(AnnotationSpec... annotations) {
      return this.annotated(Arrays.asList(annotations));
   }

   public ClassName annotated(List<AnnotationSpec> annotations) {
      return new ClassName(this.packageName, this.enclosingClassName, this.simpleName, this.concatAnnotations(annotations));
   }

   public ClassName withoutAnnotations() {
      if (!this.isAnnotated()) {
         return this;
      }

      ClassName resultEnclosingClassName = this.enclosingClassName != null ? this.enclosingClassName.withoutAnnotations() : null;
      return new ClassName(this.packageName, resultEnclosingClassName, this.simpleName);
   }

   @Override
   public boolean isAnnotated() {
      return super.isAnnotated() || this.enclosingClassName != null && this.enclosingClassName.isAnnotated();
   }

   public String packageName() {
      return this.packageName;
   }

   public ClassName enclosingClassName() {
      return this.enclosingClassName;
   }

   public ClassName topLevelClassName() {
      return this.enclosingClassName != null ? this.enclosingClassName.topLevelClassName() : this;
   }

   public String reflectionName() {
      return this.enclosingClassName != null
         ? this.enclosingClassName.reflectionName() + "$" + this.simpleName
         : (this.packageName.isEmpty() ? this.simpleName : this.packageName + "." + this.simpleName);
   }

   public List<String> simpleNames() {
      if (this.simpleNames != null) {
         return this.simpleNames;
      }

      if (this.enclosingClassName == null) {
         this.simpleNames = Collections.singletonList(this.simpleName);
      } else {
         List<String> mutableNames = new ArrayList<>();
         mutableNames.addAll(this.enclosingClassName().simpleNames());
         mutableNames.add(this.simpleName);
         this.simpleNames = Collections.unmodifiableList(mutableNames);
      }

      return this.simpleNames;
   }

   public ClassName peerClass(String name) {
      return new ClassName(this.packageName, this.enclosingClassName, name);
   }

   public ClassName nestedClass(String name) {
      return new ClassName(this.packageName, this, name);
   }

   public String simpleName() {
      return this.simpleName;
   }

   public String canonicalName() {
      return this.canonicalName;
   }

   public static ClassName get(Class<?> clazz) {
      Util.checkNotNull(clazz, "clazz == null");
      Util.checkArgument(!clazz.isPrimitive(), "primitive types cannot be represented as a ClassName");
      Util.checkArgument(!void.class.equals(clazz), "'void' type cannot be represented as a ClassName");
      Util.checkArgument(!clazz.isArray(), "array types cannot be represented as a ClassName");
      String anonymousSuffix = "";

      while (clazz.isAnonymousClass()) {
         int lastDollar = clazz.getName().lastIndexOf(36);
         anonymousSuffix = clazz.getName().substring(lastDollar) + anonymousSuffix;
         clazz = clazz.getEnclosingClass();
      }

      String name = clazz.getSimpleName() + anonymousSuffix;
      if (clazz.getEnclosingClass() == null) {
         int lastDot = clazz.getName().lastIndexOf(46);
         String packageName = lastDot != -1 ? clazz.getName().substring(0, lastDot) : "";
         return new ClassName(packageName, null, name);
      } else {
         return get(clazz.getEnclosingClass()).nestedClass(name);
      }
   }

   public static ClassName get(String packageName, String simpleName, String... simpleNames) {
      ClassName className = new ClassName(packageName, null, simpleName);

      for (String name : simpleNames) {
         className = className.nestedClass(name);
      }

      return className;
   }

   public static ClassName get(TypeElement element) {
      Util.checkNotNull(element, "element == null");
      final String simpleName = element.getSimpleName().toString();
      return element.getEnclosingElement().accept(new SimpleElementVisitor8<ClassName, Void>() {
         public ClassName visitPackage(PackageElement packageElement, Void _p) {
            return new ClassName(packageElement.getQualifiedName().toString(), null, simpleName);
         }

         public ClassName visitType(TypeElement enclosingClass, Void _p) {
            return ClassName.get(enclosingClass).nestedClass(simpleName);
         }

         public ClassName visitUnknown(Element _unknown, Void _p) {
            return ClassName.get("", simpleName);
         }

         public ClassName defaultAction(Element _enclosingElement, Void _p) {
            throw new IllegalArgumentException("Unexpected type nesting: " + element);
         }
      }, null);
   }

   public static ClassName bestGuess(String classNameString) {
      int p = 0;

      while (p < classNameString.length() && Character.isLowerCase(classNameString.codePointAt(p))) {
         p = classNameString.indexOf(46, p) + 1;
         Util.checkArgument(p != 0, "couldn't make a guess for %s", classNameString);
      }

      String packageName = p == 0 ? "" : classNameString.substring(0, p - 1);
      ClassName className = null;

      for (String simpleName : classNameString.substring(p).split("\\.", -1)) {
         Util.checkArgument(!simpleName.isEmpty() && Character.isUpperCase(simpleName.codePointAt(0)), "couldn't make a guess for %s", classNameString);
         className = new ClassName(packageName, className, simpleName);
      }

      return className;
   }

   public int compareTo(ClassName o) {
      return this.canonicalName.compareTo(o.canonicalName);
   }

   @Override
   CodeWriter emit(CodeWriter out) throws IOException {
      boolean charsEmitted = false;

      for (ClassName className : this.enclosingClasses()) {
         String simpleName;
         if (charsEmitted) {
            out.emit(".");
            simpleName = className.simpleName;
         } else {
            if (!className.isAnnotated() && className != this) {
               continue;
            }

            String qualifiedName = out.lookupName(className);
            int dot = qualifiedName.lastIndexOf(46);
            if (dot != -1) {
               out.emitAndIndent(qualifiedName.substring(0, dot + 1));
               simpleName = qualifiedName.substring(dot + 1);
               charsEmitted = true;
            } else {
               simpleName = qualifiedName;
            }
         }

         if (className.isAnnotated()) {
            if (charsEmitted) {
               out.emit(" ");
            }

            className.emitAnnotations(out);
         }

         out.emit(simpleName);
         charsEmitted = true;
      }

      return out;
   }

   private List<ClassName> enclosingClasses() {
      List<ClassName> result = new ArrayList<>();

      for (ClassName c = this; c != null; c = c.enclosingClassName) {
         result.add(c);
      }

      Collections.reverse(result);
      return result;
   }
}
