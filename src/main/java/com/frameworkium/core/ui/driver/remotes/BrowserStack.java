package com.frameworkium.core.ui.driver.remotes;

import static com.frameworkium.core.common.properties.Property.BROWSER_STACK;

import java.net.MalformedURLException;
import java.net.URL;

public class BrowserStack {

    /**
     * Get the URL for the BrowserStack instance as configured by the parameters.
     * @return URL for the BrowserStack instance
     * @throws MalformedURLException
     */
    public static URL getURL() throws MalformedURLException {
        return new URL(String.format("http://%s:%s@hub.browserstack.com:80/wd/hub",
                System.getenv("BROWSER_STACK_USERNAME"),
                System.getenv("BROWSER_STACK_ACCESS_KEY")));
    }

    public static boolean isDesired() {
        return BROWSER_STACK.isSpecified()
                && Boolean.parseBoolean(BROWSER_STACK.getValue());
    }
}
