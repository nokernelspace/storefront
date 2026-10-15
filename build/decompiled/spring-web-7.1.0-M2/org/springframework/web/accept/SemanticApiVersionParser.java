package org.springframework.web.accept;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class SemanticApiVersionParser implements ApiVersionParser<SemanticApiVersionParser.Version> {
   private static final Pattern semanticVersionPattern = Pattern.compile("^(\\d+)(\\.(\\d+))?(\\.(\\d+))?$");

   public SemanticApiVersionParser.Version parseVersion(String version) {
      Assert.notNull(version, "'version' is required");
      version = skipNonDigits(version);
      Matcher matcher = semanticVersionPattern.matcher(version);
      Assert.state(matcher.matches(), "Invalid API version format");
      String major = matcher.group(1);
      String minor = matcher.group(3);
      String patch = matcher.group(5);
      return new SemanticApiVersionParser.Version(
         Integer.parseInt(major), minor != null ? Integer.parseInt(minor) : 0, patch != null ? Integer.parseInt(patch) : 0
      );
   }

   private static String skipNonDigits(String value) {
      for (int i = 0; i < value.length(); i++) {
         if (Character.isDigit(value.charAt(i))) {
            return value.substring(i);
         }
      }

      return "";
   }

   public static final class Version implements Comparable<SemanticApiVersionParser.Version> {
      private final int major;
      private final int minor;
      private final int patch;

      Version(int major, int minor, int patch) {
         this.major = major;
         this.minor = minor;
         this.patch = patch;
      }

      public int getMajor() {
         return this.major;
      }

      public int getMinor() {
         return this.minor;
      }

      public int getPatch() {
         return this.patch;
      }

      public int compareTo(SemanticApiVersionParser.Version other) {
         int result = Integer.compare(this.major, other.major);
         if (result != 0) {
            return result;
         }

         result = Integer.compare(this.minor, other.minor);
         return result != 0 ? result : Integer.compare(this.patch, other.patch);
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other
            || other instanceof SemanticApiVersionParser.Version otherVersion
               && this.major == otherVersion.major
               && this.minor == otherVersion.minor
               && this.patch == otherVersion.patch;
      }

      @Override
      public int hashCode() {
         int result = this.major;
         result = 31 * result + this.minor;
         return 31 * result + this.patch;
      }

      @Override
      public String toString() {
         return this.major + "." + this.minor + "." + this.patch;
      }
   }
}
