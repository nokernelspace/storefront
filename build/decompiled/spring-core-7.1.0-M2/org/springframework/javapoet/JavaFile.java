package org.springframework.javapoet;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.JavaFileObject.Kind;

public final class JavaFile {
   private static final Appendable NULL_APPENDABLE = new Appendable() {
      @Override
      public Appendable append(CharSequence _charSequence) {
         return this;
      }

      @Override
      public Appendable append(CharSequence _charSequence, int _start, int _end) {
         return this;
      }

      @Override
      public Appendable append(char _c) {
         return this;
      }
   };
   private final CodeBlock fileComment;
   private final String packageName;
   private final TypeSpec typeSpec;
   private final boolean skipJavaLangImports;
   private final Set<String> staticImports;
   private final Set<String> alwaysQualify;
   private final String indent;

   private JavaFile(JavaFile.Builder builder) {
      this.fileComment = builder.fileComment.build();
      this.packageName = builder.packageName;
      this.typeSpec = builder.typeSpec;
      this.skipJavaLangImports = builder.skipJavaLangImports;
      this.staticImports = Util.immutableSet(builder.staticImports);
      this.indent = builder.indent;
      Set<String> alwaysQualifiedNames = new LinkedHashSet<>();
      this.fillAlwaysQualifiedNames(builder.typeSpec, alwaysQualifiedNames);
      this.alwaysQualify = Util.immutableSet(alwaysQualifiedNames);
   }

   public String packageName() {
      return this.packageName;
   }

   public TypeSpec typeSpec() {
      return this.typeSpec;
   }

   private void fillAlwaysQualifiedNames(TypeSpec spec, Set<String> alwaysQualifiedNames) {
      alwaysQualifiedNames.addAll(spec.alwaysQualifiedNames());

      for (TypeSpec nested : spec.typeSpecs()) {
         this.fillAlwaysQualifiedNames(nested, alwaysQualifiedNames);
      }
   }

   public void writeTo(Appendable out) throws IOException {
      CodeWriter importsCollector = new CodeWriter(NULL_APPENDABLE, this.indent, this.staticImports, this.alwaysQualify);
      this.emit(importsCollector);
      Map<String, ClassName> suggestedImports = importsCollector.suggestedImports();
      CodeWriter codeWriter = new CodeWriter(out, this.indent, suggestedImports, this.staticImports, this.alwaysQualify);
      this.emit(codeWriter);
   }

   public void writeTo(File directory) throws IOException {
      this.writeTo(directory.toPath());
   }

   public void writeTo(Path directory) throws IOException {
      this.writeToPath(directory);
   }

   public void writeTo(Path directory, Charset charset) throws IOException {
      this.writeToPath(directory, charset);
   }

   public void writeTo(Filer filer) throws IOException {
      String fileName = this.packageName.isEmpty() ? this.typeSpec.name() : this.packageName + "." + this.typeSpec.name();
      List<Element> originatingElements = this.typeSpec.originatingElements();
      JavaFileObject filerSourceFile = filer.createSourceFile(fileName, originatingElements.toArray(new Element[0]));

      try {
         try (Writer writer = filerSourceFile.openWriter()) {
            this.writeTo(writer);
         }
      } catch (Exception e) {
         try {
            filerSourceFile.delete();
         } catch (Exception var8) {
         }

         throw e;
      }
   }

   public File writeToFile(File directory) throws IOException {
      Path outputPath = this.writeToPath(directory.toPath());
      return outputPath.toFile();
   }

   public Path writeToPath(Path directory) throws IOException {
      return this.writeToPath(directory, StandardCharsets.UTF_8);
   }

   public Path writeToPath(Path directory, Charset charset) throws IOException {
      Util.checkArgument(Files.notExists(directory) || Files.isDirectory(directory), "path %s exists but is not a directory.", directory);
      Path outputDirectory = directory;
      if (!this.packageName.isEmpty()) {
         for (String packageComponent : this.packageName.split("\\.", -1)) {
            outputDirectory = outputDirectory.resolve(packageComponent);
         }

         Files.createDirectories(outputDirectory);
      }

      Path outputPath = outputDirectory.resolve(this.typeSpec.name() + ".java");

      try (Writer writer = new OutputStreamWriter(Files.newOutputStream(outputPath), charset)) {
         this.writeTo(writer);
      }

      return outputPath;
   }

