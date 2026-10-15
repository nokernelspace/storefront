package org.springframework.context.aot;

import java.util.function.Consumer;
import org.springframework.aot.generate.GeneratedFiles;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.generate.GeneratedFiles.Kind;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.TypeHint.Builder;
import org.springframework.core.io.ByteArrayResource;

class CglibClassHandler {
   private static final Consumer<Builder> instantiateCglibProxy = hint -> hint.withMembers(new MemberCategory[]{MemberCategory.INVOKE_DECLARED_CONSTRUCTORS});
   private final RuntimeHints runtimeHints;
   private final GeneratedFiles generatedFiles;

   CglibClassHandler(GenerationContext generationContext) {
      this.runtimeHints = generationContext.getRuntimeHints();
      this.generatedFiles = generationContext.getGeneratedFiles();
   }

   public void handleGeneratedClass(String cglibClassName, byte[] content) {
      this.registerHints(TypeReference.of(cglibClassName));
      String path = cglibClassName.replace(".", "/") + ".class";
      this.generatedFiles.addFile(Kind.CLASS, path, new ByteArrayResource(content));
   }

   public void handleLoadedClass(Class<?> cglibClass) {
      this.registerHints(TypeReference.of(cglibClass));
   }

   private void registerHints(TypeReference cglibTypeReference) {
      this.runtimeHints.reflection().registerType(cglibTypeReference, instantiateCglibProxy);
   }
}
