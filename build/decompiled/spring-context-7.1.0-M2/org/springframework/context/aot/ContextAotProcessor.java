package org.springframework.context.aot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.aot.generate.DefaultGenerationContext;
import org.springframework.aot.generate.FileSystemGeneratedFiles;
import org.springframework.aot.generate.NameGenerator;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.javapoet.ClassName;
import org.springframework.util.CollectionUtils;

public abstract class ContextAotProcessor extends AbstractAotProcessor<ClassName> {
   private final Class<?> applicationClass;

   protected ContextAotProcessor(Class<?> applicationClass, AbstractAotProcessor.Settings settings) {
      super(settings);
      this.applicationClass = applicationClass;
   }

   protected Class<?> getApplicationClass() {
      return this.applicationClass;
   }

   protected ClassName doProcess() {
      this.deleteExistingOutput();

      try (GenericApplicationContext applicationContext = this.prepareApplicationContext(this.getApplicationClass())) {
         return this.performAotProcessing(applicationContext);
      }
   }

   protected abstract GenericApplicationContext prepareApplicationContext(Class<?> applicationClass);

   protected ClassName performAotProcessing(GenericApplicationContext applicationContext) {
      FileSystemGeneratedFiles generatedFiles = this.createFileSystemGeneratedFiles();
      DefaultGenerationContext generationContext = new DefaultGenerationContext(this.createNameGenerator(), generatedFiles);
      ApplicationContextAotGenerator generator = new ApplicationContextAotGenerator();
      ClassName generatedInitializerClassName = generator.processAheadOfTime(applicationContext, generationContext);
      this.registerEntryPointHint(generationContext, generatedInitializerClassName);
      generationContext.writeGeneratedContent();
      this.writeHints(generationContext.getRuntimeHints());
      this.writeNativeImageProperties(this.getDefaultNativeImageArguments(this.getApplicationClass().getName()));
      return generatedInitializerClassName;
   }

   protected NameGenerator createNameGenerator() {
      return new NameGenerator(ClassName.get(this.getApplicationClass()));
   }

   protected List<String> getDefaultNativeImageArguments(String applicationClassName) {
      List<String> args = new ArrayList<>();
      args.add("-H:Class=" + applicationClassName);
      args.add("--no-fallback");
      return args;
   }

   private void registerEntryPointHint(DefaultGenerationContext generationContext, ClassName generatedInitializerClassName) {
      TypeReference generatedType = TypeReference.of(generatedInitializerClassName.canonicalName());
      TypeReference applicationType = TypeReference.of(this.getApplicationClass());
      ReflectionHints reflection = generationContext.getRuntimeHints().reflection();
      reflection.registerType(applicationType, new MemberCategory[0]);
      reflection.registerType(
         generatedType, typeHint -> typeHint.onReachableType(applicationType).withConstructor(Collections.emptyList(), ExecutableMode.INVOKE)
      );
   }

   private void writeNativeImageProperties(List<String> args) {
      if (!CollectionUtils.isEmpty(args)) {
         StringBuilder sb = new StringBuilder();
         sb.append("Args = ");
         sb.append(String.join(String.format(" \\%n"), args));
         Path file = this.getSettings()
            .getResourceOutput()
            .resolve("META-INF/native-image/" + this.getSettings().getGroupId() + "/" + this.getSettings().getArtifactId() + "/native-image.properties");

         try {
            if (!Files.exists(file)) {
               Files.createDirectories(file.getParent());
               Files.createFile(file);
            }

            Files.writeString(file, sb.toString());
         } catch (IOException ex) {
            throw new IllegalStateException("Failed to write native-image.properties", ex);
         }
      }
   }
}
