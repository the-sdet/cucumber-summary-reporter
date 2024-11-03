package io.github.the_sdet.adapter;

import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
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

  // Logger Object
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

  // Result Map to have Features, its Scenario and their results
  protected static final Map<String, Map<String, Status>> featureWiseResultMap = new ConcurrentHashMap<>();

  // Result Map to have Feature file metadata
  private static final Map<String, FeatureInfo> featureFiles = new ConcurrentHashMap<>();

  // To hold Runtime Test Users
  public static final Map<String, List<String>> testUsers = new ConcurrentHashMap<>();

  // Default Values
  private static final String defaultReportPath = "testReports/CucumberTestSummary.html";
  private static final String defaultReportTitle = "Cucumber Test Summary";
  private static final String defaultUserName = "---";
  private static final String defaultPassword = "---";
  private static final String defaultTimeStampFormat = "EEEE, dd-MMM-yyyy HH:mm:ss z";
  private static final String defaultTimeZone = "IST";
  private static final String defaultHeadingBgColor = "#66CCEE";
  private static final String defaultSubTotalBgColor = "#d7d8d8";

  private boolean useDefault = false;
  private boolean propertyLoaded = false;
  Properties prop;

  /**
   * Default Constructor
   * 
   * @param arg
   *            param
   *
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

  // HTML Strings for constructing the report
  private static final String HEAD = "<!DOCTYPE html>\n" + "<html>\n" + "\n" + "<head>\n"
      + "    <title>Automation Test Summary</title>\n" + "    <style>\n" + "        body {\n"
      + "            font-family: \"Helvetica Neue\", Helvetica, Arial, sans-serif;\n"
      + "            font-size: 14px;\n" + "        }\n" + "\n" + "        .container {\n"
      + "            display: flex;\n" + "            flex-direction: column;\n"
      + "            align-items: center;\n" + "            width: 100%;\n" + "            margin: 0 auto;\n"
      + "        }\n" + "\n" + "        .row {\n" + "            display: flex;\n"
      + "            justify-content: flex-start;\n" + "            align-items: center;\n"
      + "            flex-wrap: wrap;\n" + "            border-bottom: 1px solid gray;\n"
      + "            width: 80%;\n" + "        }\n" + "\n" + "        .span-container {\n"
      + "            display: flex;\n" + "            flex-basis: 95%;\n" + "        }\n" + "\n"
      + "        .span1 {\n" + "            flex-basis: 25%;\n" + "            text-align: left;\n"
      + "            padding: 10px;\n" + "            display: flex;\n" + "            align-items: center;\n"
      + "        }\n" + "\n" + "        .heading {\n" + "            background-color: $defaultHeadingBgColor;\n"
      + "            color: black;\n" + "            font-weight: bold;\n" + "        }\n" + "\n"
      + "        .subtotal {\n" + "            background-color: $defaultSubTotalBgColor;\n"
      + "            color: black;\n" + "            font-weight: bold;\n" + "        }\n" + "\n"
      + "        .span2 {\n" + "            flex-basis: 10%;\n" + "            text-align: left;\n"
      + "            padding: 10px;\n" + "            display: flex;\n" + "            justify-content: center;\n"
      + "            align-items: center;\n" + "        }\n" + "\n" + "        .inner-div {\n"
      + "            background-color: white;\n" + "            width: 100%;\n"
      + "            border-top: 1px solid gray;\n" + "            border-bottom: 1px solid gray;\n"
      + "        }\n" + "\n" + "        .arrow-button {\n" + "            background-color: white;\n"
      + "            border: none;\n" + "            padding: 5px;\n" + "            cursor: pointer;\n"
      + "            flex-basis: 5%;\n" + "        }\n" + "\n" + "        .arrow-button-dummy {\n"
      + "            background-color: white;\n" + "            border: none;\n" + "            padding: 5px;\n"
      + "            cursor: pointer;\n" + "            flex-basis: 5%;\n" + "            cursor: default;\n"
      + "        }\n" + "\n" + "        table.dataTable {\n" + "            width: 100%;\n"
      + "            border-collapse: collapse;\n" + "            table-layout: fixed;\n"
      + "            margin-top: 10px;\n" + "            margin-bottom: 10px;\n" + "            width: 100%;\n"
      + "            font-size: 12px;\n" + "        }\n" + "\n" + "        table.dataTable td {\n"
      + "            padding: 10px;\n" + "            border: 1px solid lightgray;\n" + "        }\n" + "\n"
      + "        table.dataTable tr.data-heading {\n" + "            background-color: #bfdff3;\n"
      + "            font-weight: bold;\n" + "        }\n" + "\n" + "        /*\n"
      + "        table.dataTable td:first-child {\n" + "            width: 15%;\n" + "        }*/\n" + "\n"
      + "        table.dataTable td:nth-child(2) {\n" + "            width: 80%;\n" + "        }\n" + "\n"
      + "        table.dataTable td:last-child {\n" + "            width: 5%;\n"
      + "            text-align: center;\n" + "            vertical-align: middle;\n" + "        }\n" + "\n"
      + "        table.summary {\n" + "            border-collapse: collapse;\n" + "            width: 100%;\n"
      + "            margin-top: 20px;\n" + "            margin-bottom: 20px;\n" + "        }\n" + "\n"
      + "        table.summary th,\n" + "        td {\n" + "            text-align: left;\n"
      + "            padding: 8px;\n" + "            border: 1px solid gray;\n" + "            width: 10%;\n"
      + "        }\n" + "\n" + "        table.summary th {\n"
      + "            background-color: $defaultHeadingBgColor;\n" + "            color: black\n" + "        }\n"
      + "\n" + "        a, a:visited {\n" + "            text-decoration: none;\n"
      + "            color: #2b64ff;\n" + "        }\n" + "        .circle {\n" + "            width: 15px;\n"
      + "            height: 15px;\n" + "            border-radius: 50%;\n" + "            position: relative;\n"
      + "            display: flex;\n" + "            align-items: center;\n" + "            margin: 0 auto;\n"
      + "        }\n" + "\n" + "        .circle-tc {\n" + "            width: 10px;\n"
      + "            height: 10px;\n" + "            border-radius: 50%;\n" + "            position: relative;\n"
      + "            display: flex;\n" + "            align-items: center;\n" + "            margin: 0 auto;\n"
      + "        }\n" + "\n" + "        .circle.red,\n" + "        .circle-tc.red {\n"
      + "            background-color: red;\n" + "        }\n" + "\n" + "        .circle.green,\n"
      + "        .circle-tc.green {\n" + "            background-color: green;\n" + "        }\n" + "\n"
      + "        .circle.orange,\n" + "        .circle-tc.orange {\n" + "            background-color: orange;\n"
      + "        }\n" + "\n" + "        .circle.grey,\n" + "        .circle-tc.grey {\n"
      + "            background-color: grey;\n" + "        }\n" + "\n" + "        .circle:hover::after {\n"
      + "            content: attr(data-label);\n" + "            position: absolute;\n"
      + "            left: 40px;\n" + "            top: 0;\n" + "            white-space: nowrap;\n"
      + "        }\n" + "\n" + "        @media (min-width:1024px) {\n" + "            .row {\n"
      + "                width: 80%;\n" + "            }\n" + "        }\n" + "\n"
      + "        @media (max-width:1024px) {\n" + "            .row {\n" + "                width: 100%;\n"
      + "            }\n" + "        }\n" + "    </style>\n" + "    <script>\n"
      + "        function toggleInnerDiv(rowId) {\n"
      + "            var innerDiv = document.getElementById(\"inner-div-\" + rowId);\n"
      + "            var arrowButton = document.getElementById(\"arrow-button-\" + rowId);\n"
      + "            var openSymbol = '\\u23CF'; // Eject symbol\n"
      + "            var closedSymbol = '\\u27A4'; // Rightwards arrow\n"
      + "            if (innerDiv.style.display === \"none\") {\n"
      + "                innerDiv.style.display = \"block\";\n"
      + "                arrowButton.innerText = openSymbol;\n" + "            } else {\n"
      + "                innerDiv.style.display = \"none\";\n"
      + "                arrowButton.innerText = closedSymbol;\n" + "            }\n" + "        }\n"
      + "    </script>\n" + "</head>";
  private static final String BODY_PART_ONE = "<body>\n" + "<div class=\"container\">\n" + "    <div class=\"row\">\n"
      + "        <table class=\"summary\">\n" + "            <tbody>";
  private static final String REPORT_TITLE = "<tr>\n"
      + "                <th class=\"top\" colspan=\"3\">$reportTitle</th>\n" + "            </tr>";
  private static final String SHOW_ENV = "<tr>\n" + "                <td class=\"top\">Environment:</td>\n"
      + "                <td class=\"summary-cell\"><a href=\"$enterUrl\"\n"
      + "                                            target=\"_blank\">$enterUrl</a></td>\n"
      + "            </tr>";
  private static final String SHOW_OS = "<tr>\n" + "                <td class=\"top\">OS &amp; Browser:</td>\n"
      + "                <td class=\"summary-cell\">$enterOsBrowserName</td>\n" + "            </tr>";
  private static final String SHOW_EXECUTED_BY = "<tr>\n" + "                <td class=\"top\">Executed By:</td>\n"
      + "                <td class=\"summary-cell\">$executedBy</td>\n" + "            </tr>";
  private static final String SHOW_TIMESTAMP = "<tr>\n"
      + "                <td class=\"top\">Execution Date &amp; Time:</td>\n"
      + "                <td class=\"summary-cell\">$enterTimeStamp</td>\n" + "            </tr>";
  private static final String BODY_PART_TWO = "</tbody>\n" + "        </table>\n" + "    </div>\n"
      + "    <div class=\"row heading\">\n" + "        <div class=\"span-container\">\n"
      + "            <span class=\"span1\">Feature Name</span>\n"
      + "            <span class=\"span1\">Test Credentials</span>\n"
      + "            <span class=\"span2\">Pass</span>\n" + "            <span class=\"span2\">Fail</span>\n"
      + "            <span class=\"span2\">Total</span>\n" + "            <span class=\"span2\">Status</span>\n"
      + "            <span class=\"span2\">Pass (%)</span>\n" + "        </div>\n" + "    </div>\n"
      + "    FeatureDetailsStart<div class=\"row\">\n" + "    <div class=\"span-container\">\n"
      + "        <span class=\"span1\">$featureName</span>\n"
      + "        <span class=\"span1\">$username<br />$password</span>\n"
      + "        <span class=\"span2\">$passCount</span>\n" + "        <span class=\"span2\">$failCount</span>\n"
      + "        <span class=\"span2\">$totalCount</span>\n" + "        <span class=\"span2\">\n"
      + "                    <div class=\"circle $featureStatus\"></div>\n" + "                </span>\n"
      + "        <span class=\"span2\">$featurePassPercent</span>\n" + "    </div>\n"
      + "    <button class=\"arrow-button\" onclick=\"toggleInnerDiv($featureNo)\" id=\"arrow-button-$featureNo\">&#x27A4;</button>\n"
      + "    <div class=\"inner-div\" id=\"inner-div-$featureNo\" style=\"display: none;\">\n"
      + "        <table class=\"dataTable\">\n" + "            <tr class=\"data-heading\">\n"
      + "                <td>Id</td>\n" + "                <td>Name</td>\n" + "                <td>Status</td>\n"
      + "            </tr>\n" + "            TcDetailsStart<tr>\n" + "            <td>$tcKey</td>\n"
      + "            <td>$tcName</td>\n" + "            <td>\n"
      + "                <div class=\"circle-tc $tcStatus\"></div>\n" + "            </td>\n"
      + "        </tr>TcDetailsEnd\n" + "        </table>\n" + "    </div>\n" + "</div>FeatureDetailsEnd\n"
      + "    <div class=\"row subtotal\">\n" + "        <div class=\"span-container\">\n"
      + "            <span class=\"span1\">Overall: </span>\n" + "            <span class=\"span1\"></span>\n"
      + "            SubTotalDetailsStart<span class=\"span2\">$overallPassCount</span>\n"
      + "            <span class=\"span2\">$overallFailCount</span>\n"
      + "            <span class=\"span2\">$overallCount</span>\n" + "            <span class=\"span2\">\n"
      + "                    <div class=\"circle $overallStatus\"></div>\n" + "                </span>\n"
      + "            <span class=\"span2\">$overallPassPercent</span>SubTotalDetailsEnd\n" + "        </div>\n"
      + "    </div>\n" + "</div>\n" + "</body>\n" + "\n" + "</html>";

  /**
   * This method generates the base for the summary report
   *
   * @author Pabitra Swain (contact.the.sdet@gmail.com)
   */
  private String constructReport() {
    StringBuilder report = new StringBuilder();
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
    String subTotalBgColor = getPropertyValue("subtotal.background.color");
    String head = HEAD.replace("$reportTitle", reportTitle)
        .replace("$defaultHeadingBgColor", headingBgColor == null ? defaultHeadingBgColor : headingBgColor)
        .replace("$defaultSubTotalBgColor", subTotalBgColor == null ? defaultSubTotalBgColor : subTotalBgColor);
    report.append(head).append(BODY_PART_ONE).append(REPORT_TITLE.replace("$reportTitle", reportTitle))
        .append(showEnvUrl && envUrl != null ? SHOW_ENV.replace("$enterUrl", envUrl) : "")
        .append(showOsBrowserInfo && osBrowserInfo != null
            ? SHOW_OS.replace("$enterOsBrowserName", osBrowserInfo)
            : "")
        .append(showExecutedBy && executedBy != null ? SHOW_EXECUTED_BY.replace("$executedBy", executedBy) : "")
        .append(showTimeStamp ? SHOW_TIMESTAMP.replace("$enterTimeStamp", getCurrentTimestamp()) : "")
        .append(BODY_PART_TWO);
    return report.toString();
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
    // This is the main HTML code that will be manipulated and written to an output
    // file.
    String reportTemplate = constructReport();

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
        .replace("SubTotalDetailsStart" + subtotalBase + "SubTotalDetailsEnd", "$insertSubTotalDetailsHere")
        .replace("FeatureDetailsStart" + featureDetailsBase + "FeatureDetailsEnd", "$insertFeatureDetailsHere");

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
              ? (featurePackage.isEmpty() ? featureFileName : featurePackage + " - " + featureFileName)
              : featureFileName;

      // Test Users
      String userName = !testUsers.isEmpty()
          ? testUsers.get(mapElement.getKey()) == null ? "NA" : testUsers.get(mapElement.getKey()).get(0)
          : getPropertyValue("test.user");
      String password = !testUsers.isEmpty()
          ? testUsers.get(mapElement.getKey()) == null ? "NA" : testUsers.get(mapElement.getKey()).get(1)
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
        tcDetails.append(tcDetailsBase.replace("$tcKey", tcKey).replace("$tcName", tcName).replace("$tcStatus",
            tcStatus));
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
          .replace("$passCount", String.valueOf(passCount)).replace("$failCount", String.valueOf(failCount))
          .replace("$totalCount", String.valueOf(totalCount))
          .replace("$featureStatus", failCount == 0 ? "green" : "red")
          .replace("$featurePassPercent", passPercent).replace("$featureNo", String.valueOf(featureNo)));

      // Log feature details to console
      log.info("{}: Total Count: {} | Pass Count: {} | Fail Count: {} | Percentage Pass: {}%",
          featureNameForReport, totalCount, passCount, failCount, passPercent);
    }

    // Calculate total count for subtotal
    overallCount = overallPassCount + overallFailCount;

    // Calculate subtotal pass percentage for overall result
    overallPassPercent = formatter.format(((double) overallPassCount / (double) overallCount) * 100) + "%";

    // Use subtotal base HTML code, add real data and frame the actual code to be
    // written to HTML report
    overallDetails = subtotalBase.replace("$overallPassCount", String.valueOf(overallPassCount))
        .replace("$overallFailCount", String.valueOf(overallFailCount))
        .replace("$overallCount", String.valueOf(overallCount))
        .replace("$overallStatus", overallFailCount == 0 ? "green" : "red")
        .replace("$overallPassPercent", overallPassPercent);

    // Log subtotal details to console
    log.info("Overall Count: {} | Overall Pass Count: {} | Overall Fail Count: {} | Overall Pass Percent: {}%",
        overallCount, overallPassCount, overallFailCount, overallPassPercent);

    // Finalise the HTML code to be written to report using the tc, feature and
    // overall details generated above
    reportTemplate = reportTemplate.replace("$insertFeatureDetailsHere", featureDetails)
        .replace("$insertSubTotalDetailsHere", overallDetails);

    String filePathProvided = getPropertyValue("report.file.path");
    // Create a new HTML file named SummaryReport.html
    File newHtmlFile = new File(filePathProvided == null ? defaultReportPath : filePathProvided);
    try {
      // Write the HTML finalized code above to the new HTML file created
      FileUtils.writeStringToFile(newHtmlFile, reportTemplate, Charset.defaultCharset());
    } catch (IOException e) {
      // Log error to console
      log.error("Something went wrong... Could NOT write to report...", e);
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
    sdf.setTimeZone(TimeZone.getTimeZone(timeZone == null ? defaultTimeStampFormat : timeZone));
    return sdf.format(new Date());
  }
}