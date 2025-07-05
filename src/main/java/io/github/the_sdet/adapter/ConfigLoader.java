package io.github.the_sdet.adapter;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Utility class to load configuration for the Cucumber Summary Reporter plugin.
 * <p>
 * This class supports multiple sources of configuration with the following
 * override precedence (highest to lowest):
 * <ol>
 * <li>Plugin argument string (e.g., {@code env.url=https://stg})</li>
 * <li>JVM system properties (e.g., {@code -Dcucumber.summary.env.url=...})</li>
 * <li>Environment variables (e.g., {@code CUCUMBER_SUMMARY_ENV_URL=...})</li>
 * <li>Properties file {@code cucumber-summary.properties} on the classpath</li>
 * </ol>
 * This class is Java 11 compatible and does not use modern Java features.
 *
 * @author Pabitra Swain (contact.the.sdet@gmail.com)
 */
class ConfigLoader {
  private static final String ENV_PREFIX = "CUCUMBER_SUMMARY_", SYS_PREFIX = "cucumber.summary.";

  private ConfigLoader() {
  }

  /**
   * Load merged configuration from all supported sources.
   *
   * @param args
   *            plugin argument string in format {@code key=value;key2=value2}
   * @return combined {@link Properties} object
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  static Properties load(String args) {
    Properties p = new Properties();
    try (InputStream in = CucumberSummaryReporter.class.getClassLoader()
        .getResourceAsStream("cucumber-summary.properties")) {
      if (in != null)
        p.load(in);
    } catch (IOException ignored) {
    }
    System.getenv().forEach((k, v) -> {
      if (k.startsWith(ENV_PREFIX))
        p.setProperty(envKey(k), v);
    });
    System.getProperties().forEach((k, v) -> {
      String s = String.valueOf(k);
      if (s.startsWith(SYS_PREFIX))
        p.setProperty(s.substring(SYS_PREFIX.length()), String.valueOf(v));
    });
    parseArgs(args).forEach(p::setProperty);
    return p;
  }

  /**
   * Normalize an environment variable to match the property key format. Example:
   * {@code CUCUMBER_SUMMARY_ENV_URL → env.url}
   *
   * @param e
   *            raw environment variable name
   * @return normalized key
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private static String envKey(String e) {
    return e.substring(ENV_PREFIX.length()).toLowerCase(Locale.ROOT).replace('_', '.');
  }

  /**
   * Parse plugin arguments of the form {@code key=value;key2=value2} into a map.
   *
   * @param a
   *            raw plugin argument string
   * @return parsed key-value map
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private static Map<String, String> parseArgs(String a) {
    Map<String, String> m = new HashMap<>();
    if (a == null || a.trim().isEmpty())
      return m;
    for (String p : a.split("[;&]")) {
      String[] kv = p.split("=", 2);
      if (kv.length == 2)
        m.put(kv[0].trim(), kv[1].trim());
    }
    return m;
  }
}