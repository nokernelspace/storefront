package org.springframework.http;

import java.io.Serializable;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public class ProblemDetail implements Serializable {
   private static final long serialVersionUID = 3307761915842206538L;
   private @Nullable URI type;
   private @Nullable String title;
   private int status;
   private @Nullable String detail;
   private @Nullable URI instance;
   private @Nullable Map<String, Object> properties;

   protected ProblemDetail(int rawStatusCode) {
      this.status = rawStatusCode;
   }

   protected ProblemDetail(ProblemDetail other) {
      this.type = other.type;
      this.title = other.title;
      this.status = other.status;
      this.detail = other.detail;
      this.instance = other.instance;
      this.properties = other.properties != null ? new LinkedHashMap<>(other.properties) : null;
   }

   protected ProblemDetail() {
   }

   public void setType(@Nullable URI type) {
      this.type = type;
   }

   public @Nullable URI getType() {
      return this.type;
   }

   public void setTitle(@Nullable String title) {
      this.title = title;
   }

   public @Nullable String getTitle() {
      if (this.title == null) {
         HttpStatus httpStatus = HttpStatus.resolve(this.status);
         if (httpStatus != null) {
            return httpStatus.getReasonPhrase();
         }
      }

      return this.title;
   }

   public void setStatus(HttpStatus httpStatus) {
      this.status = httpStatus.value();
   }

   public void setStatus(int status) {
      this.status = status;
   }

   public int getStatus() {
      return this.status;
   }

   public void setDetail(@Nullable String detail) {
      this.detail = detail;
   }

   public @Nullable String getDetail() {
      return this.detail;
   }

   public void setInstance(@Nullable URI instance) {
      this.instance = instance;
   }

   public @Nullable URI getInstance() {
      return this.instance;
   }

   public void setProperty(String name, @Nullable Object value) {
      this.properties = this.properties != null ? this.properties : new LinkedHashMap<>();
      this.properties.put(name, value);
   }

   public void setProperties(@Nullable Map<String, Object> properties) {
      this.properties = properties;
   }

   public @Nullable Map<String, Object> getProperties() {
      return this.properties;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof ProblemDetail that
            && ObjectUtils.nullSafeEquals(this.getType(), that.getType())
            && ObjectUtils.nullSafeEquals(this.getTitle(), that.getTitle())
            && this.status == that.status
            && ObjectUtils.nullSafeEquals(this.detail, that.detail)
            && ObjectUtils.nullSafeEquals(this.instance, that.instance)
            && ObjectUtils.nullSafeEquals(this.properties, that.properties);
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.type, this.getTitle(), this.status, this.detail, this.instance, this.properties);
   }

   @Override
   public String toString() {
      return this.getClass().getSimpleName() + "[" + this.initToStringContent() + "]";
   }

   protected String initToStringContent() {
      return "type='"
         + this.getType()
         + "', title='"
         + this.getTitle()
         + "', status="
         + this.getStatus()
         + ", detail='"
         + this.getDetail()
         + "', instance='"
         + this.getInstance()
         + "', properties='"
         + this.getProperties()
         + "'";
   }

   public static ProblemDetail forStatus(HttpStatusCode status) {
      Assert.notNull(status, "HttpStatusCode is required");
      return forStatus(status.value());
   }

   public static ProblemDetail forStatus(int status) {
      return new ProblemDetail(status);
   }

   public static ProblemDetail forStatusAndDetail(HttpStatusCode status, @Nullable String detail) {
      Assert.notNull(status, "HttpStatusCode is required");
      ProblemDetail problemDetail = forStatus(status.value());
      problemDetail.setDetail(detail);
      return problemDetail;
   }
}
