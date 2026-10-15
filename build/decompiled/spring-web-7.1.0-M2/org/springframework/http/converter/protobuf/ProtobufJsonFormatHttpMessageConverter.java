package org.springframework.http.converter.protobuf;

import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.util.JsonFormat.Parser;
import com.google.protobuf.util.JsonFormat.Printer;
import org.jspecify.annotations.Nullable;

public class ProtobufJsonFormatHttpMessageConverter extends ProtobufHttpMessageConverter {
   public ProtobufJsonFormatHttpMessageConverter() {
      this(null, null);
   }

   public ProtobufJsonFormatHttpMessageConverter(@Nullable Parser parser, @Nullable Printer printer) {
      this(parser, printer, (ExtensionRegistry)null);
   }

   public ProtobufJsonFormatHttpMessageConverter(@Nullable Parser parser, @Nullable Printer printer, @Nullable ExtensionRegistry extensionRegistry) {
      super(new ProtobufHttpMessageConverter.ProtobufJavaUtilDelegate(parser, printer), extensionRegistry);
   }
}
