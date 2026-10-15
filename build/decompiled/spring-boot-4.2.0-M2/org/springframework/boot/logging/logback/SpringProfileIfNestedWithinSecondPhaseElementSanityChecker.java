package org.springframework.boot.logging.logback;

import ch.qos.logback.classic.model.LoggerModel;
import ch.qos.logback.classic.model.RootLoggerModel;
import ch.qos.logback.core.joran.sanity.Pair;
import ch.qos.logback.core.joran.sanity.SanityChecker;
import ch.qos.logback.core.model.AppenderModel;
import ch.qos.logback.core.model.Model;
import ch.qos.logback.core.spi.ContextAwareBase;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

class SpringProfileIfNestedWithinSecondPhaseElementSanityChecker extends ContextAwareBase implements SanityChecker {
   private static final List<Class<? extends Model>> SECOND_PHASE_TYPES = List.of(AppenderModel.class, LoggerModel.class, RootLoggerModel.class);

   public void check(@Nullable Model model) {
      if (model != null) {
         List<Model> models = new ArrayList<>();
         SECOND_PHASE_TYPES.forEach(type -> this.deepFindAllModelsOfType(type, models, model));
         List<Pair<Model, Model>> nestedPairs = this.deepFindNestedSubModelsOfType(SpringProfileModel.class, models);
         if (!nestedPairs.isEmpty()) {
            this.addWarn("<springProfile> elements cannot be nested within an <appender>, <logger> or <root> element");
            nestedPairs.forEach(
               nested -> {
                  Model first = (Model)nested.first;
                  Model second = (Model)nested.second;
                  this.addWarn(
                     "Element <%s> at line %s contains a nested <%s> element at line %s"
                        .formatted(first.getTag(), first.getLineNumber(), second.getTag(), second.getLineNumber())
                  );
               }
            );
         }
      }
   }
}
