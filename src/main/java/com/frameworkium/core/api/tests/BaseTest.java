package com.frameworkium.core.api.tests;

import com.frameworkium.core.common.listeners.MethodInterceptor;
import com.frameworkium.core.common.listeners.ResultLoggerListener;
import com.frameworkium.core.common.listeners.TestListener;
import com.frameworkium.core.common.reporting.allure.AllureLogger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.Listeners;

@Listeners({MethodInterceptor.class,
        TestListener.class,
        ResultLoggerListener.class} )
public abstract class BaseTest {

    private static Logger logger = LogManager.getLogger(BaseTest.class);

    /**
     * Creates the allure properties for the report, after the test run
     */
    @AfterSuite(alwaysRun = true)
    public static void createAllureProperties() {
        com.frameworkium.core.common.reporting.allure.AllureProperties.create();
    }

    /**
     * Logs the start of a step to your allure report
     * Other steps will be substeps until you call stepFinish
     * @param stepName the name of the step
     */
    public void __stepStart(String stepName){
        AllureLogger.__stepStart(stepName);
    }

    /**
     * Logs the end of a step
     */
    public void __stepFinish(){
        AllureLogger.__stepFinish();
    }

}