   private void emit(CodeWriter codeWriter) throws IOException {
      codeWriter.pushPackage(this.packageName);
      if (!this.fileComment.isEmpty()) {
         codeWriter.emitComment(this.fileComment);
      }

      if (!this.packageName.isEmpty()) {
         codeWriter.emit("package $L;\n", this.packageName);
         codeWriter.emit("\n");
      }

      if (!this.staticImports.isEmpty()) {
         for (String signature : this.staticImports) {
            codeWriter.emit("import static $L;\n", signature);
         }

         codeWriter.emit("\n");
      }

      int importedTypesCount = 0;

      for (ClassName className : new TreeSet<>(codeWriter.importedTypes().values())) {
         if (!this.skipJavaLangImports || !className.packageName().equals("java.lang") || this.alwaysQualify.contains(className.simpleName())) {
            codeWriter.emit("import $L;\n", className.withoutAnnotations());
            importedTypesCount++;
         }
      }

      if (importedTypesCount > 0) {
         codeWriter.emit("\n");
      }

      this.typeSpec.emit(codeWriter, null, Collections.emptySet());
      codeWriter.popPackage();
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
      try {
         StringBuilder result = new StringBuilder();
         this.writeTo(result);
         return result.toString();
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   public JavaFileObject toJavaFileObject() {
      URI uri = URI.create(
         (this.packageName.isEmpty() ? this.typeSpec.name() : this.packageName.replace('.', '/') + "/" + this.typeSpec.name()) + Kind.SOURCE.extension
      );
      return new SimpleJavaFileObject(uri, Kind.SOURCE) {
         private final long lastModified = System.currentTimeMillis();

         public String getCharContent(boolean _ignoreEncodingErrors) {
            return JavaFile.this.toString();
         }

         @Override
         public InputStream openInputStream() {
            return new ByteArrayInputStream(this.getCharContent(true).getBytes(StandardCharsets.UTF_8));
         }

         @Override
         public long getLastModified() {
            return this.lastModified;
         }
      };
   }

   public static JavaFile.Builder builder(String packageName, TypeSpec typeSpec) {
      Util.checkNotNull(packageName, "packageName == null");
      Util.checkNotNull(typeSpec, "typeSpec == null");
      return new JavaFile.Builder(packageName, typeSpec);
   }

   public JavaFile.Builder toBuilder() {
      JavaFile.Builder builder = new JavaFile.Builder(this.packageName, this.typeSpec);
      builder.fileComment.add(this.fileComment);
      builder.skipJavaLangImports = this.skipJavaLangImports;
      builder.indent = this.indent;
      return builder;
   }

   public static final class Builder {
      private final String packageName;
      private final TypeSpec typeSpec;
      private final CodeBlock.Builder fileComment = CodeBlock.builder();
      private boolean skipJavaLangImports;
      private String indent = "  ";
      private final Set<String> staticImports = new TreeSet<>();

      private Builder(String packageName, TypeSpec typeSpec) {
         this.packageName = packageName;
         this.typeSpec = typeSpec;
      }

      public JavaFile.Builder addFileComment(String format, Object... args) {
         this.fileComment.add(format, args);
         return this;
      }

      public JavaFile.Builder addStaticImport(Enum<?> constant) {
         return this.addStaticImport(ClassName.get(constant.getDeclaringClass()), constant.name());
      }

      public JavaFile.Builder addStaticImport(Class<?> clazz, String... names) {
         return this.addStaticImport(ClassName.get(clazz), names);
      }

      public JavaFile.Builder addStaticImport(ClassName className, String... names) {
         Util.checkArgument(className != null, "className == null");
         Util.checkArgument(names != null, "names == null");
         Util.checkArgument(names.length > 0, "names array is empty");

         for (String name : names) {
            Util.checkArgument(name != null, "null entry in names array: %s", Arrays.toString(names));
            this.staticImports.add(className.canonicalName() + "." + name);
         }

         return this;
      }

      public JavaFile.Builder skipJavaLangImports(boolean skipJavaLangImports) {
         this.skipJavaLangImports = skipJavaLangImports;
         return this;
      }

      public JavaFile.Builder indent(String indent) {
         this.indent = indent;
         return this;
      }

      public JavaFile build() {
         return new JavaFile(this);
      }
   }
}
