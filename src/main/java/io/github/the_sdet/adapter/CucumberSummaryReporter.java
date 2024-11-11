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
 * Custom Event publishers for Cucumber Listener
 *
 * @author Pabitra Swain (contact.the.sdet@gmail.com)
 */
@SuppressWarnings({"unused", "SameParameterValue"})
public class CucumberSummaryReporter implements ConcurrentEventListener {

  /**
   * Logger Object
   */
  protected static final Logger log = LogManager.getLogger(CucumberSummaryReporter.class);

  static class FeatureInfo {
    String uri;
    String packageName;
    String featureFileName;
    String featureNameDefinedInFeatureFile;

    private FeatureInfo(String uri, String packageName, String featureFileName,
        String featureNameDefinedInFeatureFile) {
      this.uri = uri;
      this.packageName = packageName;
      this.featureFileName = featureFileName;
      this.featureNameDefinedInFeatureFile = featureNameDefinedInFeatureFile;
    }
  }

  /**
   * Result Map to have Features, its Scenario and their results
   */
  protected static final Map<String, Map<String, Status>> featureWiseResultMap = new ConcurrentHashMap<>();

  /**
   * Result Map to have Feature file metadata
   */
  private static final Map<String, FeatureInfo> featureFiles = new ConcurrentHashMap<>();

  /**
   * To hold Runtime Test Users
   */
  public static final Map<String, List<String>> testUsers = new ConcurrentHashMap<>();

  // Default Values
  private static final String defaultReportPath = "testReports/CucumberTestSummary.html";
  private static final String defaultReportTitle = "Cucumber Test Summary";
  private static final String defaultUserName = "---";
  private static final String defaultPassword = "---";
  private static final String defaultTimeStampFormat = "EEEE, dd-MMM-yyyy HH:mm:ss z";
  private static final String defaultTimeZone = "IST";

  private static final String defaultDesktopViewWidth = "80%";

  private static final String defaultHeadingBgColor = "#23436a";
  private static final String defaultHeadingColor = "#ffffff";

  private static final String defaultSubTotalBgColor = "#cbcbcb";
  private static final String defaultSubTotalColor = "#090909";

  private static final String defaultScenarioTableHeadingBgColor = "#efefef";
  private static final String defaultScenarioTableHeadingColor = "#090909";

  private boolean useDefault = false;
  private boolean propertyLoaded = false;
  Properties prop;

