package org.springframework.boot.ssl.pem;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

public final class PemContent {
   private static final Pattern PEM_HEADER = Pattern.compile("-+BEGIN\\s+[^-]*-+", 2);
   private static final Pattern PEM_FOOTER = Pattern.compile("-+END\\s+[^-]*-+", 2);
   private final String text;

   private PemContent(String text) {
      this.text = text.lines().map(String::trim).collect(Collectors.joining("\n"));
   }

   public List<X509Certificate> getCertificates() {
      return PemCertificateParser.parse(this.text);
   }

   public @Nullable PrivateKey getPrivateKey() {
      return this.getPrivateKey(null);
   }

   public @Nullable PrivateKey getPrivateKey(@Nullable String password) {
      return PemPrivateKeyParser.parse(this.text, password);
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else {
         return obj != null && this.getClass() == obj.getClass() ? Objects.equals(this.text, ((PemContent)obj).text) : false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.text);
   }

   @Override
   public String toString() {
      return this.text;
   }

   static @Nullable PemContent load(@Nullable String content, ResourceLoader resourceLoader) throws IOException {
      if (!StringUtils.hasLength(content)) {
         return null;
      }

      if (isPresentInText(content)) {
         return new PemContent(content);
      }

      try (InputStream in = resourceLoader.getResource(content).getInputStream()) {
         return load(in);
      } catch (IOException | UncheckedIOException ex) {
         throw new IOException("Error reading certificate or key from file '%s'".formatted(content), ex);
      }
   }

   public static PemContent load(Path path) throws IOException {
      Assert.notNull(path, "'path' must not be null");

      try (InputStream in = Files.newInputStream(path, StandardOpenOption.READ)) {
         return load(in);
      }
   }

   public static PemContent load(InputStream in) throws IOException {
      return of(StreamUtils.copyToString(in, StandardCharsets.UTF_8));
   }

   @Contract("!null -> !null")
   public static @Nullable PemContent of(@Nullable String text) {
      return text != null ? new PemContent(text) : null;
   }

   public static boolean isPresentInText(@Nullable String text) {
      return text != null && PEM_HEADER.matcher(text).find() && PEM_FOOTER.matcher(text).find();
   }
}
