package org.springframework.aot.nativex;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.core.SpringVersion;

class RuntimeHintsWriter {
   public void write(BasicJsonWriter writer, RuntimeHints hints) {
      Map<String, Object> document = new LinkedHashMap<>();
      String springVersion = SpringVersion.getVersion();
      if (springVersion != null) {
         document.put("comment", "Spring Framework " + springVersion);
      }

      List<Map<String, Object>> reflection = new ReflectionHintsAttributes().reflection(hints);
      if (!reflection.isEmpty()) {
         document.put("reflection", reflection);
      }

      List<Map<String, Object>> jni = new ReflectionHintsAttributes().jni(hints);
      if (!jni.isEmpty()) {
         document.put("jni", jni);
      }

      List<Map<String, Object>> resourceHints = new ResourceHintsAttributes().resources(hints.resources());
      if (!resourceHints.isEmpty()) {
         document.put("resources", resourceHints);
      }

      writer.writeObject(document);
   }
}
