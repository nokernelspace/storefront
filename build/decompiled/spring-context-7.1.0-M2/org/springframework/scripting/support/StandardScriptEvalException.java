package org.springframework.scripting.support;

import javax.script.ScriptException;

public class StandardScriptEvalException extends RuntimeException {
   private final ScriptException scriptException;

   public StandardScriptEvalException(ScriptException ex) {
      super(ex.getMessage());
      this.scriptException = ex;
   }

   public final ScriptException getScriptException() {
      return this.scriptException;
   }

   @Override
   public synchronized Throwable fillInStackTrace() {
      return this;
   }
}
