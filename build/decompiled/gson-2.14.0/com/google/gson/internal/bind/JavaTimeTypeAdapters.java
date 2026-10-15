package com.google.gson.internal.bind;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@IgnoreJRERequirement
final class JavaTimeTypeAdapters implements TypeAdapters.FactorySupplier {
   private static final TypeAdapter<Duration> DURATION = new TypeAdapters.IntegerFieldsTypeAdapter<Duration>("seconds", "nanos") {
      Duration create(long[] values) {
         return Duration.ofSeconds(values[0], values[1]);
      }

      long[] integerValues(Duration duration) {
         return new long[]{duration.getSeconds(), duration.getNano()};
      }
   };
   private static final TypeAdapter<Instant> INSTANT = new TypeAdapters.IntegerFieldsTypeAdapter<Instant>("seconds", "nanos") {
      Instant create(long[] values) {
         return Instant.ofEpochSecond(values[0], values[1]);
      }

      long[] integerValues(Instant instant) {
         return new long[]{instant.getEpochSecond(), instant.getNano()};
      }
   };
   private static final TypeAdapter<LocalDate> LOCAL_DATE = new TypeAdapters.IntegerFieldsTypeAdapter<LocalDate>("year", "month", "day") {
      LocalDate create(long[] values) {
         return LocalDate.of(Math.toIntExact(values[0]), Math.toIntExact(values[1]), Math.toIntExact(values[2]));
      }

      long[] integerValues(LocalDate localDate) {
         return new long[]{localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth()};
      }
   };
   public static final TypeAdapter<LocalTime> LOCAL_TIME = new TypeAdapters.IntegerFieldsTypeAdapter<LocalTime>("hour", "minute", "second", "nano") {
      LocalTime create(long[] values) {
         return LocalTime.of(Math.toIntExact(values[0]), Math.toIntExact(values[1]), Math.toIntExact(values[2]), Math.toIntExact(values[3]));
      }

      long[] integerValues(LocalTime localTime) {
         return new long[]{localTime.getHour(), localTime.getMinute(), localTime.getSecond(), localTime.getNano()};
      }
   };
   private static final TypeAdapter<MonthDay> MONTH_DAY = new TypeAdapters.IntegerFieldsTypeAdapter<MonthDay>("month", "day") {
      MonthDay create(long[] values) {
         return MonthDay.of(Math.toIntExact(values[0]), Math.toIntExact(values[1]));
      }

      long[] integerValues(MonthDay monthDay) {
         return new long[]{monthDay.getMonthValue(), monthDay.getDayOfMonth()};
      }
   };
   private static final TypeAdapter<Period> PERIOD = new TypeAdapters.IntegerFieldsTypeAdapter<Period>("years", "months", "days") {
      Period create(long[] values) {
         return Period.of(Math.toIntExact(values[0]), Math.toIntExact(values[1]), Math.toIntExact(values[2]));
      }

      long[] integerValues(Period period) {
         return new long[]{period.getYears(), period.getMonths(), period.getDays()};
      }
   };
   private static final TypeAdapter<Year> YEAR = new TypeAdapters.IntegerFieldsTypeAdapter<Year>("year") {
      Year create(long[] values) {
         return Year.of(Math.toIntExact(values[0]));
      }

      long[] integerValues(Year year) {
         return new long[]{year.getValue()};
      }
   };
   private static final TypeAdapter<YearMonth> YEAR_MONTH = new TypeAdapters.IntegerFieldsTypeAdapter<YearMonth>("year", "month") {
      YearMonth create(long[] values) {
         return YearMonth.of(Math.toIntExact(values[0]), Math.toIntExact(values[1]));
      }

      long[] integerValues(YearMonth yearMonth) {
         return new long[]{yearMonth.getYear(), yearMonth.getMonthValue()};
      }
   };
   private static final TypeAdapter<ZoneId> ZONE_ID = (new TypeAdapter<ZoneId>() {
      public ZoneId read(JsonReader in) throws IOException {
         in.beginObject();
         String id = null;
         Integer totalSeconds = null;

         while (in.peek() != JsonToken.END_OBJECT) {
            String name = in.nextName();
            switch (name) {
               case "id":
                  id = in.nextString();
                  break;
               case "totalSeconds":
                  totalSeconds = in.nextInt();
                  break;
               default:
                  in.skipValue();
            }
         }

         in.endObject();
         if (id != null) {
            return ZoneId.of(id);
         } else if (totalSeconds != null) {
            return ZoneOffset.ofTotalSeconds(totalSeconds);
         } else {
            throw new JsonSyntaxException("Missing id or totalSeconds field; at path " + in.getPreviousPath());
         }
      }

      public void write(JsonWriter out, ZoneId value) throws IOException {
         if (value instanceof ZoneOffset) {
            out.beginObject();
            out.name("totalSeconds");
            out.value(((ZoneOffset)value).getTotalSeconds());
            out.endObject();
         } else {
            out.beginObject();
            out.name("id");
            out.value(value.getId());
            out.endObject();
         }
      }
   }).nullSafe();
   static final TypeAdapterFactory JAVA_TIME_FACTORY = new TypeAdapterFactory() {
      @Override
      public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
         Class<? super T> rawType = typeToken.getRawType();
         if (!rawType.getName().startsWith("java.time.")) {
            return null;
         }

         TypeAdapter<?> adapter = null;
         if (rawType == Duration.class) {
            adapter = JavaTimeTypeAdapters.DURATION;
         } else if (rawType == Instant.class) {
            adapter = JavaTimeTypeAdapters.INSTANT;
         } else if (rawType == LocalDate.class) {
            adapter = JavaTimeTypeAdapters.LOCAL_DATE;
         } else if (rawType == LocalTime.class) {
            adapter = JavaTimeTypeAdapters.LOCAL_TIME;
         } else if (rawType == LocalDateTime.class) {
            adapter = JavaTimeTypeAdapters.localDateTime(gson);
         } else if (rawType == MonthDay.class) {
            adapter = JavaTimeTypeAdapters.MONTH_DAY;
         } else if (rawType == OffsetDateTime.class) {
            adapter = JavaTimeTypeAdapters.offsetDateTime(gson);
         } else if (rawType == OffsetTime.class) {
            adapter = JavaTimeTypeAdapters.offsetTime(gson);
         } else if (rawType == Period.class) {
            adapter = JavaTimeTypeAdapters.PERIOD;
         } else if (rawType == Year.class) {
            adapter = JavaTimeTypeAdapters.YEAR;
         } else if (rawType == YearMonth.class) {
            adapter = JavaTimeTypeAdapters.YEAR_MONTH;
         } else if (rawType == ZoneId.class || rawType == ZoneOffset.class) {
            adapter = JavaTimeTypeAdapters.ZONE_ID;
         } else if (rawType == ZonedDateTime.class) {
            adapter = JavaTimeTypeAdapters.zonedDateTime(gson);
         }

         return (TypeAdapter<T>)adapter;
      }
   };

   @Override
   public TypeAdapterFactory get() {
      return JAVA_TIME_FACTORY;
   }

   private static TypeAdapter<LocalDateTime> localDateTime(Gson gson) {
      final TypeAdapter<LocalDate> localDateAdapter = gson.getAdapter(LocalDate.class);
      final TypeAdapter<LocalTime> localTimeAdapter = gson.getAdapter(LocalTime.class);
      return (new TypeAdapter<LocalDateTime>() {
            public LocalDateTime read(JsonReader in) throws IOException {
               LocalDate localDate = null;
               LocalTime localTime = null;
               in.beginObject();

               while (in.peek() != JsonToken.END_OBJECT) {
                  String name = in.nextName();
                  switch (name) {
                     case "date":
                        localDate = localDateAdapter.read(in);
                        break;
                     case "time":
                        localTime = localTimeAdapter.read(in);
                        break;
                     default:
                        in.skipValue();
                  }
               }

               in.endObject();
               return LocalDateTime.of(
                  JavaTimeTypeAdapters.requireNonNullField(localDate, "date", in), JavaTimeTypeAdapters.requireNonNullField(localTime, "time", in)
               );
            }

            public void write(JsonWriter out, LocalDateTime value) throws IOException {
               out.beginObject();
               out.name("date");
               localDateAdapter.write(out, value.toLocalDate());
               out.name("time");
               localTimeAdapter.write(out, value.toLocalTime());
               out.endObject();
            }
         })
         .nullSafe();
   }

   private static TypeAdapter<OffsetDateTime> offsetDateTime(Gson gson) {
      final TypeAdapter<LocalDateTime> localDateTimeAdapter = localDateTime(gson);
      final TypeAdapter<ZoneOffset> zoneOffsetAdapter = gson.getAdapter(ZoneOffset.class);
      return (new TypeAdapter<OffsetDateTime>() {
            public OffsetDateTime read(JsonReader in) throws IOException {
               in.beginObject();
               LocalDateTime localDateTime = null;
               ZoneOffset zoneOffset = null;

               while (in.peek() != JsonToken.END_OBJECT) {
                  String name = in.nextName();
                  switch (name) {
                     case "dateTime":
                        localDateTime = localDateTimeAdapter.read(in);
                        break;
                     case "offset":
                        zoneOffset = zoneOffsetAdapter.read(in);
                        break;
                     default:
                        in.skipValue();
                  }
               }

               in.endObject();
               return OffsetDateTime.of(
                  JavaTimeTypeAdapters.requireNonNullField(localDateTime, "dateTime", in), JavaTimeTypeAdapters.requireNonNullField(zoneOffset, "offset", in)
               );
            }

            public void write(JsonWriter out, OffsetDateTime value) throws IOException {
               out.beginObject();
               out.name("dateTime");
               localDateTimeAdapter.write(out, value.toLocalDateTime());
               out.name("offset");
               zoneOffsetAdapter.write(out, value.getOffset());
               out.endObject();
            }
         })
         .nullSafe();
   }

   private static TypeAdapter<OffsetTime> offsetTime(Gson gson) {
      final TypeAdapter<LocalTime> localTimeAdapter = gson.getAdapter(LocalTime.class);
      final TypeAdapter<ZoneOffset> zoneOffsetAdapter = gson.getAdapter(ZoneOffset.class);
      return (new TypeAdapter<OffsetTime>() {
            public OffsetTime read(JsonReader in) throws IOException {
               in.beginObject();
               LocalTime localTime = null;
               ZoneOffset zoneOffset = null;

               while (in.peek() != JsonToken.END_OBJECT) {
                  String name = in.nextName();
                  switch (name) {
                     case "time":
                        localTime = localTimeAdapter.read(in);
                        break;
                     case "offset":
                        zoneOffset = zoneOffsetAdapter.read(in);
                        break;
                     default:
                        in.skipValue();
                  }
               }

               in.endObject();
               return OffsetTime.of(
                  JavaTimeTypeAdapters.requireNonNullField(localTime, "time", in), JavaTimeTypeAdapters.requireNonNullField(zoneOffset, "offset", in)
               );
            }

            public void write(JsonWriter out, OffsetTime value) throws IOException {
               out.beginObject();
               out.name("time");
               localTimeAdapter.write(out, value.toLocalTime());
               out.name("offset");
               zoneOffsetAdapter.write(out, value.getOffset());
               out.endObject();
            }
         })
         .nullSafe();
   }

   private static TypeAdapter<ZonedDateTime> zonedDateTime(Gson gson) {
      final TypeAdapter<LocalDateTime> localDateTimeAdapter = localDateTime(gson);
      final TypeAdapter<ZoneOffset> zoneOffsetAdapter = gson.getAdapter(ZoneOffset.class);
      final TypeAdapter<ZoneId> zoneIdAdapter = gson.getAdapter(ZoneId.class);
      return (new TypeAdapter<ZonedDateTime>() {
            public ZonedDateTime read(JsonReader in) throws IOException {
               in.beginObject();
               LocalDateTime localDateTime = null;
               ZoneOffset zoneOffset = null;
               ZoneId zoneId = null;

               while (in.peek() != JsonToken.END_OBJECT) {
                  String name = in.nextName();
                  switch (name) {
                     case "dateTime":
                        localDateTime = localDateTimeAdapter.read(in);
                        break;
                     case "offset":
                        zoneOffset = zoneOffsetAdapter.read(in);
                        break;
                     case "zone":
                        zoneId = zoneIdAdapter.read(in);
                        break;
                     default:
                        in.skipValue();
                  }
               }

               in.endObject();
               return ZonedDateTime.ofInstant(
                  JavaTimeTypeAdapters.requireNonNullField(localDateTime, "dateTime", in),
                  JavaTimeTypeAdapters.requireNonNullField(zoneOffset, "offset", in),
                  JavaTimeTypeAdapters.requireNonNullField(zoneId, "zone", in)
               );
            }

            public void write(JsonWriter out, ZonedDateTime value) throws IOException {
               if (value == null) {
                  out.nullValue();
               } else {
                  out.beginObject();
                  out.name("dateTime");
                  localDateTimeAdapter.write(out, value.toLocalDateTime());
                  out.name("offset");
                  zoneOffsetAdapter.write(out, value.getOffset());
                  out.name("zone");
                  zoneIdAdapter.write(out, value.getZone());
                  out.endObject();
               }
            }
         })
         .nullSafe();
   }

   private static <T> T requireNonNullField(T field, String fieldName, JsonReader reader) {
      if (field == null) {
         throw new JsonSyntaxException("Missing " + fieldName + " field; at path " + reader.getPreviousPath());
      } else {
         return field;
      }
   }
}
