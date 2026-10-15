package org.springframework.aot.generate;

import org.springframework.aot.hint.RuntimeHints;

public interface GenerationContext {
   GeneratedClasses getGeneratedClasses();

   GeneratedResources getGeneratedResources();

   GeneratedFiles getGeneratedFiles();

   RuntimeHints getRuntimeHints();

   GenerationContext withName(String name);
}
