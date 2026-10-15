package org.springframework.boot.ansi;

import java.io.Console;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public abstract class AnsiOutput {
   private static final String ENCODE_JOIN = ";";
   private static AnsiOutput.Enabled enabled = AnsiOutput.Enabled.DETECT;
   private static @Nullable Boolean consoleAvailable;
   private static @Nullable Boolean ansiCapable;
   private static final String OPERATING_SYSTEM_NAME = System.getProperty("os.name");
   private static final List<String> WINDOWS_ANSI_COMPATIBLE = List.of("Windows 11", "Windows Server 2025");
   private static final String ENCODE_START = "\u001b[";
   private static final String ENCODE_END = "m";
   private static final String RESET = "0;" + AnsiColor.DEFAULT;

   public static void setEnabled(AnsiOutput.Enabled enabled) {
      Assert.notNull(enabled, "'enabled' must not be null");
      AnsiOutput.enabled = enabled;
   }

   public static AnsiOutput.Enabled getEnabled() {
      return enabled;
   }

   public static void setConsoleAvailable(@Nullable Boolean consoleAvailable) {
      AnsiOutput.consoleAvailable = consoleAvailable;
   }

   public static String encode(AnsiElement element) {
      return isEnabled() ? "\u001b[" + element + "m" : "";
   }

   public static String toString(Object... elements) {
      StringBuilder sb = new StringBuilder();
      if (isEnabled()) {
         buildEnabled(sb, elements);
      } else {
         buildDisabled(sb, elements);
      }

      return sb.toString();
   }

   private static void buildEnabled(StringBuilder sb, Object[] elements) {
      boolean writingAnsi = false;
      boolean containsEncoding = false;

      for (Object element : elements) {
         if (element instanceof AnsiElement) {
            containsEncoding = true;
            if (!writingAnsi) {
               sb.append("\u001b[");
               writingAnsi = true;
            } else {
               sb.append(";");
            }
         } else if (writingAnsi) {
            sb.append("m");
            writingAnsi = false;
         }

         sb.append(element);
      }

      if (containsEncoding) {
         sb.append(writingAnsi ? ";" : "\u001b[");
         sb.append(RESET);
         sb.append("m");
      }
   }

   private static void buildDisabled(StringBuilder sb, @Nullable Object[] elements) {
      for (Object element : elements) {
         if (!(element instanceof AnsiElement) && element != null) {
            sb.append(element);
         }
      }
   }

   private static boolean isEnabled() {
      if (enabled == AnsiOutput.Enabled.DETECT) {
         if (ansiCapable == null) {
            ansiCapable = detectIfAnsiCapable();
         }

         return ansiCapable;
      } else {
         return enabled == AnsiOutput.Enabled.ALWAYS;
      }
   }

   private static boolean detectIfAnsiCapable() {
      try {
         if (Boolean.FALSE.equals(consoleAvailable)) {
            return false;
         }

         if (consoleAvailable == null) {
            Console console = System.console();
            if (console == null) {
               return false;
            }

            Method isTerminalMethod = ClassUtils.getMethodIfAvailable(Console.class, "isTerminal", new Class[0]);
            if (isTerminalMethod != null) {
               Boolean isTerminal = (Boolean)isTerminalMethod.invoke(console);
               if (Boolean.FALSE.equals(isTerminal)) {
                  return false;
               }
            }
         }

         return OPERATING_SYSTEM_NAME.toLowerCase(Locale.ENGLISH).contains("win") ? WINDOWS_ANSI_COMPATIBLE.contains(OPERATING_SYSTEM_NAME) : true;
      } catch (Throwable ex) {
         return false;
      }
   }

   public enum Enabled {
      DETECT,
      ALWAYS,
      NEVER;
   }
}
