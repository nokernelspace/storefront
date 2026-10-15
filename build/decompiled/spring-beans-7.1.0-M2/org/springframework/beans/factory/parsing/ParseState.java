package org.springframework.beans.factory.parsing;

import java.util.ArrayDeque;
import org.jspecify.annotations.Nullable;

public final class ParseState {
   private final ArrayDeque<ParseState.Entry> state;

   public ParseState() {
      this.state = new ArrayDeque<>();
   }

   private ParseState(ParseState other) {
      this.state = other.state.clone();
   }

   public void push(ParseState.Entry entry) {
      this.state.push(entry);
   }

   public void pop() {
      this.state.pop();
   }

   public ParseState.@Nullable Entry peek() {
      return this.state.peek();
   }

   public ParseState snapshot() {
      return new ParseState(this);
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder(64);
      int i = 0;

      for (ParseState.Entry entry : this.state) {
         if (i > 0) {
            sb.append('\n');
            sb.append("\t".repeat(i));
            sb.append("-> ");
         }

         sb.append(entry);
         i++;
      }

      return sb.toString();
   }

   public interface Entry {
   }
}
