package org.springframework.core.env;

class SimpleCommandLineArgsParser {
   public CommandLineArgs parse(String... args) {
      CommandLineArgs commandLineArgs = new CommandLineArgs();
      boolean endOfOptions = false;

      for (String arg : args) {
         if (!endOfOptions && arg.startsWith("--")) {
            String optionText = arg.substring(2);
            int indexOfEqualsSign = optionText.indexOf(61);
            if (indexOfEqualsSign > -1) {
               String optionName = optionText.substring(0, indexOfEqualsSign);
               String optionValue = optionText.substring(indexOfEqualsSign + 1);
               if (optionName.isEmpty()) {
                  throw new IllegalArgumentException("Invalid argument syntax: " + arg);
               }

               commandLineArgs.addOptionArg(optionName, optionValue);
            } else if (!optionText.isEmpty()) {
               commandLineArgs.addOptionArg(optionText, null);
            } else {
               endOfOptions = true;
            }
         } else {
            commandLineArgs.addNonOptionArg(arg);
         }
      }

      return commandLineArgs;
   }
}
