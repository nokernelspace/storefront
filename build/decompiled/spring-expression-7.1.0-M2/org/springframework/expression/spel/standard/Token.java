package org.springframework.expression.spel.standard;

import org.jspecify.annotations.Nullable;

class Token {
   final TokenKind kind;
   final @Nullable String data;
   final int startPos;
   final int endPos;

   Token(TokenKind tokenKind, int startPos, int endPos) {
      this(tokenKind, null, startPos, endPos);
   }

   Token(TokenKind tokenKind, char @Nullable [] tokenData, int startPos, int endPos) {
      this.kind = tokenKind;
      this.data = tokenData != null ? new String(tokenData) : null;
      this.startPos = startPos;
      this.endPos = endPos;
   }

   public TokenKind getKind() {
      return this.kind;
   }

   public boolean isIdentifier() {
      return this.kind == TokenKind.IDENTIFIER;
   }

   public boolean isNumericRelationalOperator() {
      return this.kind == TokenKind.GT
         || this.kind == TokenKind.GE
         || this.kind == TokenKind.LT
         || this.kind == TokenKind.LE
         || this.kind == TokenKind.EQ
         || this.kind == TokenKind.NE;
   }

   public String stringValue() {
      return this.data != null ? this.data : "";
   }

   public Token asInstanceOfToken() {
      return new Token(TokenKind.INSTANCEOF, this.startPos, this.endPos);
   }

   public Token asMatchesToken() {
      return new Token(TokenKind.MATCHES, this.startPos, this.endPos);
   }

   public Token asBetweenToken() {
      return new Token(TokenKind.BETWEEN, this.startPos, this.endPos);
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append('[').append(this.kind);
      if (this.kind.hasPayload()) {
         sb.append(':').append(this.data);
      }

      sb.append(']');
      sb.append('(').append(this.startPos).append(',').append(this.endPos).append(')');
      return sb.toString();
   }
}
