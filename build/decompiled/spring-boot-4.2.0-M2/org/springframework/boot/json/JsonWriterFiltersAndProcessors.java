package org.springframework.boot.json;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

record JsonWriterFiltersAndProcessors(
   List<Predicate<JsonWriter.MemberPath>> pathFilters, List<JsonWriter.NameProcessor> nameProcessors, List<JsonWriter.ValueProcessor<?>> valueProcessors
) {
   JsonWriterFiltersAndProcessors() {
      this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
   }
}
