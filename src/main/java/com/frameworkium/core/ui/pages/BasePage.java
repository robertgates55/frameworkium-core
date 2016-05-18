package com.frameworkium.core.ui.pages;

import com.frameworkium.core.common.properties.Property;
import com.frameworkium.core.common.reporting.allure.AllureLogger;
import com.frameworkium.core.ui.annotations.ForceVisible;
import com.frameworkium.core.ui.annotations.Invisible;
import com.frameworkium.core.ui.annotations.Visible;
import com.frameworkium.core.ui.capture.model.Command;
import com.frameworkium.core.ui.tests.BaseTest;
import com.google.inject.Inject;
import com.paulhammant.ngwebdriver.NgWebDriver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import ru.yandex.qatools.htmlelements.element.HtmlElement;
import ru.yandex.qatools.htmlelements.element.TypifiedElement;
import ru.yandex.qatools.htmlelements.loader.HtmlElementLoader;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.List;

import static org.openqa.selenium.support.ui.ExpectedConditions.visibilityOfAllElements;

public abstract class BasePage<T extends BasePage<T>> {

    @Inject
    protected WebDriver driver;
    @Inject
    protected WebDriverWait wait;

    protected final Logger logger = LogManager.getLogger(this);

    private NgWebDriver ngDriver;

    /**
     * @return Returns the current page object.
     * Useful for e.g. MyPage.get().then().doSomething();
     */
    @SuppressWarnings("unchecked")
    public T then() {
        return (T) this;
    }

