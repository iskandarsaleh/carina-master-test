/*******************************************************************************
 * Copyright 2013-2018 QaProSoft (http://www.qaprosoft.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.qaprosoft.carina.core.foundation;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Category;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.ITestNGMethod;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.xml.XmlTest;

import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.jayway.restassured.RestAssured;
import com.qaprosoft.amazon.AmazonS3Manager;
import com.qaprosoft.carina.core.foundation.api.APIMethodBuilder;
import com.qaprosoft.carina.core.foundation.commons.SpecialKeywords;
import com.qaprosoft.carina.core.foundation.dataprovider.core.DataProviderFactory;
import com.qaprosoft.carina.core.foundation.jira.Jira;
import com.qaprosoft.carina.core.foundation.listeners.AbstractTestListener;
import com.qaprosoft.carina.core.foundation.report.Artifacts;
import com.qaprosoft.carina.core.foundation.report.ReportContext;
import com.qaprosoft.carina.core.foundation.report.TestResultItem;
import com.qaprosoft.carina.core.foundation.report.TestResultType;
import com.qaprosoft.carina.core.foundation.report.email.EmailManager;
import com.qaprosoft.carina.core.foundation.report.email.EmailReportGenerator;
import com.qaprosoft.carina.core.foundation.report.email.EmailReportItemCollector;
import com.qaprosoft.carina.core.foundation.report.testrail.TestRail;
import com.qaprosoft.carina.core.foundation.skip.ExpectedSkipManager;
import com.qaprosoft.carina.core.foundation.utils.Configuration;
import com.qaprosoft.carina.core.foundation.utils.Configuration.DriverMode;
import com.qaprosoft.carina.core.foundation.utils.Configuration.Parameter;
import com.qaprosoft.carina.core.foundation.utils.DateUtils;
import com.qaprosoft.carina.core.foundation.utils.JsonUtils;
import com.qaprosoft.carina.core.foundation.utils.Messager;
import com.qaprosoft.carina.core.foundation.utils.R;
import com.qaprosoft.carina.core.foundation.utils.common.CommonUtils;
import com.qaprosoft.carina.core.foundation.utils.metadata.MetadataCollector;
import com.qaprosoft.carina.core.foundation.utils.metadata.model.ElementsInfo;
import com.qaprosoft.carina.core.foundation.utils.naming.TestNamingUtil;
import com.qaprosoft.carina.core.foundation.utils.resources.I18N;
import com.qaprosoft.carina.core.foundation.utils.resources.L10N;
import com.qaprosoft.carina.core.foundation.utils.resources.L10Nparser;
import com.qaprosoft.carina.core.foundation.webdriver.DriverPool;
import com.qaprosoft.carina.core.foundation.webdriver.core.capability.CapabilitiesLoader;
import com.qaprosoft.carina.core.foundation.webdriver.device.Device;
import com.qaprosoft.carina.core.foundation.webdriver.device.DevicePool;
import com.qaprosoft.hockeyapp.HockeyAppManager;

/*
 * AbstractTest - base test for UI and API tests.
 * 
 * @author Alex Khursevich
 */
@Listeners({ AbstractTestListener.class })
public abstract class AbstractTest // extends DriverHelper
{
    protected static final Logger LOGGER = Logger.getLogger(AbstractTest.class);

    protected APIMethodBuilder apiMethodBuilder;

    protected static final long EXPLICIT_TIMEOUT = Configuration.getLong(Parameter.EXPLICIT_TIMEOUT);

    protected static final String SUITE_TITLE = "%s%s%s - %s (%s%s)";
    protected static final String XML_SUITE_NAME = " (%s)";

    protected static ThreadLocal<String> suiteNameAppender = new ThreadLocal<String>();

    // 3rd party integrations
    protected String browserVersion = "";
    protected long startDate;

    @BeforeSuite(alwaysRun = true)
    public void executeBeforeTestSuite(ITestContext context) {

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new ShutdownHook());
        // Set log4j properties
        PropertyConfigurator.configure(ClassLoader.getSystemResource("log4j.properties"));
        // Set SoapUI log4j properties
        System.setProperty("soapui.log4j.config", "./src/main/resources/soapui-log4j.xml");

