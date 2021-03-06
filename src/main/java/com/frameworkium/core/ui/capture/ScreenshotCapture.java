package com.frameworkium.core.ui.capture;

import static com.frameworkium.core.common.properties.Property.CAPTURE_URL;
import static com.frameworkium.core.common.properties.Property.SUT_NAME;
import static com.frameworkium.core.common.properties.Property.SUT_VERSION;
import static org.apache.http.HttpStatus.SC_CREATED;

import com.frameworkium.core.common.properties.Property;
import com.frameworkium.core.ui.UITestLifecycle;
import com.frameworkium.core.ui.capture.model.Command;
import com.frameworkium.core.ui.capture.model.message.CreateExecution;
import com.frameworkium.core.ui.capture.model.message.CreateScreenshot;
import com.frameworkium.core.ui.driver.Driver;
import com.frameworkium.core.ui.driver.DriverSetup;
import com.frameworkium.core.ui.driver.remotes.BrowserStack;
import com.frameworkium.core.ui.driver.remotes.Sauce;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

/**
 * Takes and sends screenshots to "Capture" asynchronously.
 */
public class ScreenshotCapture {

  private static final Logger logger = LogManager.getLogger();

  /**
   * Shared Executor for async sending of screenshot messages to capture.
   */
  private static final ExecutorService executorService =
      Executors.newFixedThreadPool(4);

  private String testID;
  private String executionID;

  public ScreenshotCapture(String testID) {
    logger.debug("About to initialise Capture execution for " + testID);
    this.testID = testID;
    this.executionID = createExecution(new CreateExecution(testID, getNode()));
    logger.debug("Capture executionID=" + executionID);
  }

  public static boolean isRequired() {
    boolean allCapturePropertiesSpecified = CAPTURE_URL.isSpecified()
        && SUT_NAME.isSpecified()
        && SUT_VERSION.isSpecified();
    return allCapturePropertiesSpecified && !Driver.isNative();
  }

  /**
   * Waits up to 2 minutes to send any remaining Screenshot messages.
   */
  public static void processRemainingBacklog() {

    executorService.shutdown();

    if (!isRequired()) {
      return;
    }

    logger.info("Processing remaining Screenshot Capture backlog...");
    boolean timeout;
    try {
      timeout = !executorService.awaitTermination(2, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      throw new IllegalStateException(e);
    }
    if (timeout) {
      logger.error("Shutdown timed out. "
          + "Some screenshots might not have been sent.");
    } else {
      logger.info("Finished processing backlog.");
    }
  }

  private String createExecution(CreateExecution createExecution) {
    try {
      return getRequestSpec()
          .body(createExecution)
          .when()
          .post(CaptureEndpoint.EXECUTIONS.getUrl())
          .then().statusCode(SC_CREATED)
          .extract().path("executionID").toString();
    } catch (Exception e) {
      logger.error("Unable to create Capture execution.", e);
      return null;
    }
  }

  private RequestSpecification getRequestSpec() {
    return RestAssured.given()
        .relaxedHTTPSValidation()
        .contentType(ContentType.JSON);
  }

  private String getNode() {
    String node = "n/a";
    if (DriverSetup.useRemoteDriver()) {
      node = getRemoteNode(node);
    } else {
      try {
        node = InetAddress.getLocalHost().getCanonicalHostName();
      } catch (UnknownHostException e) {
        logger.debug("Failed to get local machine name", e);
      }
    }
    return node;
  }

  private String getRemoteNode(String defaultValue) {
    if (Sauce.isDesired()) {
      return "SauceLabs";
    } else if (BrowserStack.isDesired()) {
      return "BrowserStack";
    } else {
      try {
        return getRemoteNodeAddress();
      } catch (Exception e) {
        logger.warn("Failed to get node address of remote web driver");
        logger.debug(e);
      }
    }
    return defaultValue;
  }

  private String getRemoteNodeAddress() throws MalformedURLException {
    return RestAssured
        .get(getTestSessionURL())
        .then()
        .extract().jsonPath()
        .getString("proxyId");
  }

  private String getTestSessionURL() throws MalformedURLException {
    URL gridURL = new URL(Property.GRID_URL.getValue());
    return String.format(
        "%s://%s:%d/grid/api/testsession?session=%s",
        gridURL.getProtocol(),
        gridURL.getHost(),
        gridURL.getPort(),
        UITestLifecycle.get().getRemoteSessionId());
  }

  public void takeAndSendScreenshot(Command command, WebDriver driver) {
    takeAndSendScreenshotWithError(command, driver, null);
  }

  /**
   * Take and send a screenshot with an error message.
   */
  public void takeAndSendScreenshotWithError(
      Command command, WebDriver driver, String errorMessage) {

    if (executionID == null) {
      logger.error("Can't send Screenshot. "
          + "Capture didn't initialise execution for test: " + testID);
      return;
    }

    CreateScreenshot createScreenshotMessage =
        new CreateScreenshot(
            executionID,
            command,
            driver.getCurrentUrl(),
            errorMessage,
            getBase64Screenshot((TakesScreenshot) driver));
    addScreenshotToSendQueue(createScreenshotMessage);
  }

  private String getBase64Screenshot(TakesScreenshot driver) {
    return driver.getScreenshotAs(OutputType.BASE64);
  }

  private void addScreenshotToSendQueue(CreateScreenshot createScreenshotMessage) {
    executorService.execute(() -> sendScreenshot(createScreenshotMessage));
  }

  private void sendScreenshot(CreateScreenshot createScreenshotMessage) {
    logger.debug("About to send screenshot to Capture for {}", testID);
    try {
      getRequestSpec()
          .body(createScreenshotMessage)
          .when()
          .post(CaptureEndpoint.SCREENSHOT.getUrl())
          .then()
          .assertThat().statusCode(SC_CREATED);
      logger.debug("Sent screenshot to Capture for " + testID);
    } catch (Exception e) {
      logger.warn("Failed sending screenshot to Capture for " + testID);
      logger.debug(e);
    }
  }
}
