package org.springframework.web.util.pattern;

import java.text.MessageFormat;

public class PatternParseException extends IllegalArgumentException {
   private final int position;
   private final char[] pattern;
   private final PatternParseException.PatternMessage messageType;
   private final Object[] inserts;

   PatternParseException(int pos, char[] pattern, PatternParseException.PatternMessage messageType, Object... inserts) {
      super(messageType.formatMessage(inserts));
      this.position = pos;
      this.pattern = pattern;
      this.messageType = messageType;
      this.inserts = inserts;
   }

   PatternParseException(Throwable cause, int pos, char[] pattern, PatternParseException.PatternMessage messageType, Object... inserts) {
      super(messageType.formatMessage(inserts), cause);
      this.position = pos;
      this.pattern = pattern;
      this.messageType = messageType;
      this.inserts = inserts;
   }

   @Override
   public String getMessage() {
      return this.messageType.formatMessage(this.inserts);
   }

   public String toDetailedString() {
      StringBuilder sb = new StringBuilder();
      sb.append(this.pattern).append('\n');
      sb.append(" ".repeat(Math.max(0, this.position)));
      sb.append("^\n");
      sb.append(this.getMessage());
      return sb.toString();
   }

   public int getPosition() {
      return this.position;
   }

   public PatternParseException.PatternMessage getMessageType() {
      return this.messageType;
   }

   public Object[] getInserts() {
      return this.inserts;
   }

   public enum PatternMessage {
      MISSING_CLOSE_CAPTURE("Expected close capture character after variable name '}'"),
      MISSING_OPEN_CAPTURE("Missing preceding open capture character before variable name'{'"),
      ILLEGAL_NESTED_CAPTURE("Not allowed to nest variable captures"),
      CANNOT_HAVE_ADJACENT_CAPTURES("Adjacent captures are not allowed"),
      ILLEGAL_CHARACTER_AT_START_OF_CAPTURE_DESCRIPTOR("Char ''{0}'' not allowed at start of captured variable name"),
      ILLEGAL_CHARACTER_IN_CAPTURE_DESCRIPTOR("Char ''{0}'' is not allowed in a captured variable name"),
      CANNOT_HAVE_MANY_MULTISEGMENT_PATHELEMENTS("Multiple '{*...}' or '**' pattern elements are not allowed"),
      INVALID_LOCATION_FOR_MULTISEGMENT_PATHELEMENT("'{*...}' or '**' pattern elements should be placed at the start or end of the pattern"),
      BADLY_FORMED_CAPTURE_THE_REST("Expected form when capturing the rest of the path is simply '{*...}'"),
      MISSING_REGEX_CONSTRAINT("Missing regex constraint on capture"),
      ILLEGAL_DOUBLE_CAPTURE("Not allowed to capture ''{0}'' twice in the same pattern"),
      REGEX_PATTERN_SYNTAX_EXCEPTION("Exception occurred in regex pattern compilation"),
      CAPTURE_ALL_IS_STANDALONE_CONSTRUCT("'{*...}' cannot be mixed with other path elements in the same path segment");

      private final String message;

      PatternMessage(String message) {
         this.message = message;
      }

      public String formatMessage(Object... inserts) {
         return MessageFormat.format(this.message, inserts);
      }
   }
}
