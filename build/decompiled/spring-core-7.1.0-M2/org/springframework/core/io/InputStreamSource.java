package org.springframework.core.io;

import java.io.IOException;
import java.io.InputStream;

@FunctionalInterface
public interface InputStreamSource {
   InputStream getInputStream() throws IOException;
}
