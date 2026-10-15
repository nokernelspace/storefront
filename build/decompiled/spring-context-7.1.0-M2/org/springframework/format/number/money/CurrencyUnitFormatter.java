package org.springframework.format.number.money;

import java.util.Locale;
import javax.money.CurrencyUnit;
import javax.money.Monetary;
import org.springframework.format.Formatter;

public class CurrencyUnitFormatter implements Formatter<CurrencyUnit> {
   public String print(CurrencyUnit object, Locale locale) {
      return object.getCurrencyCode();
   }

   public CurrencyUnit parse(String text, Locale locale) {
      return Monetary.getCurrency(text, new String[0]);
   }
}