        try {
            Logger root = Logger.getRootLogger();
            Enumeration<?> allLoggers = root.getLoggerRepository().getCurrentCategories();
            while (allLoggers.hasMoreElements()) {
                Category tmpLogger = (Category) allLoggers.nextElement();
                if (tmpLogger.getName().equals("com.qaprosoft.carina.core")) {
                    tmpLogger.setLevel(Level.toLevel(Configuration.get(Parameter.CORE_LOG_LEVEL)));
                }
            }
        } catch (NoSuchMethodError e) {
            LOGGER.error("Unable to redefine logger level due to the conflicts between log4j and slf4j!");
        }

        startDate = new Date().getTime();
        LOGGER.info(Configuration.asString());
        // Configuration.validateConfiguration();

        LOGGER.debug("Default thread_count=" + context.getCurrentXmlTest().getSuite().getThreadCount());
        context.getCurrentXmlTest().getSuite().setThreadCount(Configuration.getInt(Parameter.THREAD_COUNT));
        LOGGER.debug("Updated thread_count=" + context.getCurrentXmlTest().getSuite().getThreadCount());

        // update DataProviderThreadCount if any property is provided otherwise sync with value from suite xml file
        int count = Configuration.getInt(Parameter.DATA_PROVIDER_THREAD_COUNT);
        if (count > 0) {
            LOGGER.debug("Updated 'data_provider_thread_count' from "
                    + context.getCurrentXmlTest().getSuite().getDataProviderThreadCount() + " to " + count);
            context.getCurrentXmlTest().getSuite().setDataProviderThreadCount(count);
        } else {
            LOGGER.debug("Synching data_provider_thread_count with values from suite xml file...");
            R.CONFIG.put(Parameter.DATA_PROVIDER_THREAD_COUNT.getKey(),
                    String.valueOf(context.getCurrentXmlTest().getSuite().getDataProviderThreadCount()));
            LOGGER.debug("Updated 'data_provider_thread_count': " + Configuration.getInt(Parameter.DATA_PROVIDER_THREAD_COUNT));
        }

        LOGGER.debug("Default data_provider_thread_count="
                + context.getCurrentXmlTest().getSuite().getDataProviderThreadCount());
        LOGGER.debug("Updated data_provider_thread_count="
                + context.getCurrentXmlTest().getSuite().getDataProviderThreadCount());

        if (!Configuration.isNull(Parameter.URL)) {
            if (!Configuration.get(Parameter.URL).isEmpty()) {
                RestAssured.baseURI = Configuration.get(Parameter.URL);
            }
        }

        try {
            L10N.init();
        } catch (Exception e) {
            LOGGER.error("L10N bundle is not initialized successfully!", e);
        }

        try {
            I18N.init();
        } catch (Exception e) {
            LOGGER.error("I18N bundle is not initialized successfully!", e);
        }

        try {
            L10Nparser.init();
        } catch (Exception e) {
            LOGGER.error("L10Nparser bundle is not initialized successfully!", e);
        }

        // TODO: move out from AbstractTest->executeBeforeTestSuite
        String customCapabilities = Configuration.get(Parameter.CUSTOM_CAPABILITIES);
        if (!customCapabilities.isEmpty()) {
            // redefine core CONFIG properties using custom capabilities file
            new CapabilitiesLoader().loadCapabilities(customCapabilities);
        }

        String extraCapabilities = Configuration.get(Parameter.EXTRA_CAPABILITIES);
        if (!extraCapabilities.isEmpty()) {
            // redefine core CONFIG properties using extra capabilities file
            new CapabilitiesLoader().loadCapabilities(extraCapabilities);
        }

        try {
            TestRail.updateBeforeSuite(context, this.getClass().getName(), getTitle(context));
        } catch (Exception e) {
            LOGGER.error("TestRail is not initialized successfully!", e);
        }

