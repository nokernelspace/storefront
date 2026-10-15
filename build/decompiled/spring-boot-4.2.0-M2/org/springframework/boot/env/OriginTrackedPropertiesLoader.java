package org.springframework.boot.env;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.boot.origin.TextResourceOrigin;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

class OriginTrackedPropertiesLoader {
   private final Resource resource;

   OriginTrackedPropertiesLoader(Resource resource) {
      Assert.notNull(resource, "'resource' must not be null");
      this.resource = resource;
   }

   List<OriginTrackedPropertiesLoader.Document> load(@Nullable Charset encoding) throws IOException {
      return this.load(encoding, true);
   }

   List<OriginTrackedPropertiesLoader.Document> load(@Nullable Charset encoding, boolean expandLists) throws IOException {
      List<OriginTrackedPropertiesLoader.Document> documents = new ArrayList<>();
      OriginTrackedPropertiesLoader.Document document = new OriginTrackedPropertiesLoader.Document();
      StringBuilder buffer = new StringBuilder();
      OriginTrackedPropertiesLoader.CharacterReader reader = new OriginTrackedPropertiesLoader.CharacterReader(this.resource, encoding);

      try {
         while (reader.read()) {
            if (reader.isCommentPrefixCharacter()) {
               char commentPrefixCharacter = reader.getCharacter();
               if (this.isNewDocument(reader)) {
                  if (!document.isEmpty()) {
                     documents.add(document);
                  }

                  document = new OriginTrackedPropertiesLoader.Document();
               } else {
                  if (document.isEmpty() && !documents.isEmpty()) {
                     document = documents.remove(documents.size() - 1);
                  }

                  reader.setLastLineCommentPrefixCharacter(commentPrefixCharacter);
                  reader.skipComment();
               }
            } else {
               reader.setLastLineCommentPrefixCharacter(-1);
               this.loadKeyAndValue(expandLists, document, reader, buffer);
            }
         }
      } catch (Throwable var10) {
         try {
            reader.close();
         } catch (Throwable var9) {
            var10.addSuppressed(var9);
         }

         throw var10;
      }

      reader.close();
      if (!document.isEmpty() && !documents.contains(document)) {
         documents.add(document);
      }

      return documents;
   }

   private void loadKeyAndValue(
      boolean expandLists, OriginTrackedPropertiesLoader.Document document, OriginTrackedPropertiesLoader.CharacterReader reader, StringBuilder buffer
   ) throws IOException {
      String key = this.loadKey(buffer, reader).trim();
      if (expandLists && key.endsWith("[]")) {
         key = key.substring(0, key.length() - 2);
         int index = 0;

         do {
            OriginTrackedValue value = this.loadValue(buffer, reader, true);
            document.put(key + "[" + index++ + "]", value);
            if (!reader.isEndOfLine()) {
               reader.read();
            }
         } while (!reader.isEndOfLine());
      } else {
         OriginTrackedValue value = this.loadValue(buffer, reader, false);
         document.put(key, value);
      }
   }

   private String loadKey(StringBuilder buffer, OriginTrackedPropertiesLoader.CharacterReader reader) throws IOException {
      buffer.setLength(0);
      boolean previousWhitespace = false;

      while (!reader.isEndOfLine()) {
         if (reader.isPropertyDelimiter()) {
            reader.read();
            return buffer.toString();
         }

         if (!reader.isWhiteSpace() && previousWhitespace) {
            return buffer.toString();
         }

         previousWhitespace = reader.isWhiteSpace();
         buffer.append(reader.getCharacter());
         reader.read();
      }

      return buffer.toString();
   }

   private OriginTrackedValue loadValue(StringBuilder buffer, OriginTrackedPropertiesLoader.CharacterReader reader, boolean splitLists) throws IOException {
      buffer.setLength(0);

      while (reader.isWhiteSpace() && !reader.isEndOfLine()) {
         reader.read();
      }

      TextResourceOrigin.Location location = reader.getLocation();

      while (!reader.isEndOfLine() && (!splitLists || !reader.isListDelimiter())) {
         buffer.append(reader.getCharacter());
         reader.read();
      }

      Origin origin = new TextResourceOrigin(this.resource, location);
      return OriginTrackedValue.of(buffer.toString(), origin);
   }

