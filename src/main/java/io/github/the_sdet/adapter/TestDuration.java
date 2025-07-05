package io.github.the_sdet.adapter;

import java.time.Duration;
import java.time.Instant;

import static io.github.the_sdet.adapter.CucumberSummaryReporter.log;

/**
 * Handles Test Duration Management for the Test.
 *
 * <p>
 * This class provides tracking of test start and end times It calculates and
 * formats the total duration of test execution in a human-readable format.
 * </p>
 *
 * <p>
 * Example output formats:
 * </p>
 * <ul>
 * <li>20 sec</li>
 * <li>3 min 5 sec</li>
 * <li>1 hr 20 min 5 sec</li>
 * </ul>
 *
 * @author Pabitra Swain (contact.the.sdet@gmail.com)
 */
class TestDuration {

  private static Instant startTime = null;
  private static Instant endTime = null;

  /**
   * Marks the start time of the test execution. This should be called at the very
   * beginning of the test run.
   */
  public static void markStart() {
    startTime = Instant.now();
    log.info("Test Started at {}", startTime);
  }

  /**
   * Marks the end time of the test execution. This should be called at the very
   * end of the test run.
   */
  public static void markEnd() {
    endTime = Instant.now();
    log.info("Test Completed at {}", endTime);
  }

  /**
   * Returns the formatted duration between the marked start and end times. If
   * either is missing, returns a placeholder (em dash).
   *
   * @return formatted duration string like "1 hr 20 min 5 sec" or "—" if
   *         unavailable
   */
  public static String getDuration() {
    if (startTime == null || endTime == null)
      return "—";

    Duration duration = Duration.between(startTime, endTime);
    return formatDuration(duration);
  }

  /**
   * Formats the given duration into a string like: "1 hr 20 min 5 sec", "3 min 2
   * sec", or "15 sec".
   *
   * @param duration
   *            the {@link Duration} to format
   * @return a human-readable duration string
   */
  public static String formatDuration(Duration duration) {
    long seconds = duration.getSeconds();
    long hours = seconds / 3600;
    long minutes = (seconds % 3600) / 60;
    long secs = seconds % 60;

    StringBuilder sb = new StringBuilder();
    if (hours > 0)
      sb.append(hours).append(" hr ");
    if (minutes > 0 || hours > 0)
      sb.append(minutes).append(" min ");
    sb.append(secs).append(" sec");
    String formattedDuration = sb.toString().trim();
    log.info("Test Duration: {}", formattedDuration);
    return formattedDuration;
  }
}