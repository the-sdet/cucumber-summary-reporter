package io.github.the_sdet.adapter;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom Cucumber plugin that generates a styled summary report in HTML.
 * <p>
 * Runtime override precedence (highest → lowest):
 * <ol>
 * <li>Plugin args string (e.g.
 * {@code CucumberSummaryReporter:env.url=https://stg})</li>
 * <li>JVM system property {@code -Dcucumber.summary.env.url=...}</li>
 * <li>Environment variable {@code CUCUMBER_SUMMARY_ENV_URL=...}</li>
 * <li>{@code cucumber-summary.properties} on class‑path</li>
 * <li>Hard‑coded defaults in this class</li>
 * </ol>
 * <p>
 * **Important: ** even if a system property is set *after* the reporter is
 * created (for example in a JUnit {@code @BeforeAll} hook), the getter methods
 * below re‑check {@link System#getProperty(String)} at each call, so the late
 * override still wins.
 * <p>
 * Java 11 compatible – no records, no switch expressions.
 */
public class CucumberSummaryReporter implements ConcurrentEventListener {

  /*
   * --------------------------------------------------- ⚙ Configuration
   * ---------------------------------------------------
   */

  /**
   * Merged, file‑based configuration loaded once in the constructor.
   */
  private final Properties baseCfg;

  /**
   * Prefix prepended to every key when looking for an override in
   * {@link System#getProperties()}.
   */
  private static final String SYS_PROP_PREFIX = "cucumber.summary.";

  /**
   * Resolve a configuration value.<br>
   * Order: ① System property → ② value from
   * {@code cucumber-summary.properties}.<br>
   * Returns {@code null} if the key isn’t found in either source.
   *
   * @param key
   *            the suffix of the config key (without {@link #SYS_PROP_PREFIX})
   * @return resolved value or {@code null}
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private String cfg(String key) {
    String live = System.getProperty(SYS_PROP_PREFIX + key);
    return live != null ? live : baseCfg.getProperty(key);
  }

  /**
   * Resolve a configuration value with a default.
   *
   * @param key
   *            configuration key suffix (without prefix)
   * @param def
   *            fallback default if the key is missing everywhere
   * @return resolved value (never {@code null})
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private String cfg(String key, String def) {
    String val = cfg(key);
    return val != null ? val : def;
  }

  /*
   * --------------------------------------------------- 📓 Logger
   * ---------------------------------------------------
   */

  /**
   * Dedicated logger; set {@code additivity=false} in log4j config to keep output
   * isolated.
   */
  private static final Logger log = LogManager.getLogger(CucumberSummaryReporter.class);

  /*
   * --------------------------------------------------- 🗂 Runtime state
   * containers ---------------------------------------------------
   */

  /**
   * Feature → (Scenario → Status) mapping collected during execution.
   */
  private static final Map<String, Map<String, Status>> featureResults = new ConcurrentHashMap<>();

  /**
   * Feature URI → static metadata (package, display name, etc.).
   */
  private final Map<String, FeatureInfo> featureFiles = new ConcurrentHashMap<>();

  /**
   * Mutable map to inject per‑feature test credentials at runtime.
   */
  private static final Map<String, List<String>> testUsers = new LinkedHashMap<>();

  /**
   * Immutable DTO exposing the raw execution summary.
   *
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  public static class SummaryData {
    /**
     * Feature → Scenario → Status map (immutable deep copy).
     */
    public final Map<String, Map<String, Status>> results;

    private SummaryData(Map<String, Map<String, Status>> src) {
      Map<String, Map<String, Status>> copy = new LinkedHashMap<>();
      src.forEach((f, m) -> copy.put(f, new LinkedHashMap<>(m)));
      this.results = Collections.unmodifiableMap(copy);
    }
  }

  /**
   * Returns a deep‑copied snapshot of the current execution state. Safe to call
   * even before the run finishes; never returns {@code null}.
   *
   * @return summary data
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  @SuppressWarnings("unused")
  public static SummaryData getSummaryData() {
    return new SummaryData(featureResults);
  }

  /**
   * Register a pair of test credentials for a feature at runtime.
   *
   * @param featureUri
   *            feature URI as provided by Cucumber’s {@code Scenario#getUri()}
   * @param username
   *            username to display in the HTML report
   * @param password
   *            password to display in the HTML report
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  @SuppressWarnings("unused")
  public static void registerTestUser(String featureUri, String username, String password) {
    testUsers.put(featureUri, Arrays.asList(username, password));
  }

  /*
   * --------------------------------------------------- 🔧 Defaults
   * ---------------------------------------------------
   */

  private static final String defaultReportPath = "testReports/CucumberTestSummary.html";
  private static final String defaultReportTitle = "Cucumber Test Summary";
  private static final String defaultUserName = "---";
  private static final String defaultPassword = "---";
  private static final String defaultTimeStampFormat = "EEEE, dd-MMM-yyyy HH:mm:ss z";
  private static final String defaultTimeZone = "IST";
  private static final String defaultDesktopMaxWidth = "1280";
  private static final String defaultHeadingBgColor = "#23436a";
  private static final String defaultHeadingColor = "#ffffff";
  private static final String defaultSubTotalBgColor = "#cbcbcb";
  private static final String defaultSubTotalColor = "#090909";
  private static final String defaultScenarioTableHeadingBgColor = "#efefef";
  private static final String defaultScenarioTableHeadingColor = "#090909";

  /*
   * --------------------------------------------------- 🚚 Constructors
   * ---------------------------------------------------
   */

  /**
   * Default constructor used when no plugin arguments are supplied via
   * <code>--plugin io...CucumberSummaryReporter</code>. Internal delegates to the
   * argument‑based constructor with an empty string.
   *
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  public CucumberSummaryReporter() {
    this("");
  }

  /**
   * Main constructor invoked by Cucumber when the reporter is declared with
   * inline plugin arguments (e.g.
   * <code>...:env.url=<a href="https://stg">...</a></code>).
   * <p>
   * The constructor merges configuration from four sources in descending
   * precedence: plugin args, JVM system properties, environment variables and
   * <code>cucumber-summary.properties</code> on the class‑path.
   *
   * @param pluginArgs
   *            raw argument string supplied after the colon in the plugin
   *            declaration
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  public CucumberSummaryReporter(String pluginArgs) {
    this.baseCfg = ConfigLoader.load(pluginArgs);
  }

  /*
   * --------------------------------------------------- 📢 Event wiring
   * ---------------------------------------------------
   */

  /**
   * Registers all internal event handlers with Cucumber’s {@link EventPublisher}
   * so the reporter can listen to parse, execution and run‑completion events.
   *
   * @param p
   *            singleton {@link EventPublisher} instance provided by the Cucumber
   *            runtime
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  @Override
  public void setEventPublisher(EventPublisher p) {
    p.registerHandlerFor(TestSourceParsed.class, this::onSourceParsed);
    p.registerHandlerFor(TestCaseStarted.class, this::onCaseStarted);
    p.registerHandlerFor(TestCaseFinished.class, this::onCaseFinished);
    p.registerHandlerFor(TestRunFinished.class, this::onRunFinished);
  }

  /*
   * --------------------------------------------------- 📌 Events
   * ---------------------------------------------------
   */

  /**
   * Handler for the {@link TestSourceParsed} event. Caches static metadata such
   * as package name and feature display name for later use in the HTML report.
   *
   * @param evt
   *            event dispatched when a Gherkin document is parsed
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private void onSourceParsed(TestSourceParsed evt) {
    String uri = evt.getUri().toString();
    String[] parts = uri.split("/");
    String fileName = parts[parts.length - 1].split("\\.")[0];
    String folder = parts[parts.length - 2];
    String pkg = folder.contains(":") ? cleanPkg(folder.split(":")[1]) : cleanPkg(folder);
    String featName = evt.getNodes().stream().map(Node::getName).filter(Optional::isPresent).map(Optional::get)
        .findFirst().orElse("Feature name missing");
    featureFiles.put(uri, new FeatureInfo(uri, pkg, fileName, featName));
  }

  /**
   * Utility to strip generic <code>feature</code>/<code>features</code> folder
   * names from a path fragment so they don’t appear as Java package names in the
   * report.
   *
   * @param f
   *            raw folder name
   * @return cleaned package name or empty string
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private String cleanPkg(String f) {
    return ("feature".equalsIgnoreCase(f) || "features".equalsIgnoreCase(f)) ? "" : f;
  }

  /**
   * Fires when a scenario begins execution. Currently only logs a DEBUG message;
   * retained as a hook point for potential future extensions.
   *
   * @param e
   *            {@link TestCaseStarted} event
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private void onCaseStarted(TestCaseStarted e) {
    log.debug("Started: {}", e.getTestCase().getName());
  }

  /**
   * Captures scenario pass/fail status at completion and stores it in the
   * in‑memory result map.
   *
   * @param e
   *            {@link TestCaseFinished} event containing result status
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private void onCaseFinished(TestCaseFinished e) {
    String uri = e.getTestCase().getUri().toString();
    String name = e.getTestCase().getName();
    String key = "Scenario Outline".equals(e.getTestCase().getKeyword())
        ? name + " #" + e.getTestCase().getLocation().getLine()
        : name;
    featureResults.computeIfAbsent(uri, k -> new LinkedHashMap<>()).put(key, e.getResult().getStatus());
  }

  /**
   * Generates the summary HTML file once Cucumber announces that all scenarios
   * have finished.
   *
   * @param e
   *            {@link TestRunFinished} terminal event
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private void onRunFinished(TestRunFinished e) {
    // generate the HTML first
    generateReport();
  }

  /*
   * --------------------------------------------------- 🖼 Report skeleton
   * ---------------------------------------------------
   */

  /**
   * Generates a timestamp string based on configured format and time zone.
   *
   * @return formatted timestamp string
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private String timestamp() {
    SimpleDateFormat sdf = new SimpleDateFormat(cfg("time.stamp.format", defaultTimeStampFormat));
    sdf.setTimeZone(TimeZone.getTimeZone(cfg("time.zone", defaultTimeZone)));
    return sdf.format(new Date());
  }

  /**
   * Reads the content of a resource file as a string.
   *
   * @param name
   *            resource file name
   * @return content of the resource as UTF-8 string, or null if not found
   * @throws IOException
   *             if reading the resource fails
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private String readRes(String name) throws IOException {
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
      if (in == null) {
        log.error("Template {} missing", name);
        return null;
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /**
   * Builds the complete HTML skeleton for the summary report. Populates
   * placeholders in the HTML template with dynamic content, styles, and scripts.
   *
   * @return final populated HTML string, or null if any required template file is
   *         missing
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private String skeleton() {
    try {
      String html = readRes("ReportTemplate.html"), css = readRes("styles.css"), js = readRes("scripts.js");
      if (html == null || css == null || js == null)
        return null;
      String r = html.replace("$styleGoesHere", css).replace("$scriptGoesHere", js)
          .replace("$reportTitle", cfg("report.title", defaultReportTitle))
          .replace("$defaultHeadingBgColor", cfg("heading.background.color", defaultHeadingBgColor))
          .replace("$defaultHeadingColor", cfg("heading.color", defaultHeadingColor))
          .replace("$defaultSubTotalBgColor", cfg("subtotal.background.color", defaultSubTotalBgColor))
          .replace("$defaultSubTotalColor", cfg("subtotal.color", defaultSubTotalColor))
          .replace("$defaultScenarioTableHeadingBgColor",
              cfg("scenario.table.heading.background.color", defaultScenarioTableHeadingBgColor))
          .replace("$defaultScenarioTableHeadingColor",
              cfg("scenario.table.heading.color", defaultScenarioTableHeadingColor))
          .replace("$defaultMaxWidth", cfg("desktop.view.max.width", defaultDesktopMaxWidth));

      if (Boolean.parseBoolean(cfg("show.env", "false")) && cfg("env.url") != null) {
        r = r.replace("$enterUrl", cfg("env.url"));
      } else {
        r = r.replace("environmentRow", "environmentRow hidden");
      }
      if (Boolean.parseBoolean(cfg("show.os.browser", "false")) && cfg("os.browser") != null) {
        r = r.replace("$enterOsBrowserName", cfg("os.browser"));
      } else {
        r = r.replace("OsBrowserRow", "OsBrowserRow hidden");
      }
      if (Boolean.parseBoolean(cfg("show.executed.by", "false")) && cfg("executed.by") != null) {
        r = r.replace("$executedBy", cfg("executed.by"));
      } else {
        r = r.replace("executedByRow", "executedByRow hidden");
      }
      if (Boolean.parseBoolean(cfg("show.execution.timestamp", "true"))) {
        r = r.replace("$enterTimeStamp", timestamp());
      } else {
        r = r.replace("TimeStampRow", "TimeStampRow hidden");
      }
      return r;
    } catch (IOException ex) {
      log.error("Skeleton build fail", ex);
      return null;
    }
  }

  /*
   * --------------------------------------------------- 📊 Report generation
   * ---------------------------------------------------
   */

  /**
   * Generates the HTML report
   *
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private void generateReport() {
    if (featureResults.isEmpty()) {
      log.info("No results – skip report");
      return;
    }
    String rpt = skeleton();
    if (rpt == null)
      return;

    String featTpl = StringUtils.substringBetween(rpt, "FeatureDetailsStart", "FeatureDetailsEnd");
    String tcTpl = StringUtils.substringBetween(rpt, "TcDetailsStart", "TcDetailsEnd");
    String subTpl = StringUtils.substringBetween(rpt, "SubTotalDetailsStart", "SubTotalDetailsEnd");
    featTpl = featTpl.replace("TcDetailsStart" + tcTpl + "TcDetailsEnd", "$insertTc");
    rpt = rpt.replace("TcDetailsStart" + tcTpl + "TcDetailsEnd", "$insertTc")
        .replace("SubTotalDetailsStart" + subTpl + "SubTotalDetailsEnd", "$insertSub")
        .replace("FeatureDetailsStart" + featTpl + "FeatureDetailsEnd", "$insertFeat");

    StringBuilder featBuf = new StringBuilder();
    DecimalFormat df = new DecimalFormat("0.00");
    int oPass = 0, oFail = 0, fNo = 0;

    for (Map.Entry<String, Map<String, Status>> feat : featureResults.entrySet()) {
      fNo++;
      FeatureInfo info = featureFiles.get(feat.getKey());
      String name = Boolean.parseBoolean(cfg("use.feature.name.from.feature.file", "false"))
          ? info.featureNameDefinedInFeatureFile
          : info.featureFileName;
      if (Boolean.parseBoolean(cfg("use.package.name", "true")) && !info.packageName.isEmpty())
        name = info.packageName + " - " + name;
      List<String> credentials = testUsers.getOrDefault(feat.getKey(),
          Arrays.asList(cfg("test.user", defaultUserName), cfg("test.password", defaultPassword)));
      String user = credentials.get(0), pwd = credentials.get(1);

      StringBuilder tcBuf = new StringBuilder();
      int pass = 0, fail = 0, idx = 1;
      for (Map.Entry<String, Status> sc : feat.getValue().entrySet()) {
        boolean ok = sc.getValue() == Status.PASSED;
        if (ok)
          pass++;
        else
          fail++;
        tcBuf.append(tcTpl.replace("$tcKey", "SC-" + String.format("%03d", idx++))
            .replace("$tcName", sc.getKey()).replace("$tcStatus", ok ? "green" : "red"));
      }
      oPass += pass;
      oFail += fail;
      int tot = pass + fail;
      featBuf.append(featTpl.replace("$insertTc", tcBuf.toString()).replace("$featureName", name)
          .replace("$username", user).replace("$password", pwd).replace("$passCount", String.valueOf(pass))
          .replace("$failCount", String.valueOf(fail)).replace("$totalCount", String.valueOf(tot))
          .replace("$featureStatus", fail == 0 ? "green" : "red")
          .replace("$featurePassPercent", df.format((double) pass / tot * 100) + "%")
          .replace("$featureNo", String.valueOf(fNo)));
    }

    int overall = oPass + oFail;
    String sub = subTpl.replace("$overallPassCount", String.valueOf(oPass))
        .replace("$overallFailCount", String.valueOf(oFail)).replace("$overallCount", String.valueOf(overall))
        .replace("$overallStatus", oFail == 0 ? "green" : "red").replace("$overallPassPercent",
            overall == 0 ? "0.00%" : df.format((double) oPass / overall * 100) + "%");

    rpt = rpt.replace("$insertFeat", featBuf.toString()).replace("$insertSub", sub)
        .replace("$overallPassCount", String.valueOf(oPass)).replace("$overallFailCount", String.valueOf(oFail))
        .replace("$overallCount", String.valueOf(overall));

    try {
      FileUtils.writeStringToFile(new File(cfg("report.file.path", defaultReportPath)), rpt,
          Charset.defaultCharset());
    } catch (IOException ex) {
      log.error("Write report fail", ex);
    }
  }

  /*
   * --------------------------------------------------- 📑 Helper classes
   * ---------------------------------------------------
   */

  /**
   * Represents metadata extracted from a Gherkin feature. Immutable value holder
   * for use inside the report generation logic.
   *
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private static class FeatureInfo {
    /**
     * Fully qualified URI of the feature file.
     */
    final String uri;
    /**
     * Java package where the feature logically belongs.
     */
    final String packageName;
    /**
     * Name of the feature file (e.g., Login.feature).
     */
    final String featureFileName;
    /**
     * Actual feature name defined inside the feature file (e.g., Feature: User
     * Login).
     */
    final String featureNameDefinedInFeatureFile;

    /**
     * Constructor to initialize all fields.
     *
     * @param u
     *            feature file URI
     * @param p
     *            logical package name
     * @param f
     *            feature file name
     * @param n
     *            feature name defined within the feature file
     * @author Pabitra Swain (contact.the.sdet@gmail.com)
     */
    FeatureInfo(String u, String p, String f, String n) {
      uri = u;
      packageName = p;
      featureFileName = f;
      featureNameDefinedInFeatureFile = n;
    }
  }
}

/*
 * --------------------------------------------------- 🔧 ConfigLoader
 * (package‑private) ---------------------------------------------------
 */

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
   * Normalize an environment variable to match properties key format. Example:
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