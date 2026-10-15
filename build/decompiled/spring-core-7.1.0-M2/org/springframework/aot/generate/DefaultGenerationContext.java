package org.springframework.aot.generate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.util.Assert;

public class DefaultGenerationContext implements GenerationContext {
   private final Map<String, AtomicInteger> sequenceGenerator;
   private final GeneratedClasses generatedClasses;
   private final GeneratedResources generatedResources;
   private final GeneratedFiles generatedFiles;
   private final RuntimeHints runtimeHints;

   public DefaultGenerationContext(NameGenerator nameGenerator, GeneratedFiles generatedFiles) {
      this(nameGenerator, generatedFiles, new RuntimeHints());
   }

   public DefaultGenerationContext(NameGenerator nameGenerator, GeneratedFiles generatedFiles, RuntimeHints runtimeHints) {
      this(new GeneratedClasses(nameGenerator), new GeneratedResources(nameGenerator), generatedFiles, runtimeHints);
   }

   DefaultGenerationContext(GeneratedClasses generatedClasses, GeneratedResources generatedResources, GeneratedFiles generatedFiles, RuntimeHints runtimeHints) {
      Assert.notNull(generatedClasses, "'generatedClasses' must not be null");
      Assert.notNull(generatedResources, "'generatedResources' must not be null");
      Assert.notNull(generatedFiles, "'generatedFiles' must not be null");
      Assert.notNull(runtimeHints, "'runtimeHints' must not be null");
      this.sequenceGenerator = new ConcurrentHashMap<>();
      this.generatedClasses = generatedClasses;
      this.generatedResources = generatedResources;
      this.generatedFiles = generatedFiles;
      this.runtimeHints = runtimeHints;
   }

   protected DefaultGenerationContext(DefaultGenerationContext existing, String featureName) {
      int sequence = existing.sequenceGenerator.computeIfAbsent(featureName, key -> new AtomicInteger()).getAndIncrement();
      if (sequence > 0) {
         featureName = featureName + sequence;
      }

      this.sequenceGenerator = existing.sequenceGenerator;
      this.generatedClasses = existing.generatedClasses.withFeatureNamePrefix(featureName);
      this.generatedResources = existing.generatedResources.withFeatureNamePrefix(featureName);
      this.generatedFiles = existing.generatedFiles;
      this.runtimeHints = existing.runtimeHints;
   }

   @Override
   public GeneratedClasses getGeneratedClasses() {
      return this.generatedClasses;
   }

   @Override
   public GeneratedResources getGeneratedResources() {
      return this.generatedResources;
   }

   @Override
   public GeneratedFiles getGeneratedFiles() {
      return this.generatedFiles;
   }

   @Override
   public RuntimeHints getRuntimeHints() {
      return this.runtimeHints;
   }

   public DefaultGenerationContext withName(String name) {
      return new DefaultGenerationContext(this, name);
   }

   public void writeGeneratedContent() {
      this.generatedClasses.writeTo(this.generatedFiles);
      this.generatedResources.writeTo(this.generatedFiles);
   }
}
