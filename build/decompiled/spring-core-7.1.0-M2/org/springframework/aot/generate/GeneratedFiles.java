package org.springframework.aot.generate;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.InputStreamSource;
import org.springframework.javapoet.JavaFile;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.function.ThrowingConsumer;

public interface GeneratedFiles {
   default void addSourceFile(JavaFile javaFile) {
      validatePackage(javaFile.packageName(), javaFile.typeSpec().name());
      String className = javaFile.packageName() + "." + javaFile.typeSpec().name();
      this.addSourceFile(className, javaFile::writeTo);
   }

   default void addSourceFile(String className, CharSequence content) {
      this.addSourceFile(className, appendable -> appendable.append(content));
   }

   default void addSourceFile(String className, ThrowingConsumer<Appendable> content) {
      this.addFile(GeneratedFiles.Kind.SOURCE, getClassNamePath(className), content);
   }

   default void addSourceFile(String className, InputStreamSource content) {
      this.addFile(GeneratedFiles.Kind.SOURCE, getClassNamePath(className), content);
   }

   default void addResourceFile(String path, CharSequence content) {
      this.addResourceFile(path, appendable -> appendable.append(content));
   }

   default void addResourceFile(String path, ThrowingConsumer<Appendable> content) {
      this.addFile(GeneratedFiles.Kind.RESOURCE, path, content);
   }

   default void addResourceFile(String path, InputStreamSource content) {
      this.addFile(GeneratedFiles.Kind.RESOURCE, path, content);
   }

   default void addClassFile(String path, InputStreamSource content) {
      this.addFile(GeneratedFiles.Kind.CLASS, path, content);
   }

   default void addFile(GeneratedFiles.Kind kind, String path, CharSequence content) {
      this.addFile(kind, path, appendable -> appendable.append(content));
   }

   default void addFile(GeneratedFiles.Kind kind, String path, ThrowingConsumer<Appendable> content) {
      Assert.notNull(content, "'content' must not be null");
      this.addFile(kind, path, new AppendableConsumerInputStreamSource(content));
   }

   default void addFile(GeneratedFiles.Kind kind, String path, InputStreamSource content) {
      Assert.notNull(kind, "'kind' must not be null");
      Assert.hasLength(path, "'path' must not be empty");
      Assert.notNull(content, "'content' must not be null");
      this.handleFile(kind, path, handler -> handler.create(content));
   }

   void handleFile(GeneratedFiles.Kind kind, String path, ThrowingConsumer<GeneratedFiles.FileHandler> handler);

   private static String getClassNamePath(String className) {
      Assert.hasLength(className, "'className' must not be empty");
      validatePackage(ClassUtils.getPackageName(className), className);
      Assert.isTrue(isJavaIdentifier(className), "'className' must be a valid identifier, got '" + className + "'");
      return ClassUtils.convertClassNameToResourcePath(className) + ".java";
   }

   private static void validatePackage(String packageName, String className) {
      if (!StringUtils.hasLength(packageName)) {
         throw new IllegalArgumentException(
            "Could not add '" + className + "', processing classes in the default package is not supported. Did you forget to add a package statement?"
         );
      }
   }

   private static boolean isJavaIdentifier(String className) {
      char[] chars = className.toCharArray();

      for (int i = 0; i < chars.length; i++) {
         if (i == 0 && !Character.isJavaIdentifierStart(chars[i])) {
            return false;
         }

         if (i > 0 && chars[i] != '.' && !Character.isJavaIdentifierPart(chars[i])) {
            return false;
         }
      }

      return true;
   }

   abstract class FileHandler {
      private final boolean exists;
      private final Supplier<InputStreamSource> existingContent;

      protected FileHandler(boolean exists, Supplier<InputStreamSource> existingContent) {
         this.exists = exists;
         this.existingContent = existingContent;
      }

      public boolean exists() {
         return this.exists;
      }

      public @Nullable InputStreamSource getContent() {
         return this.exists() ? this.existingContent.get() : null;
      }

      public void create(InputStreamSource content) {
         Assert.notNull(content, "'content' must not be null");
         if (this.exists()) {
            throw new IllegalStateException("%s already exists".formatted(this));
         }

         this.copy(content, false);
      }

      public void override(InputStreamSource content) {
         Assert.notNull(content, "'content' must not be null");
         this.copy(content, true);
      }

      protected abstract void copy(InputStreamSource content, boolean override);
   }

   enum Kind {
      SOURCE,
      RESOURCE,
      CLASS;
   }
}
