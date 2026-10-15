package org.springframework.aot.nativex;

import java.util.function.Consumer;
import org.springframework.aot.hint.RuntimeHints;

public abstract class NativeConfigurationWriter {
   public void write(RuntimeHints hints) {
      if (this.hasAnyHint(hints)) {
         this.writeTo("reachability-metadata.json", writer -> new RuntimeHintsWriter().write(writer, hints));
      }
   }

   private boolean hasAnyHint(RuntimeHints hints) {
      return hints.proxies().jdkProxyHints().findAny().isPresent()
         || hints.reflection().typeHints().findAny().isPresent()
         || hints.reflection().lambdaHints().findAny().isPresent()
         || hints.resources().resourcePatternHints().findAny().isPresent()
         || hints.resources().resourceBundleHints().findAny().isPresent()
         || hints.jni().typeHints().findAny().isPresent()
         || this.hasAnyDeprecatedHint(hints);
   }

   private boolean hasAnyDeprecatedHint(RuntimeHints hints) {
      return hints.serialization().javaSerializationHints().findAny().isPresent();
   }

   protected abstract void writeTo(String fileName, Consumer<BasicJsonWriter> writer);
}
