package org.springframework.web.accept;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;

public class StandardApiVersionDeprecationHandler implements ApiVersionDeprecationHandler {
   private final ApiVersionParser<?> versionParser;
   private final Map<Comparable<?>, StandardApiVersionDeprecationHandler.VersionInfo> infos = new HashMap<>();

   public StandardApiVersionDeprecationHandler() {
      this(new SemanticApiVersionParser());
   }

   public StandardApiVersionDeprecationHandler(ApiVersionParser<?> parser) {
      this.versionParser = parser;
   }

   public StandardApiVersionDeprecationHandler.VersionSpec configureVersion(String version) {
      Comparable<?> parsedVersion = this.versionParser.parseVersion(version);
      return new StandardApiVersionDeprecationHandler.VersionSpec(parsedVersion);
   }

   @Override
   public void handleVersion(Comparable<?> requestVersion, Object handler, HttpServletRequest request, HttpServletResponse response) {
      for (StandardApiVersionDeprecationHandler.VersionInfo info : this.infos.values()) {
         if (info.match(requestVersion, request)) {
            if (info.deprecationDate() != null) {
               response.setHeader("Deprecation", info.deprecationDate());
            }

            if (info.deprecationLink() != null) {
               response.addHeader("Link", info.deprecationLink());
            }

            if (info.sunsetDate() != null) {
               response.setHeader("Sunset", info.sunsetDate());
            }

            if (info.sunsetLink() != null) {
               response.addHeader("Link", info.sunsetLink());
            }
         }
      }
   }

   @Override
   public String toString() {
      return "StandardApiVersionDeprecationHandler " + this.infos.values();
   }

   private record VersionInfo(
      Comparable<?> version,
      Predicate<HttpServletRequest> requestPredicate,
      @Nullable String deprecationDate,
      @Nullable String deprecationLink,
      @Nullable String sunsetDate,
      @Nullable String sunsetLink
   ) {
      VersionInfo(Comparable<?> version) {
         this(version, request -> true, null, null, null, null);
      }

      public StandardApiVersionDeprecationHandler.VersionInfo withRequestPredicate(Predicate<HttpServletRequest> predicate) {
         return new StandardApiVersionDeprecationHandler.VersionInfo(
            this.version(), predicate, this.deprecationDate(), this.deprecationLink(), this.sunsetDate(), this.sunsetLink()
         );
      }

      public StandardApiVersionDeprecationHandler.VersionInfo withDeprecationDate(ZonedDateTime deprecationDate) {
         return new StandardApiVersionDeprecationHandler.VersionInfo(
            this.version(),
            this.requestPredicate(),
            "@" + deprecationDate.toInstant().getEpochSecond(),
            this.deprecationLink(),
            this.sunsetDate(),
            this.sunsetLink()
         );
      }

      public StandardApiVersionDeprecationHandler.VersionInfo withDeprecationLink(URI uri, MediaType mediaType) {
         return new StandardApiVersionDeprecationHandler.VersionInfo(
            this.version(),
            this.requestPredicate(),
            this.deprecationDate(),
            String.format("<%s>; rel=\"deprecation\"; type=\"%s\"", uri, mediaType),
            this.sunsetDate(),
            this.sunsetLink()
         );
      }

      public StandardApiVersionDeprecationHandler.VersionInfo withSunsetDate(ZonedDateTime sunsetDate) {
         return new StandardApiVersionDeprecationHandler.VersionInfo(
            this.version(),
            this.requestPredicate(),
            this.deprecationDate(),
            this.deprecationLink(),
            sunsetDate.format(DateTimeFormatter.RFC_1123_DATE_TIME),
            this.sunsetLink()
         );
      }

      public StandardApiVersionDeprecationHandler.VersionInfo withSunsetLink(URI uri, MediaType mediaType) {
         return new StandardApiVersionDeprecationHandler.VersionInfo(
            this.version(),
            this.requestPredicate(),
            this.deprecationDate(),
            this.deprecationLink(),
            this.sunsetDate(),
            String.format("<%s>; rel=\"sunset\"; type=\"%s\"", uri, mediaType)
         );
      }

      boolean match(Comparable<?> requestVersion, HttpServletRequest request) {
         return this.version().equals(requestVersion) && this.requestPredicate().test(request);
      }
   }

   public final class VersionSpec {
      private final Comparable<?> version;

      private VersionSpec(Comparable<?> version) {
         this.version = version;
         StandardApiVersionDeprecationHandler.this.infos.put(version, new StandardApiVersionDeprecationHandler.VersionInfo(version));
      }

      public StandardApiVersionDeprecationHandler.VersionSpec setRequestPredicate(Predicate<HttpServletRequest> predicate) {
         return this.map(info -> info.withRequestPredicate(predicate));
      }

      public StandardApiVersionDeprecationHandler.VersionSpec setDeprecationDate(ZonedDateTime date) {
         return this.map(info -> info.withDeprecationDate(date));
      }

      public StandardApiVersionDeprecationHandler.VersionSpec setDeprecationLink(URI uri) {
         return this.setDeprecationLink(uri, MediaType.TEXT_HTML);
      }

      public StandardApiVersionDeprecationHandler.VersionSpec setDeprecationLink(URI uri, MediaType mediaType) {
         return this.map(info -> info.withDeprecationLink(uri, mediaType));
      }

      public StandardApiVersionDeprecationHandler.VersionSpec setSunsetDate(ZonedDateTime date) {
         return this.map(info -> info.withSunsetDate(date));
      }

      public StandardApiVersionDeprecationHandler.VersionSpec setSunsetLink(URI uri) {
         return this.setSunsetLink(uri, MediaType.TEXT_HTML);
      }

      public StandardApiVersionDeprecationHandler.VersionSpec setSunsetLink(URI uri, MediaType mediaType) {
         return this.map(info -> info.withSunsetLink(uri, mediaType));
      }

      private StandardApiVersionDeprecationHandler.VersionSpec map(
         Function<StandardApiVersionDeprecationHandler.VersionInfo, StandardApiVersionDeprecationHandler.VersionInfo> function
      ) {
         StandardApiVersionDeprecationHandler.this.infos.compute(this.version, (version, versionInfo) -> {
            Assert.state(versionInfo != null, "No VersionInfo");
            return function.apply(versionInfo);
         });
         return this;
      }
   }
}
