package org.springframework.aot.generate;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.StringJoiner;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.InputStreamSource;
import org.springframework.util.Assert;
import org.springframework.util.function.ThrowingConsumer;

public final class GeneratedResource {
   private final String path;
   private final GeneratedResource.Content content;

   GeneratedResource(String path) {
      this.path = path;
      this.content = new GeneratedResource.DefaultContent();
   }

   public String getPath() {
      return this.path;
   }

   public boolean hasContent() {
      return this.content.exists();
   }

   public void handle(Consumer<GeneratedResource.Content> content) {
      content.accept(this.content);
   }

   void writeTo(GeneratedFiles generatedFiles) {
      if (this.hasContent()) {
         generatedFiles.addFile(GeneratedFiles.Kind.RESOURCE, this.path, this.content);
      }
   }

   @Override
   public String toString() {
      return new StringJoiner(", ", GeneratedResource.class.getSimpleName() + "[", "]").add("path='" + this.path + "'").toString();
   }

   public interface Content extends InputStreamSource {
      boolean exists();

      void create(CharSequence content);

      void createOrValidate(CharSequence content);

      void create(ThrowingConsumer<Appendable> content);

      void createOrValidate(ThrowingConsumer<Appendable> content);

      void create(InputStreamSource content);

      void createOrValidate(InputStreamSource content);
   }

   private final class DefaultContent implements GeneratedResource.Content {
      private @Nullable InputStreamSource source;

      @Override
      public InputStream getInputStream() throws IOException {
         Assert.state(this.source != null, "No content is set for " + GeneratedResource.this);
         return this.source.getInputStream();
      }

      @Override
      public boolean exists() {
         return this.source != null;
      }

      @Override
      public void create(CharSequence content) {
         this.create(appendable -> appendable.append(content));
      }

      @Override
      public void createOrValidate(CharSequence content) {
         this.createOrValidate(appendable -> appendable.append(content));
      }

      @Override
      public void create(ThrowingConsumer<Appendable> content) {
         this.create(new AppendableConsumerInputStreamSource(content));
      }

      @Override
      public void createOrValidate(ThrowingConsumer<Appendable> content) {
         this.createOrValidate(new AppendableConsumerInputStreamSource(content));
      }

      @Override
      public void create(InputStreamSource content) {
         Assert.notNull(content, "'content' must not be null");
         if (this.exists()) {
            throw new IllegalStateException("Content for generated resource at '%s' has already been set".formatted(GeneratedResource.this.getPath()));
         }

         this.source = content;
      }

      @Override
      public void createOrValidate(InputStreamSource content) {
         Assert.notNull(content, "'content' must not be null");
         if (this.source == null) {
            this.source = content;
         } else {
            if (!this.hasSameContentAs(content)) {
               throw new IllegalArgumentException(
                  "Content for generated resource at '%s' differs from the content that has already been written".formatted(GeneratedResource.this.getPath())
               );
            }
         }
      }

      private boolean hasSameContentAs(InputStreamSource content) {
         try (
            InputStream input1 = this.getInputStream();
            InputStream input2 = content.getInputStream();
         ) {
            if (input1 == input2) {
               return true;
            }

            byte[] buffer1 = new byte[8192];
            byte[] buffer2 = new byte[8192];

            int count1;
            while ((count1 = input1.read(buffer1)) != -1) {
               int count2 = input2.read(buffer2);
               if (count1 != count2) {
                  return false;
               }

               if (!Arrays.equals(buffer1, 0, count1, buffer2, 0, count2)) {
                  return false;
               }
            }

            return input2.read() == -1;
         } catch (IOException ex) {
            throw new RuntimeException("Failed to validate content for generated resource at '%s'".formatted(GeneratedResource.this.getPath()), ex);
         }
      }
   }
}