        updateAppPath();

    }

    @BeforeClass(alwaysRun = true)
    public void executeBeforeTestClass(ITestContext context) throws Throwable {
        // do nothing for now
    }

    @AfterClass(alwaysRun = true)
    public void executeAfterTestClass(ITestContext context) throws Throwable {
        if (Configuration.getDriverMode() == DriverMode.CLASS_MODE) {
            LOGGER.debug("Deinitialize driver(s) in UITest->AfterClass.");
            quitDrivers();
        }
    }

    @BeforeMethod(alwaysRun = true)
    public void executeBeforeTestMethod(XmlTest xmlTest, Method testMethod, ITestContext context) throws Throwable {

        // handle expected skip
        if (ExpectedSkipManager.getInstance().isSkip(testMethod, context)) {
            skipExecution("Based on rule listed above");
        }

        // do nothing for now
        apiMethodBuilder = new APIMethodBuilder();
    }

    @AfterMethod(alwaysRun = true)
    public void executeAfterTestMethod(ITestResult result) {
        try {
            if (apiMethodBuilder != null) {
                apiMethodBuilder.close();
            }

            DriverMode driverMode = Configuration.getDriverMode();

            if (driverMode == DriverMode.METHOD_MODE) {
                LOGGER.debug("Deinitialize driver(s) in @AfterMethod.");
                quitDrivers();
            }

            // TODO: improve later removing duplicates with AbstractTestListener
            // handle Zafira already passed exception for re-run and do nothing. maybe return should be enough
            if (result.getThrowable() != null && result.getThrowable().getMessage() != null
                    && result.getThrowable().getMessage().startsWith(SpecialKeywords.ALREADY_PASSED)) {
                // [VD] it is prohibited to release TestInfoByThread in this place.!
                return;
            }

            // handle AbstractTest->SkipExecution
            if (result.getThrowable() != null && result.getThrowable().getMessage() != null
                    && result.getThrowable().getMessage().startsWith(SpecialKeywords.SKIP_EXECUTION)) {
                // [VD] it is prohibited to release TestInfoByThread in this place.!
                return;
            }

            List<String> tickets = Jira.getTickets(result);
            result.setAttribute(SpecialKeywords.JIRA_TICKET, tickets);
            Jira.updateAfterTest(result);

            // we shouldn't deregister info here as all retries will not work
            // TestNamingUtil.releaseZafiraTest();

            // clear jira tickets to be sure that next test is not affected.
            Jira.clearTickets();

            Artifacts.clearArtifacts();

        } catch (Exception e) {
            LOGGER.error("Exception in AbstractTest->executeAfterTestMethod: " + e.getMessage());
            e.printStackTrace();
        }

    }

    @AfterSuite(alwaysRun = true)
    public void executeAfterTestSuite(ITestContext context) {
        try {
            if (Configuration.getDriverMode() == DriverMode.SUITE_MODE) {
                LOGGER.debug("Deinitialize driver(s) in UITest->AfterSuite.");
                quitDrivers();
            }

            ReportContext.removeTempDir(); // clean temp artifacts directory
            //HtmlReportGenerator.generate(ReportContext.getBaseDir().getAbsolutePath());

            String browser = getBrowser();
            String deviceName = getDeviceName();
            // String suiteName = getSuiteName(context);
            String title = getTitle(context);

            TestResultType testResult = EmailReportGenerator.getSuiteResult(EmailReportItemCollector.getTestResults());
            String status = testResult.getName();

            title = status + ": " + title;

            String env = "";
            if (!Configuration.isNull(Parameter.ENV)) {
                env = Configuration.get(Parameter.ENV);
            }

            if (!Configuration.get(Parameter.URL).isEmpty()) {
                env += " - <a href='" + Configuration.get(Parameter.URL) + "'>" + Configuration.get(Parameter.URL) + "</a>";
            }

            ReportContext.getTempDir().delete();

            // Update JIRA
            Jira.updateAfterSuite(context, EmailReportItemCollector.getTestResults());

            // generate and send email report by Zafira to test group of people
            String emailList = Configuration.get(Parameter.EMAIL_LIST);
            String failureEmailList = Configuration.get(Parameter.FAILURE_EMAIL_LIST);
            String senderEmail = Configuration.get(Parameter.SENDER_EMAIL);
            String senderPassword = Configuration.get(Parameter.SENDER_PASSWORD);

            // Generate and send email report using regular method
            EmailReportGenerator report = new EmailReportGenerator(title, env,
                    Configuration.get(Parameter.APP_VERSION), deviceName,
                    browser, DateUtils.now(), DateUtils.timeDiff(startDate),
                    EmailReportItemCollector.getTestResults(),
                    EmailReportItemCollector.getCreatedItems());

            String emailContent = report.getEmailBody();

            if (!R.ZAFIRA.getBoolean("zafira_enabled")) {
                // Do not send email if run is running with enabled Zafira
                EmailManager.send(title, emailContent,
                        emailList,
                        senderEmail,
                        senderPassword);

                if (testResult.equals(TestResultType.FAIL) && !failureEmailList.isEmpty()) {
                    EmailManager.send(title, emailContent,
                            failureEmailList,
                            senderEmail,
                            senderPassword);
                }
            }

            // Store emailable report under emailable-report.html
            ReportContext.generateHtmlReport(emailContent);

            printExecutionSummary(EmailReportItemCollector.getTestResults());

            LOGGER.debug("Generating email report...");

            TestResultType suiteResult = EmailReportGenerator.getSuiteResult(EmailReportItemCollector.getTestResults());
            switch (suiteResult) {
            case SKIP_ALL:
                Assert.fail("All tests were skipped! Analyze logs to determine possible configuration issues.");
                break;
            case SKIP_ALL_ALREADY_PASSED:
                LOGGER.info("Nothing was executed in rerun mode because all tests already passed and registered in Zafira Repoting Service!");
                break;
            default:
                // do nothing
            }
            LOGGER.debug("Finish email report generation.");

        } catch (Exception e) {
            LOGGER.error("Exception in AbstractTest->executeAfterSuite: " + e.getMessage());
            e.printStackTrace();
        }

    }

    // TODO: remove this private method
    private String getDeviceName() {
        String deviceName = "Desktop";

        if (!DevicePool.getDevice().isNull()) {
            // Samsung - Android 4.4.2; iPhone - iOS 7
            Device device = DevicePool.getDevice();
            String deviceTemplate = "%s - %s %s";
            deviceName = String.format(deviceTemplate, device.getName(), device.getOs(), device.getOsVersion());
        }

        return deviceName;
    }

    protected String getBrowser() {
        String browser = "";
        if (!Configuration.get(Parameter.BROWSER).isEmpty()) {
            browser = Configuration.get(Parameter.BROWSER);
        }

        if (!browserVersion.isEmpty()) {
            browser = browser + " " + browserVersion;
        }

        return browser;
    }

    protected String getTitle(ITestContext context) {
        String browser = getBrowser();
        if (!browser.isEmpty()) {
            browser = " " + browser; // insert the space before
        }
        String device = getDeviceName();

        String env = !Configuration.isNull(Parameter.ENV) ? Configuration.get(Parameter.ENV) : Configuration.get(Parameter.URL);

        String title = "";
        String app_version = "";

        if (!Configuration.get(Parameter.APP_VERSION).isEmpty()) {
            // if nothing is specified then title will contain nothing
            app_version = Configuration.get(Parameter.APP_VERSION) + " - ";
        }

        String suiteName = getSuiteName(context);
        String xmlFile = getSuiteFileName(context);

        title = String.format(SUITE_TITLE, app_version, suiteName, String.format(XML_SUITE_NAME, xmlFile), env, device, browser);

        return title;
    }

    private String getSuiteFileName(ITestContext context) {
        // TODO: investigate why we need such method and suite file name at all
        String fileName = context.getSuite().getXmlSuite().getFileName();
        if (fileName == null) {
            fileName = "undefined";
        }
        LOGGER.debug("Full suite file name: " + fileName);
        if (fileName.contains("\\")) {
            fileName = fileName.replaceAll("\\\\", "/");
        }
        fileName = StringUtils.substringAfterLast(fileName, "/");
        LOGGER.debug("Short suite file name: " + fileName);
        return fileName;
    }

    protected String getSuiteName(ITestContext context) {

        String suiteName = "";

        if (context.getSuite().getXmlSuite() != null && !"Default suite".equals(context.getSuite().getXmlSuite().getName())) {
            suiteName = Configuration.get(Parameter.SUITE_NAME).isEmpty() ? context.getSuite().getXmlSuite().getName()
                    : Configuration.get(Parameter.SUITE_NAME);
        } else {
            suiteName = Configuration.get(Parameter.SUITE_NAME).isEmpty() ? R.EMAIL.get("title") : Configuration.get(Parameter.SUITE_NAME);
        }

        String appender = getSuiteNameAppender();
        if (appender != null && !appender.isEmpty()) {
            suiteName = suiteName + " - " + appender;
        }

        return suiteName;
    }

    protected void setSuiteNameAppender(String appender) {
        suiteNameAppender.set(appender);
    }

    protected String getSuiteNameAppender() {
        return suiteNameAppender.get();
    }

    private void printExecutionSummary(List<TestResultItem> tris) {
        Messager.INROMATION
                .info("**************** Test execution summary ****************");
        int num = 1;
        for (TestResultItem tri : tris) {
            String failReason = tri.getFailReason();
            if (failReason == null) {
                failReason = "";
            }

            if (!tri.isConfig() && !failReason.contains(SpecialKeywords.ALREADY_PASSED)
                    && !failReason.contains(SpecialKeywords.SKIP_EXECUTION)) {
                String reportLinks = !StringUtils.isEmpty(tri.getLinkToScreenshots())
                        ? "screenshots=" + tri.getLinkToScreenshots() + " | "
                        : "";
                reportLinks += !StringUtils.isEmpty(tri.getLinkToLog()) ? "log=" + tri.getLinkToLog() : "";
                Messager.TEST_RESULT.info(String.valueOf(num++), tri.getTest(), tri.getResult().toString(),
                        reportLinks);
            }
        }
    }

    /**
     * Redefine Jira tickets from test.
     *
     * @param tickets to set
     */
    @Deprecated
    protected void setJiraTicket(String... tickets) {
        List<String> jiraTickets = new ArrayList<String>();
        for (String ticket : tickets) {
            jiraTickets.add(ticket);
        }
        Jira.setTickets(jiraTickets);
    }

    /**
     * Redefine TestRails cases from test.
     *
     * @param cases to set
     */
    protected void setTestRailCase(String... cases) {
        TestRail.setCasesID(cases);
    }

    @DataProvider(name = "DataProvider", parallel = true)
    public Object[][] createData(final ITestNGMethod testMethod, ITestContext context) {
        Annotation[] annotations = testMethod.getConstructorOrMethod().getMethod().getDeclaredAnnotations();
        Object[][] objects = DataProviderFactory.getNeedRerunDataProvider(annotations, context, testMethod);
        return objects;
    }

    @DataProvider(name = "SingleDataProvider")
    public Object[][] createDataSingleThread(final ITestNGMethod testMethod,
            ITestContext context) {
        Annotation[] annotations = testMethod.getConstructorOrMethod().getMethod().getDeclaredAnnotations();
        Object[][] objects = DataProviderFactory.getNeedRerunDataProvider(annotations, context, testMethod);
        return objects;
    }

    /**
     * Pause for specified timeout.
     *
     * @param timeout in seconds.
     */

    public void pause(long timeout) {
        CommonUtils.pause(timeout);
    }

    public void pause(Double timeout) {
        CommonUtils.pause(timeout);
    }

    protected void putS3Artifact(String key, String path) {
        AmazonS3Manager.getInstance().put(Configuration.get(Parameter.S3_BUCKET_NAME), key, path);
    }

    protected S3Object getS3Artifact(String bucket, String key) {
        return AmazonS3Manager.getInstance().get(Configuration.get(Parameter.S3_BUCKET_NAME), key);
    }

    protected S3Object getS3Artifact(String key) {
        return getS3Artifact(Configuration.get(Parameter.S3_BUCKET_NAME), key);
    }

    private void updateAppPath() {

        try {
            if (!Configuration.get(Parameter.ACCESS_KEY_ID).isEmpty()) {
                updateS3AppPath();
            }
        } catch (Exception e) {
            LOGGER.error("AWS S3 manager exception detected!", e);
        }

        try {
            if (!Configuration.get(Parameter.HOCKEYAPP_TOKEN).isEmpty()) {
                updateHockeyAppPath();
            }
        } catch (Exception e) {
            LOGGER.error("HockeyApp manager exception detected!", e);
        }

    }

    /**
     * Method to update MOBILE_APP path in case if apk is located in Hockey App.
     */
    private void updateHockeyAppPath() {
        // hockeyapp://appName/platformName/buildType/version
        Pattern HOCKEYAPP_PATTERN = Pattern
                .compile("hockeyapp:\\/\\/([a-zA-Z-0-9][^\\/]*)\\/([a-zA-Z-0-9][^\\/]*)\\/([a-zA-Z-0-9][^\\/]*)\\/([a-zA-Z-0-9][^\\/]*)");
        String mobileAppPath = Configuration.getMobileApp();
        Matcher matcher = HOCKEYAPP_PATTERN.matcher(mobileAppPath);

        LOGGER.info("Analyzing if mobile_app is located on HockeyApp...");
        if (matcher.find()) {
            LOGGER.info("app artifact is located on HockeyApp...");
            String appName = matcher.group(1);
            String platformName = matcher.group(2);
            String buildType = matcher.group(3);
            String version = matcher.group(4);

            String hockeyAppLocalStorage = Configuration.get(Parameter.HOCKEYAPP_LOCAL_STORAGE);
            // download file from HockeyApp to local storage

            File file = HockeyAppManager.getInstance().getBuild(hockeyAppLocalStorage, appName, platformName, buildType, version);

            Configuration.setMobileApp(file.getAbsolutePath());

            LOGGER.info("Updated mobile app: " + Configuration.getMobileApp());

            // try to redefine app_version if it's value is latest or empty
            String appVersion = Configuration.get(Parameter.APP_VERSION);
            if (appVersion.equals("latest") || appVersion.isEmpty()) {
                R.CONFIG.put(Parameter.APP_VERSION.getKey(), file.getName());
            }
        }

    }

    /**
     * Method to update MOBILE_APP path in case if apk is located in s3 bucket.
     */
    private void updateS3AppPath() {
        Pattern S3_BUCKET_PATTERN = Pattern.compile("s3:\\/\\/([a-zA-Z-0-9][^\\/]*)\\/(.*)");
        // get app path to be sure that we need(do not need) to download app from s3 bucket
        String mobileAppPath = Configuration.getMobileApp();
        Matcher matcher = S3_BUCKET_PATTERN.matcher(mobileAppPath);

        LOGGER.info("Analyzing if mobile app is located on S3...");
        if (matcher.find()) {
            LOGGER.info("app artifact is located on s3...");
            String bucketName = matcher.group(1);
            String key = matcher.group(2);
            Pattern pattern = Pattern.compile(key);

            // analyze if we have any pattern inside mobile_app to make extra
            // search in AWS
            int position = key.indexOf(".*");
            if (position > 0) {
                // /android/develop/dfgdfg.*/Mapmyrun.apk
                int slashPosition = key.substring(0, position).lastIndexOf("/");
                if (slashPosition > 0) {
                    key = key.substring(0, slashPosition);
                    S3ObjectSummary lastBuild = AmazonS3Manager.getInstance().getLatestBuildArtifact(bucketName, key,
                            pattern);
                    key = lastBuild.getKey();
                }

            }

            S3Object objBuild = AmazonS3Manager.getInstance().get(bucketName, key);

            String s3LocalStorage = Configuration.get(Parameter.S3_LOCAL_STORAGE);

            // download file from AWS to local storage

            String fileName = s3LocalStorage + "/" + StringUtils.substringAfterLast(objBuild.getKey(), "/");
            File file = new File(fileName);

            // verify maybe requested artifact with the same size was already
            // download
            if (file.exists() && file.length() == objBuild.getObjectMetadata().getContentLength()) {
                LOGGER.info("build artifact with the same size already downloaded: " + file.getAbsolutePath());
            } else {
                LOGGER.info(String.format("Following data was extracted: bucket: %s, key: %s, local file: %s",
                        bucketName, key, file.getAbsolutePath()));
                AmazonS3Manager.getInstance().download(bucketName, key, new File(fileName));
            }

            Configuration.setMobileApp(file.getAbsolutePath());

            // try to redefine app_version if it's value is latest or empty
            String appVersion = Configuration.get(Parameter.APP_VERSION);
            if (appVersion.equals("latest") || appVersion.isEmpty()) {
                R.CONFIG.put(Parameter.APP_VERSION.getKey(), file.getName());
            }

        }
    }

    protected void setBug(String id) {
        String test = TestNamingUtil.getTestNameByThread();
        TestNamingUtil.associateBug(test, id);
    }

    protected void skipExecution(String message) {
        throw new SkipException(SpecialKeywords.SKIP_EXECUTION + ": " + message);
    }

    // --------------------------------------------------------------------------
    // Web Drivers
    // --------------------------------------------------------------------------
    protected WebDriver getDriver() {
        return getDriver(DriverPool.DEFAULT);
    }

    protected WebDriver getDriver(String name) {
        WebDriver drv = DriverPool.getDriver(name);
        if (drv == null) {
            Assert.fail("Unable to find driver by name: " + name);
        }
        
        return drv;
        //return castDriver(drv);
    }

    protected WebDriver getDriver(String name, DesiredCapabilities capabilities, String seleniumHost) {
        WebDriver drv = DriverPool.getDriver(name, capabilities, seleniumHost);
        if (drv == null) {
            Assert.fail("Unable to find driver by name: " + name);
        }
        return drv;
        //return castDriver(drv);
    }

