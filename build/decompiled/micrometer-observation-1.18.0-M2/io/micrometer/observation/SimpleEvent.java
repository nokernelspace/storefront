package io.micrometer.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

final class SimpleEvent implements Observation.Event {
   private final String name;
   private final String contextualName;
   private final long wallTime;
   private final KeyValues lowCardinalityKeyValues;
   private final KeyValues highCardinalityKeyValues;

   SimpleEvent(String name, String contextualName) {
      this(name, contextualName, System.currentTimeMillis(), KeyValues.empty(), KeyValues.empty());
   }

   SimpleEvent(String name, String contextualName, long wallTime, Iterable<KeyValue> highCardinalityKeyValues, Iterable<KeyValue> lowCardinalityKeyValues) {
      this.name = name;
      this.contextualName = contextualName;
      this.wallTime = wallTime;
      this.highCardinalityKeyValues = KeyValues.of(highCardinalityKeyValues);
      this.lowCardinalityKeyValues = KeyValues.of(lowCardinalityKeyValues);
   }

   @Override
   public String getName() {
      return this.name;
   }

   @Override
   public String getContextualName() {
      return this.contextualName;
   }

   @Override
   public long getWallTime() {
      return this.wallTime;
   }

   @Override
   public KeyValues getLowCardinalityKeyValues() {
      return this.lowCardinalityKeyValues;
   }

   @Override
   public KeyValues getHighCardinalityKeyValues() {
      return this.highCardinalityKeyValues;
   }

   @Override
   public KeyValues getKeyValues() {
      return this.lowCardinalityKeyValues.and(this.highCardinalityKeyValues);
   }

   @Override
   public String toString() {
      return "event.name='" + this.getName() + "', event.contextualName='" + this.getContextualName() + "', event.wallTime=" + this.getWallTime();
   }
}