   private boolean isNewDocument(OriginTrackedPropertiesLoader.CharacterReader reader) throws IOException {
      if (reader.isSameLastLineCommentPrefix()) {
         return false;
      }

      boolean result = reader.getLocation().getColumn() == 0;
      result = result && this.readAndExpect(reader, reader::isHyphenCharacter);
      result = result && this.readAndExpect(reader, reader::isHyphenCharacter);
      result = result && this.readAndExpect(reader, reader::isHyphenCharacter);
      if (!reader.isEndOfLine()) {
         reader.read();
         reader.skipWhitespace();
      }

      return result && reader.isEndOfLine();
   }

   private boolean readAndExpect(OriginTrackedPropertiesLoader.CharacterReader reader, BooleanSupplier check) throws IOException {
      reader.read();
      return check.getAsBoolean();
   }

   private static class CharacterReader implements Closeable {
      private static final String[] ESCAPES = new String[]{"trnf", "\t\r\n\f"};
      private final LineNumberReader reader;
      private int columnNumber = -1;
      private boolean escaped;
      private int character;
      private int lastLineCommentPrefixCharacter;

      CharacterReader(Resource resource, @Nullable Charset encoding) throws IOException {
         this.reader = new LineNumberReader(new InputStreamReader(resource.getInputStream(), encoding != null ? encoding : StandardCharsets.ISO_8859_1));
      }

      @Override
      public void close() throws IOException {
         this.reader.close();
      }

      boolean read() throws IOException {
         this.escaped = false;
         this.character = this.reader.read();
         this.columnNumber++;
         if (this.columnNumber == 0) {
            this.skipWhitespace();
         }

         if (this.character == 92) {
            this.escaped = true;
            this.readEscaped();
         } else if (this.character == 10) {
            this.columnNumber = -1;
         }

         return !this.isEndOfFile();
      }

      private void skipWhitespace() throws IOException {
         while (this.isWhiteSpace()) {
            this.character = this.reader.read();
            this.columnNumber++;
         }
      }

      private void setLastLineCommentPrefixCharacter(int lastLineCommentPrefixCharacter) {
         this.lastLineCommentPrefixCharacter = lastLineCommentPrefixCharacter;
      }

      private void skipComment() throws IOException {
         while (this.character != 10 && this.character != -1) {
            this.character = this.reader.read();
         }

         this.columnNumber = -1;
      }

      private void readEscaped() throws IOException {
         this.character = this.reader.read();
         int escapeIndex = ESCAPES[0].indexOf(this.character);
         if (escapeIndex != -1) {
            this.character = ESCAPES[1].charAt(escapeIndex);
         } else if (this.character == 10) {
            this.columnNumber = -1;
            this.read();
         } else if (this.character == 117) {
            this.readUnicode();
         }
      }

      private void readUnicode() throws IOException {
         this.character = 0;

         for (int i = 0; i < 4; i++) {
            int digit = this.reader.read();
            if (digit >= 48 && digit <= 57) {
               this.character = (this.character << 4) + digit - 48;
            } else if (digit >= 97 && digit <= 102) {
               this.character = (this.character << 4) + digit - 97 + 10;
            } else {
               if (digit < 65 || digit > 70) {
                  throw new IllegalStateException("Malformed \\uxxxx encoding.");
               }

               this.character = (this.character << 4) + digit - 65 + 10;
            }
         }
      }

      boolean isWhiteSpace() {
         return !this.escaped && (this.character == 32 || this.character == 9 || this.character == 12);
      }

      boolean isEndOfFile() {
         return this.character == -1;
      }

      boolean isEndOfLine() {
         return this.character == -1 || !this.escaped && this.character == 10;
      }

      boolean isListDelimiter() {
         return !this.escaped && this.character == 44;
      }

      boolean isPropertyDelimiter() {
         return !this.escaped && (this.character == 61 || this.character == 58);
      }

      char getCharacter() {
         return (char)this.character;
      }

      TextResourceOrigin.Location getLocation() {
         return new TextResourceOrigin.Location(this.reader.getLineNumber(), this.columnNumber);
      }

      boolean isSameLastLineCommentPrefix() {
         return this.lastLineCommentPrefixCharacter == this.character;
      }

      boolean isCommentPrefixCharacter() {
         return this.character == 35 || this.character == 33;
      }

      boolean isHyphenCharacter() {
         return this.character == 45;
      }
   }

   static class Document {
      private final Map<String, OriginTrackedValue> values = new LinkedHashMap<>();

      void put(String key, OriginTrackedValue value) {
         if (!key.isEmpty()) {
            this.values.put(key, value);
         }
      }

      boolean isEmpty() {
         return this.values.isEmpty();
      }

      Map<String, OriginTrackedValue> asMap() {
         return this.values;
      }
   }
}