/*    private WebDriver castDriver(WebDriver drv) {
        if (drv instanceof EventFiringWebDriver) {
            return ((EventFiringWebDriver) drv).getWrappedDriver();
        } else {
            return drv;
        }
    }*/
    
    protected static void quitDrivers() {
        DriverPool.quitDrivers();
    }

    public static class ShutdownHook extends Thread {

        private static final Logger LOGGER = Logger.getLogger(ShutdownHook.class);

        private void generateMetadata() {
            Map<String, ElementsInfo> allData = MetadataCollector.getAllCollectedData();
            if (allData.size() > 0) {
                LOGGER.debug("Generating collected metadada start...");
            }
            for (String key : allData.keySet()) {
                LOGGER.debug("Creating... medata for '" + key + "' object...");
                File file = new File(ReportContext.getArtifactsFolder().getAbsolutePath() + "/metadata/" + key.hashCode() + ".json");
                PrintWriter out = null;
                try {
                    out = new PrintWriter(file);
                    out.append(JsonUtils.toJson(MetadataCollector.getAllCollectedData().get(key)));
                    out.flush();
                } catch (FileNotFoundException e) {
                    LOGGER.error("Unable to write metadata to json file: " + file.getAbsolutePath(), e);
                } finally {
                    out.close();
                }
                LOGGER.debug("Created medata for '" + key + "' object...");
            }

            if (allData.size() > 0) {
                LOGGER.debug("Generating collected metadada finish...");
            }
        }

        @Override
        public void run() {
            LOGGER.debug("Running shutdown hook");
            generateMetadata();
        }

    }

}