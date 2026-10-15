package org.springframework.boot.system;

import java.io.Console;
import java.io.Reader;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.concurrent.Future;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.util.ClassUtils;

public enum JavaVersion {
   SEVENTEEN("17", Console.class, "charset"),
   EIGHTEEN("18", Duration.class, "isPositive"),
   NINETEEN("19", Future.class, "state"),
   TWENTY("20", Class.class, "accessFlags"),
   TWENTY_ONE("21", SortedSet.class, "getFirst"),
   TWENTY_TWO("22", Console.class, "isTerminal"),
   TWENTY_THREE("23", NumberFormat.class, "isStrict"),
   TWENTY_FOUR("24", Reader.class, "of", CharSequence.class),
   TWENTY_FIVE("25", Reader.class, "readAllLines"),
   TWENTY_SIX("26", String.class, "equalsFoldCase", String.class),
   TWENTY_SEVEN("27", String.class, "encodedLength", Charset.class);

   private final String name;
   private final boolean available;
   private final Class<?> versionSpecificClass;

   JavaVersion(String name, Class<?> versionSpecificClass, String versionSpecificMethod, Class<?>... paramTypes) {
      this.name = name;
      this.versionSpecificClass = versionSpecificClass;
      this.available = ClassUtils.hasMethod(versionSpecificClass, versionSpecificMethod, paramTypes);
   }

   @Override
   public String toString() {
      return this.name;
   }

   public static JavaVersion getJavaVersion() {
      List<JavaVersion> candidates = Arrays.asList(values());
      Collections.reverse(candidates);

      for (JavaVersion candidate : candidates) {
         if (candidate.available) {
            return candidate;
         }
      }

      return SEVENTEEN;
   }

   public boolean isEqualOrNewerThan(JavaVersion version) {
      return this.compareTo(version) >= 0;
   }

   public boolean isOlderThan(JavaVersion version) {
      return this.compareTo(version) < 0;
   }

   static class Hints implements RuntimeHintsRegistrar {
      public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
         for (JavaVersion javaVersion : JavaVersion.values()) {
            hints.reflection().registerType(javaVersion.versionSpecificClass, new MemberCategory[0]);
         }
      }
   }
}
