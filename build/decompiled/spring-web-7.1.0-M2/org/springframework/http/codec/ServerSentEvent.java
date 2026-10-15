package org.springframework.http.codec;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ObjectUtils;
import org.springframework.web.util.SseUtils;

public final class ServerSentEvent<T> {
   private final @Nullable String id;
   private final @Nullable String event;
   private final @Nullable Duration retry;
   private final @Nullable String comment;
   private final @Nullable T data;

   private ServerSentEvent(@Nullable String id, @Nullable String event, @Nullable Duration retry, @Nullable String comment, @Nullable T data) {
      this.id = id;
      this.event = event;
      this.retry = retry;
      this.comment = comment;
      this.data = data;
   }

   public @Nullable String id() {
      return this.id;
   }

   public @Nullable String event() {
      return this.event;
   }

   public @Nullable Duration retry() {
      return this.retry;
   }

   public @Nullable String comment() {
      return this.comment;
   }

   public @Nullable T data() {
      return this.data;
   }

   public String format() {
      StringBuilder sb = new StringBuilder();
      if (this.id != null) {
         this.appendAttribute("id", this.id, sb);
      }

      if (this.event != null) {
         this.appendAttribute("event", this.event, sb);
      }

      if (this.retry != null) {
         this.appendAttribute("retry", this.retry.toMillis(), sb);
      }

      if (this.comment != null) {
         sb.append(':');
         SseUtils.appendFieldValue("", this.comment, sb);
         sb.append('\n');
      }

      if (this.data != null) {
         sb.append("data: ");
      }

      return sb.toString();
   }

   private void appendAttribute(String fieldName, Object fieldValue, StringBuilder sb) {
      sb.append(fieldName).append(':').append(fieldValue).append('\n');
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof ServerSentEvent<?> that
            && ObjectUtils.nullSafeEquals(this.id, that.id)
            && ObjectUtils.nullSafeEquals(this.event, that.event)
            && ObjectUtils.nullSafeEquals(this.retry, that.retry)
            && ObjectUtils.nullSafeEquals(this.comment, that.comment)
            && ObjectUtils.nullSafeEquals(this.data, that.data);
   }

   @Override
   public int hashCode() {
      return ObjectUtils.nullSafeHash(new Object[]{this.id, this.event, this.retry, this.comment, this.data});
   }

   @Override
   public String toString() {
      return "ServerSentEvent [id = '"
         + this.id
         + "', event='"
         + this.event
         + "', retry="
         + this.retry
         + ", comment='"
         + this.comment
         + "', data="
         + this.data
         + "]";
   }

   public static <T> ServerSentEvent.Builder<T> builder() {
      return new ServerSentEvent.BuilderImpl<>();
   }

   public static <T> ServerSentEvent.Builder<T> builder(T data) {
      return new ServerSentEvent.BuilderImpl<>(data);
   }

   public interface Builder<T> {
      ServerSentEvent.Builder<T> id(String id);

      ServerSentEvent.Builder<T> event(String event);

      ServerSentEvent.Builder<T> retry(Duration retry);

      ServerSentEvent.Builder<T> comment(String comment);

      ServerSentEvent.Builder<T> data(@Nullable T data);

      ServerSentEvent<T> build();
   }

   private static class BuilderImpl<T> implements ServerSentEvent.Builder<T> {
      private @Nullable String id;
      private @Nullable String event;
      private @Nullable Duration retry;
      private @Nullable String comment;
      private @Nullable T data;

      public BuilderImpl() {
      }

      public BuilderImpl(T data) {
         this.data = data;
      }

      @Override
      public ServerSentEvent.Builder<T> id(String id) {
         SseUtils.assertNoLineSeparator(id);
         this.id = id;
         return this;
      }

      @Override
      public ServerSentEvent.Builder<T> event(String event) {
         SseUtils.assertNoLineSeparator(event);
         this.event = event;
         return this;
      }

      @Override
      public ServerSentEvent.Builder<T> retry(Duration retry) {
         this.retry = retry;
         return this;
      }

      @Override
      public ServerSentEvent.Builder<T> comment(String comment) {
         this.comment = comment;
         return this;
      }

      @Override
      public ServerSentEvent.Builder<T> data(@Nullable T data) {
         this.data = data;
         return this;
      }

      @Override
      public ServerSentEvent<T> build() {
         return new ServerSentEvent<>(this.id, this.event, this.retry, this.comment, this.data);
      }
   }
}
