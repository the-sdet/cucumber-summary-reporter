# Cucumber Summary Reporter

[![Maven Central](https://img.shields.io/maven-central/v/io.github.the-sdet/cucumber-summary-reporter)](https://search.maven.org/artifact/io.github.the-sdet/cucumber-summary-reporter)
[![javadoc](https://javadoc.io/badge2/io.github.the-sdet/cucumber-summary-reporter/javadoc.svg)](https://javadoc.io/doc/io.github.the-sdet/cucumber-summary-reporter)

## A Simple Library that generates a Cucumber Test Summary

This library generates a summary report for Cucumber test results, which can be integrated as a plugin/adapter for Cucumber. The report provides a concise overview of test execution, making it easier to analyze test stats or distribute a simplified summary, for example, via email.


### Cucumber Summary Report — All Pass - Scenario Wise Credentials
<img src="src/main/resources/img/scenario-wise-cred.png" alt="Cucumber Summary Report - All Pass"/>

### Cucumber Summary Report — All Pass Extended - Scenario Wise Credentials
<img src="src/main/resources/img/scenario-wise-cred-extended.png" alt="Cucumber Summary Report - All Pass"/>

### Cucumber Summary Report — All Pass - Feature Wise Credentials
<img src="src/main/resources/img/feature-wise-cred.png" alt="Cucumber Summary Report - All Pass"/>

### Cucumber Summary Report — All Pass Extended - Feature Wise Credentials
<img src="src/main/resources/img/feature-wise-cred-extended.png" alt="Cucumber Summary Report - All Pass"/>

### Cucumber Summary Report — Mixed Result (Pass & Fail)
<img src="src/main/resources/img/pass-fail.png" alt="Cucumber Summary Report - Pass & Fail"/>

### Cucumber Summary Report — Mixed Result Extended (Pass & Fail)
<img src="src/main/resources/img/pass-fail-extended.png" alt="Cucumber Summary Report - Pass & Fail"/>

### Cucumber Summary Report — Mixed Result (Pass & Fail & Skip)
<img src="src/main/resources/img/pass-fail-skip.png" alt="Cucumber Summary Report - Pass & Fail"/>

### Cucumber Summary Report — Mixed Result Extended (Pass & Fail & Skip)
<img src="src/main/resources/img/pass-fail-skip-extended.png" alt="Cucumber Summary Report - Pass & Fail & Skip"/>

### Cucumber Summary Report — Filter (Exclude Skip)
<img src="src/main/resources/img/filter-exclude-skip.png" alt="Cucumber Summary Report - Filter (Exclude Skip)"/>

### Cucumber Summary Report — Filter (Only Fail)
<img src="src/main/resources/img/filter-only-fail.png" alt="Cucumber Summary Report - Filter (Only Fail)"/>

### Cucumber Summary Report — Filter (Exclude Pass)
<img src="src/main/resources/img/filter-exclude-pass.png" alt="Cucumber Summary Report - Filter (Exclude Pass)"/>

### Cucumber Summary Report — Tablet View
<img src="src/main/resources/img/tablet.png" alt="Cucumber Summary Report - Tablet View"/>

### Cucumber Summary Report — Mobile View
<img src="src/main/resources/img/mobile.png" alt="Cucumber Summary Report - Mobile View"/>

## Features
* Generates a quick summary of Cucumber test results.
* Doughnut Chart gives a visual representation of an Overall result
* Provides statistics on test runs, including total, passed, failed scenarios.
* Supports output formats suitable for presentations or email reports.
* Easy integration as a Cucumber plugin/adapter.
* Optimised for Desktop, Tablet and Mobile View
* Download Report in PNG and Excel

## Usage

Cucumber Summary Reporter requires Cucumber 7 or newer.

To use Cucumber Summary Reporter, add the following dependency to your Maven project:

```xml

<dependency>
    <groupId>io.github.the-sdet</groupId>
    <artifactId>cucumber-summary-reporter</artifactId>
    <version>2.0.4</version>
</dependency>
```

After adding the dependency, use `io.github.the_sdet.adapter.CucumberSummaryReporter:` as plugin for Cucumber test runner.

If you are using JUnit4, you might need to add it under @CucumberOptions and plugin like below
```java
@RunWith(Cucumber.class)
@CucumberOptions(
features = "src/test/resources/features", glue = {"stepdefinitions", "hooks"},
plugin = {"pretty", "html:testReports/CucumberReport.html", "io.github.the_sdet.adapter.CucumberSummaryReporter:"},
tags = "not @ignore"
)
public class TestRunner {

}
```
Or if you are using JUnit5, you might need to add this property to your `junit-platform.properties`

`cucumber.plugin=pretty, html:testReports/CucumberReport.html, io.github.the_sdet.adapter.CucumberSummaryReporter:`

### Configs
By Default, the report will show only Report title and Execution Timestamp as metadata along with the results.

Other info like Environment, OS & Browser, Executed By won't appear by default but can be configured.

For the configuration, create a file named `cucumber-summary.properties` inside `test/resources`.

Below are the Properties that can be defined for the report configuration 
```properties
report.title=Cucumber Test Summary — The SDET

show.env=true
env.url=https://github.com/the-sdet

show.os.browser=true
os.browser=Windows (Chrome)

show.executed.by=true
executed.by=Pabitra

report.file.path=testReports/CucumberSummary.html

# in case the features are present under some additional folders under features folder,
# and you want that folder name to be added as prefix with the feature file name for better readability
# E.g., if Homepage is the folder; Components, Navigation are features, then
# when true it will appear like Homepage - Components, Homepage - Navigation and when false Components and Navigation
use.package.name=true

# in case you want to use the feature name defined inside the feature file to appear in the report instead of file name
# E.g., if the file name is Homepage.feature and the feature name inside the file is 'Homepage Tests'
# when true, the report will display Homepage Tests and Homepage when false
use.feature.name.from.feature.file=false

# Below config will show the execution timestamp at the top of the report
show.execution.timestamp=true
time.stamp.format=EEEE, dd-MMM-yyyy HH:mm:ss z
time.zone=IST

# Below config will show the test duration at the top of the report
show.execution.duration=true

# in case you want to add credentials to the report, make use of the below properties.
# when true, the report will display credentials, else it won't
# default configuration is set to 'false'
display.credentials=true
# E.g., 
# if you are using different credentials for each scenario, you can go for option=scenario
# If you use the same credentials for all scenarios for a feature, you can go for option=feature
# default configuration is set to 'feature'
credentials.display.option=scenario

# The Max desktop view is set to 1280 px (1320 - 20 - 20) for better look and feel.
# You can change it with the below property (don't include px at the end; use just numbers)
desktop.view.max.width=1320

# Below properties can be used to change the look and feel of the report
heading.background.color=#23436a
heading.color=#ffffff

subtotal.background.color=#03455b
subtotal.color=#ffffff

scenario.table.heading.background.color=#efefef
scenario.table.heading.color=#000000
```
### Default Configs
* The Report title will appear as `Cucumber Test Summary`
* Report Location will be - `testReports/CucumberTestSummary.html`
* Timestamp format will be - `EEEE, dd-MMM-yyyy HH:mm:ss z`
* Timezone for the timestamp will be `IST`
* Credential display will be `false`
* Credential display option will be `feature`
* Heading Background Color will be - `#23436a`
* Heading Color will be - `#ffffff`
* Subtotal Background Color will be - `#cbcbcb`
* Subtotal Color will be - `#090909`
* Scenario table Background Color will be - `#efefef`
* Scenario table Color will be - `#090909`
* Heading Background color will be - `#23436a`

## Displaying Test Credentials in Test Report
From v2.0.3 onwards, 'test.user', 'test.password' properties have been removed. 
In case you were using it already, pls change your implementation to use the below methods 
```java
@After
public void afterTest(Scenario scenario) {
    //In case scenarios in each feature use shared credentials
    registerTestUserForFeature(scenario, "contact.the.sdet@gmail.com", "cucumberSummary@123");
    
    //Or below if you are setting credentials for each scenario
    registerTestUserForScenario(scenario, "contact.the.sdet@gmail.com", "cucumberSummary@123");
}

/*
    After hook is just an example, you can call these methods anywhere during your execution.
    This will only work if properties are set 
        'display.credentials=true'
        'credentials.display.option=scenario' or 'credentials.display.option=feature'
*/
```

## Override Properties at Runtime
Sometimes, we need to update values from runtime, E.g., depending on env,
we use different URLs and that might NOT be possible to provide it properly in the properties file itself.
Right???
That's sorted.
Replace the * in the below command with the expected property key, and it works like charm.

```code
System.setProperty("cucumber.summary.*","new_value");
```
## Need Summary Data for some additional logging or reporting purposes?
You can use the below code in your afterAll hook and get those details.
This will give you a Map of Features with the scenarios in it along with their status
```java
SummaryData summaryData = CucumberSummaryReporter.getSummaryData();
Map<String, Map<String, Status>> results = summaryData.results;
```
## Example of Usage
An Example of Usage of the Library can be found here: https://github.com/pabitra-qa/UsingCucumberSummaryReporter

## Authors

<a href="https://github.com/the-sdet"><img align="center" src="https://github.githubassets.com/assets/GitHub-Mark-ea2971cee799.png" alt="pabitra-qa" height="40" width="40" /></a>
[@the-sdet](https://github.com/the-sdet)

<a href="https://github.com/the-sdet"><img align="center" src="https://github.githubassets.com/assets/GitHub-Mark-ea2971cee799.png" alt="pabitra-qa" height="40" width="40" /></a>
[@pabitra-qa](https://github.com/pabitra-qa)

## 🚀 About Me

I'm a dedicated and passionate Software Development Engineer in Test (SDET) trying to help the community in focusing on 
building great automation frameworks rather than writing the same utilities again and again and again...

## Connect With Me

<a href="https://linkedin.com/in/pswain7"><img align="center" src="https://content.linkedin.com/content/dam/me/business/en-us/amp/brand-site/v2/bg/LI-Logo.svg.original.svg" alt="pabitra-qa" height="35"/></a>
&nbsp; <a href="https://pabitra-qa.github.io/"><img align="center" src="https://chromeenterprise.google/static/images/chrome-logo.svg" height="40" width="40"/></a>

## Feedback

If you have any feedback, please reach out to us at [contact.the.sdet@gmail.com](mailto:contact.the.sdet@gmail.com).

[//]: # (<img align="center" src="https://pabitra-qa.github.io/dp.png" width="200px"/>)