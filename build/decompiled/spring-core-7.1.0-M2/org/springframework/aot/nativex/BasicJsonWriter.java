package org.springframework.aot.nativex;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.aot.hint.TypeReference;

class BasicJsonWriter {
   private final BasicJsonWriter.IndentingWriter writer;

   public BasicJsonWriter(Writer writer, String singleIndent) {
      this.writer = new BasicJsonWriter.IndentingWriter(writer, singleIndent);
   }

   public BasicJsonWriter(Writer writer) {
      this(writer, "  ");
   }

   public void writeObject(Map<String, Object> attributes) {
      this.writeObject(attributes, true);
   }

   public void writeArray(List<?> items) {
      this.writeArray(items, true);
   }

   private void writeObject(Map<String, Object> attributes, boolean newLine) {
      if (attributes.isEmpty()) {
         this.writer.print("{ }");
      } else {
         this.writer
            .println("{")
            .indented(this.writeAll(attributes.entrySet().iterator(), entry -> this.writeAttribute(entry.getKey(), entry.getValue())))
            .print("}");
      }

      if (newLine) {
         this.writer.println();
      }
   }

   private void writeArray(List<?> items, boolean newLine) {
      if (items.isEmpty()) {
         this.writer.print("[ ]");
      } else {
         this.writer.println("[").indented(this.writeAll(items.iterator(), this::writeValue)).print("]");
      }

      if (newLine) {
         this.writer.println();
      }
   }

   private <T> Runnable writeAll(Iterator<T> it, Consumer<T> writer) {
      return () -> {
         while (it.hasNext()) {
            writer.accept(it.next());
            if (it.hasNext()) {
               this.writer.println(",");
            } else {
               this.writer.println();
            }
         }
      };
   }

   private void writeAttribute(String name, Object value) {
      this.writer.print(this.quote(name) + ": ");
      this.writeValue(value);
   }

   private void writeValue(Object value) {
      if (value instanceof Map<?, ?> map) {
         this.writeObject((Map<String, Object>)map, false);
      } else if (value instanceof List<?> list) {
         this.writeArray(list, false);
      } else if (value instanceof TypeReference typeReference) {
         this.writer.print(this.quote(typeReference.getName()));
      } else if (value instanceof CharSequence string) {
         this.writer.print(this.quote(escape(string)));
      } else {
         if (!(value instanceof Boolean flag)) {
            throw new IllegalStateException("unsupported type: " + value.getClass());
         }

         this.writer.print(Boolean.toString(flag));
      }
   }

   private String quote(String name) {
      return "\"" + name + "\"";
   }

   private static String escape(CharSequence input) {
      StringBuilder builder = new StringBuilder();
      input.chars()
         .forEach(
            c -> {
               // $VF: Couldn't be decompiled
               // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
               // java.lang.RuntimeException: invalid constant type: Ljava/io/Serializable; with value \b
               //   at org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent.toJava(ConstExprent.java:364)
               //   at org.jetbrains.java.decompiler.modules.decompiler.exps.SwitchExprent.toJava(SwitchExprent.java:152)
               //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.getCastedExprent(ExprProcessor.java:1054)
               //   at org.jetbrains.java.decompiler.modules.decompiler.exps.InvocationExprent.appendParamList(InvocationExprent.java:1151)
               //   at org.jetbrains.java.decompiler.modules.decompiler.exps.InvocationExprent.toJava(InvocationExprent.java:921)
               //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.listToJava(ExprProcessor.java:925)
               //   at org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement.toJava(BasicBlockStatement.java:87)
               //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.jmpWrapper(ExprProcessor.java:860)
               //   at org.jetbrains.java.decompiler.modules.decompiler.stats.SequenceStatement.toJava(SequenceStatement.java:107)
               //   at org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement.toJava(RootStatement.java:36)
               //   at org.jetbrains.java.decompiler.main.ClassWriter.methodLambdaToJava(ClassWriter.java:1017)
               //
               // Bytecode:
               // 00: aload 0
               // 01: iload 1
               // 02: lookupswitch 114 8 8 89 9 109 10 99 12 94 13 104 34 74 47 84 92 79
               // 4c: ldc "\\\""
               // 4e: goto 92
               // 51: ldc "\\\\"
               // 53: goto 92
               // 56: ldc "\\/"
               // 58: goto 92
               // 5b: ldc "\\b"
               // 5d: goto 92
               // 60: ldc "\\f"
               // 62: goto 92
               // 65: ldc "\\n"
               // 67: goto 92
               // 6a: ldc "\\r"
               // 6c: goto 92
               // 6f: ldc "\\t"
               // 71: goto 92
               // 74: iload 1
               // 75: bipush 31
               // 77: if_icmpgt 8d
               // 7a: ldc "\\u%04x"
               // 7c: bipush 1
               // 7d: anewarray 2
               // 80: dup
               // 81: bipush 0
               // 82: iload 1
               // 83: invokestatic java/lang/Integer.valueOf (I)Ljava/lang/Integer;
               // 86: aastore
               // 87: invokestatic java/lang/String.format (Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;
               // 8a: goto 92
               // 8d: iload 1
               // 8e: i2c
               // 8f: invokestatic java/lang/Character.valueOf (C)Ljava/lang/Character;
               // 92: invokevirtual java/lang/StringBuilder.append (Ljava/lang/Object;)Ljava/lang/StringBuilder;
               // 95: pop
               // 96: return
            }
         );
      return builder.toString();
   }

   static class IndentingWriter extends Writer {
      private final Writer out;
      private final String singleIndent;
      private int level = 0;
      private String currentIndent = "";
      private boolean prependIndent = false;

      IndentingWriter(Writer out, String singleIndent) {
         this.out = out;
         this.singleIndent = singleIndent;
      }

      public BasicJsonWriter.IndentingWriter print(String string) {
         this.write(string.toCharArray(), 0, string.length());
         return this;
      }

      public BasicJsonWriter.IndentingWriter println(String string) {
         this.write(string.toCharArray(), 0, string.length());
         return this.println();
      }

      public BasicJsonWriter.IndentingWriter println() {
         String separator = System.lineSeparator();

         try {
            this.out.write(separator.toCharArray(), 0, separator.length());
         } catch (IOException ex) {
            throw new IllegalStateException(ex);
         }

         this.prependIndent = true;
         return this;
      }

      public BasicJsonWriter.IndentingWriter indented(Runnable runnable) {
         this.indent();
         runnable.run();
         return this.outdent();
      }

      private BasicJsonWriter.IndentingWriter indent() {
         this.level++;
         return this.refreshIndent();
      }

      private BasicJsonWriter.IndentingWriter outdent() {
         this.level--;
         return this.refreshIndent();
      }

      private BasicJsonWriter.IndentingWriter refreshIndent() {
         this.currentIndent = this.singleIndent.repeat(Math.max(0, this.level));
         return this;
      }

      @Override
      public void write(char[] chars, int offset, int length) {
         try {
            if (this.prependIndent) {
               this.out.write(this.currentIndent.toCharArray(), 0, this.currentIndent.length());
               this.prependIndent = false;
            }

            this.out.write(chars, offset, length);
         } catch (IOException ex) {
            throw new IllegalStateException(ex);
         }
      }

      @Override
      public void flush() throws IOException {
         this.out.flush();
      }

      @Override
      public void close() throws IOException {
         this.out.close();
      }
   }
}
