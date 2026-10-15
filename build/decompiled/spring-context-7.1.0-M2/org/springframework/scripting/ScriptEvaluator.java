package org.springframework.scripting;

import java.util.Map;
import org.jspecify.annotations.Nullable;

public interface ScriptEvaluator {
   @Nullable Object evaluate(ScriptSource script) throws ScriptCompilationException;

   @Nullable Object evaluate(ScriptSource script, @Nullable Map<String, Object> arguments) throws ScriptCompilationException;
}
