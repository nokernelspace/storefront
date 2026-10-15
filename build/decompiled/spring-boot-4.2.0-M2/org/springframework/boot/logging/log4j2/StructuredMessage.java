package org.springframework.boot.logging.log4j2;

import java.io.IOException;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.util.MultiFormatStringBuilderFormattable;
import org.springframework.boot.json.WritableJson;

final class StructuredMessage {
   private static final String JSON2 = "JSON";
   private static final String[] JSON = new String[]{"JSON"};

   private StructuredMessage() {
   }

   static Object get(Message message) {
      return message instanceof MultiFormatStringBuilderFormattable multiFormatMessage && hasJsonFormat(multiFormatMessage)
         ? WritableJson.of(out -> formatTo(multiFormatMessage, out))
         : message.getFormattedMessage();
   }

   private static boolean hasJsonFormat(MultiFormatStringBuilderFormattable message) {
      for (String format : message.getFormats()) {
         if ("JSON".equalsIgnoreCase(format)) {
            return true;
         }
      }

      return false;
   }

   private static void formatTo(MultiFormatStringBuilderFormattable message, Appendable out) throws IOException {
      if (out instanceof StringBuilder stringBuilder) {
         message.formatTo(JSON, stringBuilder);
      } else {
         out.append(message.getFormattedMessage(JSON));
      }
   }
}
