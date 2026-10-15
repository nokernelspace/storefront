package org.springframework.http.client.reactive;

import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.ResponseCookie;

final class JdkResponseCookieParser implements ResponseCookie.Parser {
   private static final Pattern SAME_SITE_PATTERN = Pattern.compile("(?i).*SameSite=(Strict|Lax|None).*");

   @Override
   public List<ResponseCookie> parse(String header) {
      Matcher matcher = SAME_SITE_PATTERN.matcher(header);
      String sameSite = matcher.matches() ? matcher.group(1) : null;
      List<HttpCookie> cookies = HttpCookie.parse(header);
      List<ResponseCookie> result = new ArrayList<>(cookies.size());
      cookies.forEach(cookie -> result.add(ResponseCookie.from(cookie).sameSite(sameSite).build()));
      return result;
   }
}