    /**
     * @return Returns the current page object.
     * Useful for e.g. MyPage.get().then().with().aComponent().clickHome();
     */
    @SuppressWarnings("unchecked")
    public T with() {
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T get() {
        HtmlElementLoader.populatePageObject(this, driver);
        try {
            if (isPageAngularJS()) {
                waitForAngularRequestsToFinish();
            }

            waitForVisibleAndInvisibleElements(this);

            try {
                AllureLogger.logToAllure("Page '" + this.getClass().getName() + "' successfully loaded");
                if (Property.CAPTURE_URL.isSpecified())
                    BaseTest.getCapture().takeAndSendScreenshot(
                            new Command("load", null, this.getClass().getName()), driver, null);
            } catch (Exception e) {
                logger.error("Error logging page load, but loaded successfully", e);
            }
        } catch (IllegalAccessException e) {
            logger.error("Error while waiting for page " + this.getClass().getName() + " to load", e);
            throw new RuntimeException(e); // don't bury errors
        }
        return (T) this;
    }

    public T get(String url) {
        driver.get(url);
        return get();
    }

    public T get(long timeout) {
        wait = new WebDriverWait(driver, timeout);
        return get();
    }

    public T get(String url, long timeout) {
        wait = new WebDriverWait(driver, timeout);
        return get(url);
    }

    private void waitForVisibleAndInvisibleElements(Object pageObject)
            throws IllegalAccessException {

        for (Field field : pageObject.getClass().getDeclaredFields()) {
            for (Annotation annotation : field.getDeclaredAnnotations()) {

                field.setAccessible(true);
                Object obj = field.get(pageObject);

                if (annotation instanceof Visible) {
                    waitForObjectToBeVisible(obj);
                } else if (annotation instanceof Invisible) {
                    waitForObjectToBeInvisible(obj);
                } else if (annotation instanceof ForceVisible) {
                    if (obj instanceof HtmlElement) {
                        WebElement e = ((HtmlElement) obj).getWrappedElement();
                        forceVisible(e);
                    } else if (obj instanceof TypifiedElement) {
                        WebElement e = ((TypifiedElement) obj).getWrappedElement();
                        forceVisible(e);
                    } else if (obj instanceof WebElement) {
                        forceVisible((WebElement) obj);
                    }
                }
            }
        }
    }

    private void waitForObjectToBeVisible(Object obj)
            throws IllegalAccessException {

        // Checks for @Visible tags inside an HtmlElement Component
        if (obj instanceof HtmlElement) {
            logger.debug("Checking for visible elements inside HtmlElement");
            waitForVisibleAndInvisibleElements(obj);
        }

        // This handles Lists of WebElements e.g. List<WebElement>
        if (obj instanceof List) {
            //TODO - where to handle List<Link>, for example?
            try {
                wait.until(visibilityOfAllElements((List<WebElement>) obj));
            } catch (StaleElementReferenceException serex) {
                logger.info("Caught StaleElementReferenceException in waitForObjectToBeVisible");
                tryToEnsureWeHaveUnloadedOldPageAndNewPageIsReady();
                wait.until(visibilityOfAllElements((List<WebElement>) obj));
            } catch (ClassCastException ccex) {
                logger.debug("Caught ClassCastException in waitForObjectToBeVisible - " +
                        "will try to get the first object in the List instead");
                obj = ((List<Object>) obj).get(0);
                waitForObjectToBeVisible(obj);
            }
        } else {
            WebElement element;
            if (obj instanceof TypifiedElement) {
                element = ((TypifiedElement) obj).getWrappedElement();
            } else if (obj instanceof WebElement) {
                element = (WebElement) obj;
            } else {
                throw new IllegalArgumentException(
                        "Only elements of type TypifiedElement, WebElement or " +
                                "List<WebElement> are supported by @Visible.");
            }
            try {
                wait.until(ExpectedConditions.visibilityOf(element));
            } catch (StaleElementReferenceException e) {
                logger.info("Caught StaleElementReferenceException in waitForObjectToBeVisible");
                tryToEnsureWeHaveUnloadedOldPageAndNewPageIsReady();
                wait.until(ExpectedConditions.visibilityOf(element));
            }
        }
    }

    private void waitForObjectToBeInvisible(Object obj)
            throws IllegalArgumentException {

        WebElement element;
        if (obj instanceof TypifiedElement) {
            element = ((TypifiedElement) obj).getWrappedElement();
        } else if (obj instanceof WebElement) {
            element = (WebElement) obj;
        } else {
            throw new IllegalArgumentException(
                    "Only elements of type TypifiedElement or WebElement " +
                            "are supported by @Invisible at present.");
        }
        wait.until(invisibilityOfElement(element));
    }

    /** Will wait up to ~10 seconds for the document to be ready. */
    private void tryToEnsureWeHaveUnloadedOldPageAndNewPageIsReady() {
        int notReadyCount = 0;
        int readyCount = 0;
        while (notReadyCount < 20 && readyCount < 3) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) { /* don't care */ }
            boolean docReady = (boolean) executeJS(
                    "return document.readyState == 'complete'");
            if (docReady) {
                readyCount++;
            } else {
                notReadyCount++;
            }
            logger.debug(
                    "Document ready: {}. Not ready {} times, ready {} times.",
                    docReady,
                    notReadyCount,
                    readyCount);
        }
    }

    //TODO - move to helper methods section instead
    private ExpectedCondition<Boolean> invisibilityOfElement(WebElement element) {
        return new ExpectedCondition<Boolean>() {
            @Nullable
            @Override
            public Boolean apply(WebDriver input) {
                try {
                    return !element.isDisplayed();
                } catch (NoSuchElementException
                        | StaleElementReferenceException e) {
                    return true;
                }
            }

            public String toString() {
                return "element to no longer be visible: " + element;
            }
        };
    }

    /**
     * Executes a javascript snippet to determine whether a page uses AngularJS
     * @return boolean - AngularJS true/false
     */
    private boolean isPageAngularJS() {
        try {
            return executeJS("return typeof angular;").equals("object");
        } catch (NullPointerException e) {
            logger.error("Detecting whether the page was angular returned a null object. " +
                    "This means your browser hasn't started! Investigate into the issue.");
            return false;
        }
    }

    /** Method to wait for AngularJS requests to finish on the page */
    protected void waitForAngularRequestsToFinish() {
        getNgDriver().waitForAngularRequestsToFinish();
    }

    private NgWebDriver getNgDriver() {
        if (ngDriver == null) {
            ngDriver = new NgWebDriver((JavascriptExecutor) driver);
        }
        return ngDriver;
    }

    /**
     * @param javascript the Javascript to execute on the current page
     * @return Returns an Object returned by the Javascript provided
     */
    protected Object executeJS(String javascript) {
        Object returnObj = null;
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        try {
            returnObj = jsExecutor.executeScript(javascript);
        } catch (Exception e) {
            logger.error("Javascript execution failed! Please investigate into the issue.");
            logger.debug("Failed Javascript:" + javascript);
        }
        return returnObj;
    }

    /**
     * Method which executes an async JS call
     *
     * @param javascript the JavaScript code to execute
     * @return the object returned from the executed JavaScript
     */
    protected Object executeAsyncJS(String javascript) {
        Object returnObj = null;
        try {
            JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
            returnObj = jsExecutor.executeAsyncScript(javascript);
        } catch (Exception e) {
            logger.error("Async javascript execution failed! Please investigate the issue.");
            logger.debug("Failed Javascript:" + javascript);
        }
        return returnObj;
    }

    protected void forceVisible(WebElement element) {
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        jsExecutor.executeScript(
                "arguments[0].style.zindex='10000';" +
                        "arguments[0].style.visibility='visible';" +
                        "arguments[0].style.opacity='100';",
                element);
    }

    /**
     * @return Returns the title of the web page
     */
    public String getTitle() {
        return driver.getTitle();
    }

    /**
     * @return Returns the source code of the current page
     */
    public String getSource() {
        return driver.getPageSource();
    }
}