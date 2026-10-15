package org.springframework.javapoet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.StreamSupport;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

public final class CodeBlock {
   private static final Pattern NAMED_ARGUMENT = Pattern.compile("\\$(?<argumentName>[\\w_]+):(?<typeChar>[\\w]).*");
   private static final Pattern LOWERCASE = Pattern.compile("[a-z]+[\\w_]*");
   private final List<String> formatParts;
   private final List<Object> args;

   private CodeBlock(CodeBlock.Builder builder) {
      this.formatParts = Util.immutableList(builder.formatParts);
      this.args = Util.immutableList(builder.args);
   }

   public boolean isEmpty() {
      return this.formatParts.isEmpty();
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

   List<String> formatParts() {
      return this.formatParts;
   }

   List<Object> args() {
      return this.args;
   }

   @Override
   public int hashCode() {
      return this.toString().hashCode();
   }

   @Override
   public String toString() {
      StringBuilder out = new StringBuilder();

      try {
         new CodeWriter(out).emit(this);
         return out.toString();
      } catch (IOException e) {
         throw new UncheckedIOException(e);
      }
   }

   public static CodeBlock of(String format, Object... args) {
      return new CodeBlock.Builder().add(format, args).build();
   }

   public static CodeBlock join(Iterable<CodeBlock> codeBlocks, String separator) {
      return StreamSupport.stream(codeBlocks.spliterator(), false).collect(joining(separator));
   }

   public static Collector<CodeBlock, ?, CodeBlock> joining(String separator) {
      return Collector.of(
         () -> new CodeBlock.CodeBlockJoiner(separator, builder()),
         CodeBlock.CodeBlockJoiner::add,
         CodeBlock.CodeBlockJoiner::merge,
         CodeBlock.CodeBlockJoiner::join
      );
   }

   public static Collector<CodeBlock, ?, CodeBlock> joining(String separator, String prefix, String suffix) {
      CodeBlock.Builder builder = builder().add("$N", prefix);
      return Collector.of(
         () -> new CodeBlock.CodeBlockJoiner(separator, builder), CodeBlock.CodeBlockJoiner::add, CodeBlock.CodeBlockJoiner::merge, joiner -> {
            builder.add(of("$N", suffix));
            return joiner.join();
         }
      );
   }

   public static CodeBlock.Builder builder() {
      return new CodeBlock.Builder();
   }

   public CodeBlock.Builder toBuilder() {
      CodeBlock.Builder builder = new CodeBlock.Builder();
      builder.formatParts.addAll(this.formatParts);
      builder.args.addAll(this.args);
      return builder;
   }

   public static final class Builder {
      private final List<String> formatParts = new ArrayList<>();
      private final List<Object> args = new ArrayList<>();

      private Builder() {
      }

      public boolean isEmpty() {
         return this.formatParts.isEmpty();
      }

      public CodeBlock.Builder addNamed(String format, Map<String, ?> arguments) {
         int p = 0;

         for (String argument : arguments.keySet()) {
            Util.checkArgument(CodeBlock.LOWERCASE.matcher(argument).matches(), "argument '%s' must start with a lowercase character", argument);
         }

         while (p < format.length()) {
            int nextP = format.indexOf("$", p);
            if (nextP == -1) {
               this.formatParts.add(format.substring(p));
               break;
            }

            if (p != nextP) {
               this.formatParts.add(format.substring(p, nextP));
               p = nextP;
            }

            Matcher matcher = null;
            int colon = format.indexOf(58, p);
            if (colon != -1) {
               int endIndex = Math.min(colon + 2, format.length());
               matcher = CodeBlock.NAMED_ARGUMENT.matcher(format.substring(p, endIndex));
            }

            if (matcher != null && matcher.lookingAt()) {
               String argumentName = matcher.group("argumentName");
               Util.checkArgument(arguments.containsKey(argumentName), "Missing named argument for $%s", argumentName);
               char formatChar = matcher.group("typeChar").charAt(0);
               this.addArgument(format, formatChar, arguments.get(argumentName));
               this.formatParts.add("$" + formatChar);
               p += matcher.regionEnd();
            } else {
               Util.checkArgument(p < format.length() - 1, "dangling $ at end");
               Util.checkArgument(this.isNoArgPlaceholder(format.charAt(p + 1)), "unknown format $%s at %s in '%s'", format.charAt(p + 1), p + 1, format);
               this.formatParts.add(format.substring(p, p + 2));
               p += 2;
            }
         }

         return this;
      }

      public CodeBlock.Builder add(CodeBlock codeBlock) {
         this.formatParts.addAll(codeBlock.formatParts);
         this.args.addAll(codeBlock.args);
         return this;
      }

      public CodeBlock.Builder add(String format, Object... args) {
         boolean hasRelative = false;
         boolean hasIndexed = false;
         int relativeParameterCount = 0;
         int[] indexedParameterCount = new int[args.length];
         int p = 0;

         while (p < format.length()) {
            if (format.charAt(p) != '$') {
               int nextP = format.indexOf(36, p + 1);
               if (nextP == -1) {
                  nextP = format.length();
               }

               this.formatParts.add(format.substring(p, nextP));
               p = nextP;
            } else {
               int indexStart = ++p;

               char c;
               do {
                  Util.checkArgument(p < format.length(), "dangling format characters in '%s'", format);
                  c = format.charAt(p++);
               } while (c >= '0' && c <= '9');

               int indexEnd = p - 1;
               if (this.isNoArgPlaceholder(c)) {
                  Util.checkArgument(indexStart == indexEnd, "$$, $>, $<, $[, $], $W, and $Z may not have an index");
                  this.formatParts.add("$" + c);
               } else {
                  int index;
                  if (indexStart < indexEnd) {
                     index = Integer.parseInt(format.substring(indexStart, indexEnd)) - 1;
                     hasIndexed = true;
                     if (args.length > 0) {
                        indexedParameterCount[index % args.length]++;
                     }
                  } else {
                     index = relativeParameterCount;
                     hasRelative = true;
                     relativeParameterCount++;
                  }

                  Util.checkArgument(
                     index >= 0 && index < args.length,
                     "index %d for '%s' not in range (received %s arguments)",
                     index + 1,
                     format.substring(indexStart - 1, indexEnd + 1),
                     args.length
                  );
                  Util.checkArgument(!hasIndexed || !hasRelative, "cannot mix indexed and positional parameters");
                  this.addArgument(format, c, args[index]);
                  this.formatParts.add("$" + c);
               }
            }
         }

         if (hasRelative) {
            Util.checkArgument(relativeParameterCount >= args.length, "unused arguments: expected %s, received %s", relativeParameterCount, args.length);
         }

         if (hasIndexed) {
            List<String> unused = new ArrayList<>();

            for (int i = 0; i < args.length; i++) {
               if (indexedParameterCount[i] == 0) {
                  unused.add("$" + (i + 1));
               }
            }

            String s = unused.size() == 1 ? "" : "s";
            Util.checkArgument(unused.isEmpty(), "unused argument%s: %s", s, String.join(", ", unused));
         }

         Util.checkArgument(hasRelative || hasIndexed || args.length <= 0, "unused arguments: expected %s, received %s", 0, args.length);
         return this;
      }

      private boolean isNoArgPlaceholder(char c) {
         return c == '$' || c == '>' || c == '<' || c == '[' || c == ']' || c == 'W' || c == 'Z';
      }

      private void addArgument(String format, char c, Object arg) {
         switch (c) {
            case 'L':
               this.args.add(this.argToLiteral(arg));
               break;
            case 'M':
            case 'O':
            case 'P':
            case 'Q':
            case 'R':
            default:
               throw new IllegalArgumentException(String.format("invalid format string: '%s'", format));
            case 'N':
               this.args.add(this.argToName(arg));
               break;
            case 'S':
               this.args.add(this.argToString(arg));
               break;
            case 'T':
               this.args.add(this.argToType(arg));
         }
      }

      private String argToName(Object o) {
         if (o instanceof CharSequence charSequence) {
            return charSequence.toString();
         } else if (o instanceof ParameterSpec parameterSpec) {
            return parameterSpec.name();
         } else if (o instanceof FieldSpec fieldSpec) {
            return fieldSpec.name();
         } else if (o instanceof MethodSpec methodSpec) {
            return methodSpec.name();
         } else if (o instanceof TypeSpec typeSpec) {
            return typeSpec.name();
         } else {
            throw new IllegalArgumentException("expected name but was " + o);
         }
      }

      private Object argToLiteral(Object o) {
         return o;
      }

      private String argToString(Object o) {
         return o != null ? String.valueOf(o) : null;
      }

      private TypeName argToType(Object o) {
         if (o instanceof TypeName typeName) {
            return (TypeName)o;
         } else if (o instanceof TypeMirror typeMirror) {
            return TypeName.get(typeMirror);
         } else if (o instanceof Element element) {
            return TypeName.get(element.asType());
         } else if (o instanceof Type type) {
            return TypeName.get(type);
         } else {
            throw new IllegalArgumentException("expected type but was " + o);
         }
      }

      public CodeBlock.Builder beginControlFlow(String controlFlow, Object... args) {
         this.add(controlFlow + " {\n", args);
         this.indent();
         return this;
      }

      public CodeBlock.Builder nextControlFlow(String controlFlow, Object... args) {
         this.unindent();
         this.add("} " + controlFlow + " {\n", args);
         this.indent();
         return this;
      }

      public CodeBlock.Builder endControlFlow() {
         this.unindent();
         this.add("}\n");
         return this;
      }

      public CodeBlock.Builder endControlFlow(String controlFlow, Object... args) {
         this.unindent();
         this.add("} " + controlFlow + ";\n", args);
         return this;
      }

      public CodeBlock.Builder addStatement(String format, Object... args) {
         this.add("$[");
         this.add(format, args);
         this.add(";\n$]");
         return this;
      }

      public CodeBlock.Builder addStatement(CodeBlock codeBlock) {
         return this.addStatement("$L", codeBlock);
      }

      public CodeBlock.Builder indent() {
         this.formatParts.add("$>");
         return this;
      }

      public CodeBlock.Builder unindent() {
         this.formatParts.add("$<");
         return this;
      }

      public CodeBlock.Builder clear() {
         this.formatParts.clear();
         this.args.clear();
         return this;
      }

      public CodeBlock build() {
         return new CodeBlock(this);
      }
   }

   private static final class CodeBlockJoiner {
      private final String delimiter;
      private final CodeBlock.Builder builder;
      private boolean first = true;

      CodeBlockJoiner(String delimiter, CodeBlock.Builder builder) {
         this.delimiter = delimiter;
         this.builder = builder;
      }

      CodeBlock.CodeBlockJoiner add(CodeBlock codeBlock) {
         if (!this.first) {
            this.builder.add(this.delimiter);
         }

         this.first = false;
         this.builder.add(codeBlock);
         return this;
      }

      CodeBlock.CodeBlockJoiner merge(CodeBlock.CodeBlockJoiner other) {
         CodeBlock otherBlock = other.builder.build();
         if (!otherBlock.isEmpty()) {
            this.add(otherBlock);
         }

         return this;
      }

      CodeBlock join() {
         return this.builder.build();
      }
   }
}