  /**
   * Default Constructor
   *
   * @param arg
   *            param
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  public CucumberSummaryReporter(String arg) {
  }

  /**
   * Event publishers
   *
   * @param publisher
   *            Type of publisher
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  @Override
  public void setEventPublisher(EventPublisher publisher) {
    // Register Handles for Cucumber Listener
    publisher.registerHandlerFor(TestCaseStarted.class, this::scenarioStartedHandler);
    publisher.registerHandlerFor(TestCaseFinished.class, this::scenarioFinishedHandler);
    publisher.registerHandlerFor(TestRunFinished.class, this::runFinishedHandler);
    publisher.registerHandlerFor(TestSourceParsed.class, this::sourceParseStartedHandler);
    publisher.registerHandlerFor(TestStepStarted.class, this::stepStartedHandler);
    publisher.registerHandlerFor(TestStepFinished.class, this::stepFinishedHandler);
  }

  /**
   * Executed when Scenario starts
   *
   * @param event
   *            ScenarioFinish event object
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  protected void scenarioStartedHandler(TestCaseStarted event) {
    log.debug("Scenario Execution Started: {}", event.getTestCase().getName());
  }

  private void sourceParseStartedHandler(TestSourceParsed event) {
    String uri = event.getUri().toString();
    Collection<Node> nodes = event.getNodes();
    String packageName, featureName;
    String[] test = uri.split("/");
    int size = test.length;
    featureName = test[size - 1].split("\\.")[0];
    if (test[size - 2].contains(":"))
      packageName = getPackageName(test[size - 2].split(":")[1]);
    else
      packageName = getPackageName(test[size - 2]);

    String featureNameDefinedInFeatureFile = "";
    for (Node node : nodes) {
      featureNameDefinedInFeatureFile = node.getName().orElse("Feature Name is missing");
    }
    featureFiles.put(uri, new FeatureInfo(uri, packageName, featureName, featureNameDefinedInFeatureFile));
  }

  /**
   * Executed when Scenario Step finishes
   *
   * @param event
   *            ScenarioFinish event object
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  protected void scenarioFinishedHandler(TestCaseFinished event) {
    String scenario = event.getTestCase().getName();
    log.debug("Scenario Completed: {}", scenario);
    String uri = event.getTestCase().getUri().toString();

    if (!featureWiseResultMap.containsKey(uri)) {
      Map<String, Status> scenarios = new ConcurrentHashMap<>();
      scenarios.put(scenario, event.getResult().getStatus());
      featureWiseResultMap.put(uri, scenarios);
    } else {
      featureWiseResultMap.get(uri).put(scenario, event.getResult().getStatus());
    }

    // Steps for the scenario - Not Required for the summary Report
    /*
     * List<TestStep> steps = event.getTestCase().getTestSteps(); for (TestStep step
     * : steps) { if (step instanceof PickleStepTestStep) { String stepText =
     * (((PickleStepTestStep) step).getStep().getText()); log.info(stepText); } }
     */
  }

  /**
   * Executed when Test Run Finished
   *
   * @param event
   *            TestRunFinish event object
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private void runFinishedHandler(TestRunFinished event) {
    parseResultToReport(featureWiseResultMap);
  }

  /**
   * Executed when Scenario Step starts
   *
   * @param event
   *            StepStart event object
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  protected void stepStartedHandler(TestStepStarted event) {
    if (event.getTestStep() instanceof PickleStepTestStep) {
      PickleStepTestStep testStep = (PickleStepTestStep) event.getTestStep();
      if (!testStep.getStep().getKeyword().startsWith("Given")) {
        log.debug("Step Started: {}", testStep.getStep().getText());
      }
    }
  }

  /**
   * Executed when Scenario Step finishes
   *
   * @param event
   *            StepFinish event object
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  protected void stepFinishedHandler(TestStepFinished event) {
    if (event.getTestStep() instanceof PickleStepTestStep) {
      PickleStepTestStep testStep = (PickleStepTestStep) event.getTestStep();
      if (!testStep.getStep().getKeyword().startsWith("Given")) {
        log.debug("Step Completed: {}", testStep.getStep().getText());
      }
    }
  }

  /**
   * Checks if there is no extra folder structure under features
   *
   * @param potentialPackageName
   *            String Parent Folder of the feature file.
   * @return The folder name if there is any available
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private static String getPackageName(String potentialPackageName) {
    if (potentialPackageName.equalsIgnoreCase("feature") || potentialPackageName.equalsIgnoreCase("features"))
      return "";
    else
      return potentialPackageName;
  }

  /**
   * This method reads values from a property file
   *
   * @param propertyName
   *            Name of the property, which value needs to be retrieved
   * @return String value of the property
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private String getPropertyValue(String propertyName) {
    if (!propertyLoaded)
      prop = loadPropertyFile();
    return prop.getProperty(propertyName);
  }

  /**
   * This method loads the 'cucumber-summary.properties' file
   *
   * @return Returns the loaded properties
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private Properties loadPropertyFile() {
    Properties prop = new Properties();
    try {
      prop.load(
          CucumberSummaryReporter.class.getClassLoader().getResourceAsStream("cucumber-summary.properties"));
    } catch (Exception e) {
      log.error("'cucumber-summary.properties' NOT found in test/resources. Using Default Config...");
      useDefault = true;
    }
    propertyLoaded = true;
    return prop;
  }

  /**
   * This method constructs the base or non-test content of the final report
   *
   * @return String content
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private String constructReport() {
    try {
      // Load the base HTML template and static assets
      String reportTemplate = loadFileContent("ReportTemplate.html"); // Load HTML template
      String cssStyles = loadFileContent("styles.css"); // Load CSS
      String jsScripts = loadFileContent("scripts.js"); // Load JavaScript

      String reportTitleProvided = getPropertyValue("report.title");
      String reportTitle = reportTitleProvided == null ? defaultReportTitle : reportTitleProvided;
      boolean showEnvUrl = Boolean.parseBoolean(getPropertyValue("show.env"));
      String envUrl = getPropertyValue("env.url");
      boolean showOsBrowserInfo = Boolean.parseBoolean(getPropertyValue("show.os.browser"));
      String osBrowserInfo = getPropertyValue("os.browser");
      boolean showExecutedBy = Boolean.parseBoolean(getPropertyValue("show.executed.by"));
      String executedBy = getPropertyValue("executed.by");
      boolean showTimeStamp = Boolean.parseBoolean(getPropertyValue("show.execution.timestamp")) || useDefault;

      String headingBgColor = getPropertyValue("heading.background.color");
      String headingColor = getPropertyValue("heading.color");
      String subTotalColor = getPropertyValue("subtotal.color");
      String subTotalBgColor = getPropertyValue("subtotal.background.color");
      String scenarioTableHeadingBg = getPropertyValue("scenario.table.heading.background.color");
      String scenarioTableHeadingColor = getPropertyValue("scenario.table.heading.color");

      String desktopViewWidth = getPropertyValue("desktop.view.width");

      if (cssStyles == null || jsScripts == null || reportTemplate == null) {
        log.error("Template Files NOT found.. Please raise a bug to the developer.");
        return null;
      }

      String report = reportTemplate.replace("$styleGoesHere", cssStyles).replace("$scriptGoesHere", jsScripts)
          .replace("$reportTitle", reportTitle)
          .replace("$defaultHeadingBgColor", headingBgColor == null ? defaultHeadingBgColor : headingBgColor)
          .replace("$defaultHeadingColor", headingColor == null ? defaultHeadingColor : headingColor)
          .replace("$defaultSubTotalBgColor",
              subTotalBgColor == null ? defaultSubTotalBgColor : subTotalBgColor)
          .replace("$defaultSubTotalColor", subTotalColor == null ? defaultSubTotalColor : subTotalColor)
          .replace("$defaultScenarioTableHeadingBgColor",
              scenarioTableHeadingBg == null
                  ? defaultScenarioTableHeadingBgColor
                  : scenarioTableHeadingBg)
          .replace("$defaultScenarioTableHeadingColor",
              scenarioTableHeadingColor == null
                  ? defaultScenarioTableHeadingColor
                  : scenarioTableHeadingColor)
          .replace("$reportTitle", reportTitle)
          .replace("$defaultDesktopViewWidth", desktopViewWidth == null
              ? defaultDesktopViewWidth
              : desktopViewWidth.trim().endsWith("%") ? desktopViewWidth : desktopViewWidth + "%");

      if (showEnvUrl && envUrl != null) {
        report = report.replace("$enterUrl", envUrl);
      } else {
        report = report.replace("$enterUrl", "");
        report = report.replace("environmentRow", "environmentRow hidden");
      }

      if (showOsBrowserInfo && osBrowserInfo != null) {
        report = report.replace("$enterOsBrowserName", osBrowserInfo);
      } else {
        report = report.replace("$enterOsBrowserName", "");
        report = report.replace("OsBrowserRow", "OsBrowserRow hidden");
      }

      if (showExecutedBy && executedBy != null) {
        report = report.replace("$executedBy", executedBy);
      } else {
        report = report.replace("$executedBy", "");
        report = report.replace("executedByRow", "executedByRow hidden");
      }
      if (showTimeStamp) {
        report = report.replace("$enterTimeStamp", getCurrentTimestamp());
      } else {
        report = report.replace("$enterTimeStamp", "");
        report = report.replace("TimeStampRow", "TimeStampRow hidden");
      }
      return report;
    } catch (Exception e) {
      log.error("Template File NOT found.. Please raise a bug to the developer.");
      return null;
    }
  }

  /**
   * This method reads the template file content
   *
   * @param fileName
   *            Name of the template file
   * @return String content of the file
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private String loadFileContent(String fileName) throws IOException {
    try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName)) {
      if (inputStream == null) {
        log.error("Template File {} NOT found.. Please raise a bug to the developer.", fileName);
        return null;
      }

      // Read the InputStream into a String
      try (Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8)) {
        StringBuilder content = new StringBuilder();
        while (scanner.hasNextLine()) {
          content.append(scanner.nextLine()).append("\n");
        }
        return content.toString(); // Return file content as String
      }
    }
  }

  /**
   * This method writes the actual test results to the report and generates the
   * final report
   *
   * @param resultMap
   *            Map of Feature (URI) as key and Map of Scenario and Status as
   *            value
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  public void parseResultToReport(Map<String, Map<String, Status>> resultMap) {
    if (!resultMap.isEmpty()) {
      String reportTemplate = constructReport();
      if (reportTemplate != null) {
        // HTML base code to add feature details to report
        String featureDetailsBase = StringUtils.substringBetween(reportTemplate, "FeatureDetailsStart",
            "FeatureDetailsEnd");
        // HTML base code to add test case details to report
        String tcDetailsBase = StringUtils.substringBetween(reportTemplate, "TcDetailsStart", "TcDetailsEnd");
        // HTML base code to add subtotal details to report
        String subtotalBase = StringUtils.substringBetween(reportTemplate, "SubTotalDetailsStart",
            "SubTotalDetailsEnd");

        // HTML base code to add feature details to report after removing test case base
        // code
        featureDetailsBase = featureDetailsBase.replace("TcDetailsStart" + tcDetailsBase + "TcDetailsEnd",
            "$insertTcDetailsHere");

        // Remove base code, so that real execution data can be added later
        reportTemplate = reportTemplate
            .replace("TcDetailsStart" + tcDetailsBase + "TcDetailsEnd", "$insertTcDetailsHere")
            .replace("SubTotalDetailsStart" + subtotalBase + "SubTotalDetailsEnd",
                "$insertSubTotalDetailsHere")
            .replace("FeatureDetailsStart" + featureDetailsBase + "FeatureDetailsEnd",
                "$insertFeatureDetailsHere");

        // Variable to store HTML code for feature details
        StringBuilder featureDetails = new StringBuilder();
        // Variable to store HTML code for subtotal
        String overallDetails;

        // Double formatter
        DecimalFormat formatter = new DecimalFormat("0.00");

        // Variables to store subtotal numeric data
        int overallCount, overallPassCount = 0, overallFailCount = 0;
        String overallPassPercent;

        // Variable to be used for hiding and showing tc details for each feature
        int featureNo = 0;
        // Iterate through each feature
        for (Map.Entry<String, Map<String, Status>> mapElement : resultMap.entrySet()) {
          // Increase the feature number by 1 for each feature
          featureNo++;

          // Variable to store HTML code for test case details
          StringBuilder tcDetails = new StringBuilder();

          // Variables to store feature wise numeric data
          int totalCount = 0, passCount = 0, failCount = 0;
          String passPercent;

          // Name of the feature
          boolean useNameFromFeatureFile = Boolean
              .parseBoolean(getPropertyValue("use.feature.name.from.feature.file"));
          FeatureInfo featureInfo = featureFiles.get(mapElement.getKey());
          String featureFileName = featureInfo.featureFileName;
          String featurePackage = featureInfo.packageName;
          String featureNameDefinedInFeatureFile = featureInfo.featureNameDefinedInFeatureFile;
          boolean usePackageName = Boolean.parseBoolean(getPropertyValue("use.package.name")) || useDefault;
          String featureNameForReport = useNameFromFeatureFile
              ? featureNameDefinedInFeatureFile
              : usePackageName
                  ? (featurePackage.isEmpty()
                      ? featureFileName
                      : featurePackage + " - " + featureFileName)
                  : featureFileName;

          // Test Users
          String userName = !testUsers.isEmpty()
              ? testUsers.get(mapElement.getKey()) == null
                  ? "NA"
                  : testUsers.get(mapElement.getKey()).get(0)
              : getPropertyValue("test.user");
          String password = !testUsers.isEmpty()
              ? testUsers.get(mapElement.getKey()) == null
                  ? "NA"
                  : testUsers.get(mapElement.getKey()).get(1)
              : getPropertyValue("test.password");

          // Test Cases for the current feature
          Map<String, Status> scenarios = mapElement.getValue();
          int scenarioCounter = 1;
          for (Map.Entry<String, Status> scenario : scenarios.entrySet()) {
            String tcKey = "SC-" + String.format("%03d", scenarioCounter);
            String tcName = scenario.getKey();
            Status scenarioResult = scenario.getValue();
            String tcStatus;
            if (scenarioResult == Status.PASSED) {
              // If status is PASSED, then increase pass count and set green color for HTML
              // code
              passCount++;
              tcStatus = "green";
            } else {
              // If status is FAILED, then increase fail count and set red color for HTML code
              failCount++;
              tcStatus = "red";
            }
            // calculate total count
            totalCount = passCount + failCount;

            // Use test case base HTML code, add real data and frame the actual code to be
            // written to an HTML report
            tcDetails.append(tcDetailsBase.replace("$tcKey", tcKey).replace("$tcName", tcName)
                .replace("$tcStatus", tcStatus));
            scenarioCounter++;
          }

          // Increase the subtotal pass, fail and total counts
          overallPassCount = overallPassCount + passCount;
          overallFailCount = overallFailCount + failCount;

          // Calculate pass percentage for feature
          passPercent = formatter.format(((double) passCount / (double) totalCount) * 100) + "%";

          // Use feature base HTML code, add real data and frame the actual code to be
          // written to an HTML report
          featureDetails.append(featureDetailsBase.replace("$insertTcDetailsHere", tcDetails)
              .replace("$featureName", featureNameForReport)
              .replace("$username", userName != null ? userName : defaultUserName)
              .replace("$password", password != null ? password : defaultPassword)
              .replace("$passCount", String.valueOf(passCount))
              .replace("$failCount", String.valueOf(failCount))
              .replace("$totalCount", String.valueOf(totalCount))
              .replace("$featureStatus", failCount == 0 ? "green" : "red")
              .replace("$featurePassPercent", passPercent)
              .replace("$featureNo", String.valueOf(featureNo)));

          // Log feature details to console
          log.info("{}: Total Count: {} | Pass Count: {} | Fail Count: {} | Percentage Pass: {}%",
              featureNameForReport, totalCount, passCount, failCount, passPercent);
        }

        // Calculate total count for subtotal
        overallCount = overallPassCount + overallFailCount;

        // Calculate subtotal pass percentage for overall result
        overallPassPercent = formatter.format(((double) overallPassCount / (double) overallCount) * 100) + "%";

        // Use subtotal base HTML code, add real data and frame the actual code to be
        // written to an HTML report
        overallDetails = subtotalBase.replace("$overallPassCount", String.valueOf(overallPassCount))
            .replace("$overallFailCount", String.valueOf(overallFailCount))
            .replace("$overallCount", String.valueOf(overallCount))
            .replace("$overallStatus", overallFailCount == 0 ? "green" : "red")
            .replace("$overallPassPercent", overallPassPercent);

        // Log subtotal details to console
        log.info(
            "Overall Count: {} | Overall Pass Count: {} | Overall Fail Count: {} | Overall Pass Percent: {}%",
            overallCount, overallPassCount, overallFailCount, overallPassPercent);

        // Finalise the HTML code to be written to report using the tc, feature and
        // overall details generated above
        reportTemplate = reportTemplate.replace("$insertFeatureDetailsHere", featureDetails)
            .replace("$insertSubTotalDetailsHere", overallDetails)
            .replace("$overallPassCount", String.valueOf(overallPassCount))
            .replace("$overallFailCount", String.valueOf(overallFailCount))
            .replace("$overallCount", String.valueOf(overallCount));

        String filePathProvided = getPropertyValue("report.file.path");
        // Create a new HTML file named SummaryReport.html
        File newHtmlFile = new File(filePathProvided == null ? defaultReportPath : filePathProvided);
        try {
          // Write the HTML finalized code above to the new HTML file created
          FileUtils.writeStringToFile(newHtmlFile, reportTemplate, Charset.defaultCharset());
        } catch (IOException e) {
          // Log error to console
          log.error("Something went wrong... Could NOT generate report. Please contact the developer...", e);
        }
      } else {
        log.error("Could NOT generate Report... Please contact the developer...");
      }
    } else {
      log.info("No Results Found. Could NOT generate Report...");
    }
  }

  /**
   * This method returns the timestamp of the test execution
   *
   * @return Timestamp of test execution
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private String getCurrentTimestamp() {
    String timeFormat = getPropertyValue("time.stamp.format");
    String timeZone = getPropertyValue("time.zone");
    SimpleDateFormat sdf = new SimpleDateFormat(timeFormat == null ? defaultTimeStampFormat : timeFormat);
    sdf.setTimeZone(TimeZone.getTimeZone(timeZone == null ? defaultTimeZone : timeZone));
    return sdf.format(new Date());
  }
}