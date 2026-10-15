package org.springframework.expression.spel;

import org.jspecify.annotations.Nullable;
import org.springframework.expression.EvaluationException;

public class SpelEvaluationException extends EvaluationException {
   private final SpelMessage message;
   private final @Nullable Object[] inserts;

   public SpelEvaluationException(SpelMessage message, @Nullable Object... inserts) {
      super(message.formatMessage(inserts));
      this.message = message;
      this.inserts = inserts;
   }

   public SpelEvaluationException(int position, SpelMessage message, @Nullable Object... inserts) {
      super(position, message.formatMessage(inserts));
      this.message = message;
      this.inserts = inserts;
   }

   public SpelEvaluationException(int position, @Nullable Throwable cause, SpelMessage message, @Nullable Object... inserts) {
      super(position, message.formatMessage(inserts), cause);
      this.message = message;
      this.inserts = inserts;
   }

   public SpelEvaluationException(@Nullable Throwable cause, SpelMessage message, @Nullable Object... inserts) {
      super(message.formatMessage(inserts), cause);
      this.message = message;
      this.inserts = inserts;
   }

   public void setPosition(int position) {
      this.position = position;
   }

   public SpelMessage getMessageCode() {
      return this.message;
   }

   public @Nullable Object @Nullable [] getInserts() {
      return this.inserts;
   }
}
