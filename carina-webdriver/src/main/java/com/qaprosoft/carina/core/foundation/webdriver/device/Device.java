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
package com.qaprosoft.carina.core.foundation.webdriver.device;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.WebDriver;

import com.qaprosoft.carina.commons.models.RemoteDevice;
import com.qaprosoft.carina.core.foundation.commons.SpecialKeywords;
import com.qaprosoft.carina.core.foundation.report.ReportContext;
import com.qaprosoft.carina.core.foundation.utils.Configuration;
import com.qaprosoft.carina.core.foundation.utils.Configuration.Parameter;
import com.qaprosoft.carina.core.foundation.utils.R;
import com.qaprosoft.carina.core.foundation.utils.android.recorder.utils.AdbExecutor;
import com.qaprosoft.carina.core.foundation.utils.android.recorder.utils.CmdLine;
import com.qaprosoft.carina.core.foundation.utils.common.CommonUtils;
import com.qaprosoft.carina.core.foundation.utils.factory.DeviceType;
import com.qaprosoft.carina.core.foundation.utils.factory.DeviceType.Type;
import com.qaprosoft.carina.core.foundation.webdriver.DriverPool;

public class Device extends RemoteDevice {
    private static final Logger LOGGER = Logger.getLogger(Device.class);

    /**
     * ENABLED only in case of availability of parameter - 'uninstall_related_apps'.
     * Store udids of devices where related apps were uninstalled
     */
    private static List<String> clearedDeviceUdids = new ArrayList<>();

    AdbExecutor executor = new AdbExecutor();

    public Device() {
        this("", "", "", "", "", "");
    }

    public Device(String name, String type, String os, String osVersion, String udid, String remoteURL) {
        setName(name);
        setType(type);
        setOs(os);
        setOsVersion(osVersion);
        setUdid(udid);
        setRemoteURL(remoteURL);
    }

    public Device(RemoteDevice remoteDevice) {
        setName(remoteDevice.getName());
        setType(remoteDevice.getType());
        setOs(remoteDevice.getOs());
        setOsVersion(remoteDevice.getOsVersion());
        setUdid(remoteDevice.getUdid());
        setRemoteURL(remoteDevice.getRemoteURL());
        setProxyPort(remoteDevice.getProxyPort());
    }

    public Device(Capabilities capabilities) {
        // 1. read from CONFIG and specify if any: capabilities.deviceName, capabilities.device (browserstack notation)
        // 2. read from capabilities object and set if if it is not null
        String deviceName = R.CONFIG.get(SpecialKeywords.MOBILE_DEVICE_NAME);
        if (!R.CONFIG.get(SpecialKeywords.MOBILE_DEVICE_BROWSERSTACK_NAME).isEmpty()) {
            deviceName = R.CONFIG.get(SpecialKeywords.MOBILE_DEVICE_BROWSERSTACK_NAME);
        }
        if (capabilities.getCapability("deviceName") != null) {
            deviceName = capabilities.getCapability("deviceName").toString();
        }
        if (capabilities.getCapability("deviceModel") != null) {
            // deviceModel is returned from capabilities with name of device for local appium runs
            deviceName = capabilities.getCapability("deviceModel").toString();
        }
        setName(deviceName);

        // TODO: should we register default device type as phone?
        String deviceType = SpecialKeywords.PHONE;
        if (!R.CONFIG.get(SpecialKeywords.MOBILE_DEVICE_TYPE).isEmpty()) {
            deviceType = R.CONFIG.get(SpecialKeywords.MOBILE_DEVICE_TYPE);
        }
        if (capabilities.getCapability("deviceType") != null) {
            deviceType = capabilities.getCapability("deviceType").toString();
        }
        setType(deviceType);

        setOs(Configuration.getPlatform());

        String platformVersion = R.CONFIG.get(SpecialKeywords.MOBILE_DEVICE_PLATFORM_VERSION);
        if (!R.CONFIG.get(SpecialKeywords.MOBILE_DEVICE_BROWSERSTACK_PLATFORM_VERSION).isEmpty()) {
            platformVersion = R.CONFIG.get(SpecialKeywords.MOBILE_DEVICE_BROWSERSTACK_PLATFORM_VERSION);
        }
        if (capabilities.getCapability("platformVersion") != null) {
            platformVersion = capabilities.getCapability("platformVersion").toString();
        }

        setOsVersion(platformVersion);

        String deviceUdid = R.CONFIG.get(SpecialKeywords.MOBILE_DEVICE_UDID);
        if (capabilities.getCapability("udid") != null) {
            deviceUdid = capabilities.getCapability("udid").toString();
        }

        setUdid(deviceUdid);
        
        String proxyPort = R.CONFIG.get(SpecialKeywords.MOBILE_PROXY_PORT);
        if (capabilities.getCapability("proxy_port") != null) {
            proxyPort = capabilities.getCapability("proxy_port").toString();
        }

        setProxyPort(proxyPort);
    }

    public boolean isPhone() {
        return getType().equalsIgnoreCase(SpecialKeywords.PHONE);
    }

    public boolean isTablet() {
        return getType().equalsIgnoreCase(SpecialKeywords.TABLET);
    }

    public boolean isTv() {
        return getType().equalsIgnoreCase(SpecialKeywords.TV);
    }

    public Type getDeviceType() {
        if (isNull()) {
            // if no device initialized it means that desktop UI automation is used
            return Type.DESKTOP;
        }

        if (getOs().equalsIgnoreCase(SpecialKeywords.ANDROID)) {
            if (isTablet()) {
                return Type.ANDROID_TABLET;
            }
            if (isTv()) {
                return Type.ANDROID_TV;
            }
            return Type.ANDROID_PHONE;
        } else if (getOs().equalsIgnoreCase(SpecialKeywords.IOS) || getOs().equalsIgnoreCase(SpecialKeywords.MAC)) {
            if (isTablet()) {
                return Type.IOS_TABLET;
            }
            return Type.IOS_PHONE;
        }
        throw new RuntimeException("Incorrect driver type. Please, check config file for " + toString());
    }

    public String toString() {
        return String.format("name: %s; type: %s; os: %s; osVersion: %s; udid: %s; remoteURL: %s", getName(),
                getType(), getOs(), getOsVersion(), getUdid(), getRemoteURL());
    }

    public boolean isNull() {
        if (getName() == null) {
            return true;
        }
        return getName().isEmpty();
    }

    public void connectRemote() {
        if (isNull())
            return;

        LOGGER.info("adb connect " + getRemoteURL());
        String[] cmd = CmdLine.insertCommandsAfter(executor.getDefaultCmd(), "connect", getRemoteURL());
        executor.execute(cmd);
        CommonUtils.pause(1);

        String[] cmd2 = CmdLine.insertCommandsAfter(executor.getDefaultCmd(), "devices");
        executor.execute(cmd2);

        // TODO: add several attempt of connect until device appear among connected devices
        // quick workaround to do double connect...
        executor.execute(cmd);
        executor.execute(cmd2);
    }

    public void disconnectRemote() {
        if (isNull())
            return;

        // [VD] No need to do adb command as stopping STF session do it correctly
        // in new STF we have huge problems with sessions disconnect
        LOGGER.info("adb disconnect " + getRemoteURL());
        String[] cmd = CmdLine.insertCommandsAfter(executor.getDefaultCmd(), "disconnect", getRemoteURL());
        executor.execute(cmd);

    }

    @Deprecated
    public void dropFile(String pathToFile) {
        if (this.isNull())
            return;

        String[] cmd = CmdLine.insertCommandsAfter(executor.getDefaultCmd(), "-s", getAdbName(), "shell", "rm", pathToFile);
        executor.execute(cmd);
    }

    public String getFullPackageByName(final String name) {

        List<String> packagesList = getInstalledPackages();
        LOGGER.info("Found packages: ".concat(packagesList.toString()));
        String resultPackage = null;
        for (String packageStr : packagesList) {
            if (packageStr.matches(String.format(".*%s.*", name))) {
                LOGGER.info("Package was found: ".concat(packageStr));
                resultPackage = packageStr;
                break;
            }
        }
        if (null == resultPackage) {
            LOGGER.info("Package wasn't found using following name: ".concat(name));
            resultPackage = "not found";
        }
        return resultPackage;
    }

    public List<String> getInstalledPackages() {
        String deviceUdid = getAdbName();
        LOGGER.info("Device udid: ".concat(deviceUdid));
        String[] cmd = CmdLine.createPlatformDependentCommandLine("adb", "-s", deviceUdid, "shell", "pm", "list", "packages");
        LOGGER.info("Following cmd will be executed: " + Arrays.toString(cmd));
        List<String> packagesList = executor.execute(cmd);
        return packagesList;
    }

    public boolean isAppInstall(final String packageName) {
        return !getFullPackageByName(packageName).contains("not found");
    }

    @Deprecated
    public void pullFile(String pathFrom, String pathTo) {
        if (isNull())
            return;

        String[] cmd = CmdLine.insertCommandsAfter(executor.getDefaultCmd(), "-s", getAdbName(), "pull", pathFrom, pathTo);
        executor.execute(cmd);
    }

    private Boolean getScreenState() {
        // determine current screen status
        // adb -s <udid> shell dumpsys input_method | find "mScreenOn"
        String[] cmd = CmdLine.insertCommandsAfter(executor.getDefaultCmd(), "-s", getAdbName(), "shell", "dumpsys",
                "input_method");
        List<String> output = executor.execute(cmd);

        Boolean screenState = null;
        String line;

        Iterator<String> itr = output.iterator();
        while (itr.hasNext()) {
            // mScreenOn - default value for the most of Android devices
            // mInteractive - for Nexuses
            line = itr.next();
            if (line.contains("mScreenOn=true") || line.contains("mInteractive=true")) {
                screenState = true;
                break;
            }
            if (line.contains("mScreenOn=false") || line.contains("mInteractive=false")) {
                screenState = false;
                break;
            }
        }

        if (screenState == null) {
            LOGGER.error(getUdid()
                    + ": Unable to determine existing device screen state!");
            return screenState; // no actions required if state is not recognized.
        }

        if (screenState) {
            LOGGER.info(getUdid() + ": screen is ON");
        }

        if (!screenState) {
            LOGGER.info(getUdid() + ": screen is OFF");
        }

        return screenState;
    }

    public void screenOff() {
        if (!Configuration.getPlatform().equalsIgnoreCase(SpecialKeywords.ANDROID)) {
            return;
        }
        if (!Configuration.getBoolean(Parameter.MOBILE_SCREEN_SWITCHER)) {
            return;
        }

        if (isNull())
            return;

        Boolean screenState = getScreenState();
        if (screenState == null) {
            return;
        }
        if (screenState) {
            String[] cmd = CmdLine.insertCommandsAfter(executor.getDefaultCmd(), "-s", getAdbName(), "shell", "input",
                    "keyevent", "26");
            executor.execute(cmd);

            CommonUtils.pause(5);

            screenState = getScreenState();
            if (screenState) {
                LOGGER.error(getUdid() + ": screen is still ON!");
            }

            if (!screenState) {
                LOGGER.info(getUdid() + ": screen turned off.");
            }
        }
    }

    public void screenOn() {
        if (!Configuration.getPlatform().equalsIgnoreCase(SpecialKeywords.ANDROID)) {
            return;
        }

        if (!Configuration.getBoolean(Parameter.MOBILE_SCREEN_SWITCHER)) {
            return;
        }

        if (isNull())
            return;

        Boolean screenState = getScreenState();
        if (screenState == null) {
            return;
        }

        if (!screenState) {
            String[] cmd = CmdLine.insertCommandsAfter(executor.getDefaultCmd(), "-s", getAdbName(), "shell",
                    "input", "keyevent", "26");
            executor.execute(cmd);

            CommonUtils.pause(5);
            // verify that screen is Off now
            screenState = getScreenState();
            if (!screenState) {
                LOGGER.error(getUdid() + ": screen is still OFF!");
            }

            if (screenState) {
                LOGGER.info(getUdid() + ": screen turned on.");
            }
        }
    }

    public void pressKey(int key) {
        if (isNull())
            return;

        String[] cmd = CmdLine.insertCommandsAfter(executor.getDefaultCmd(), "-s", getAdbName(), "shell", "input",
                "keyevent", String.valueOf(key));
        executor.execute(cmd);
    }

    public void pause(long timeout) {
        CommonUtils.pause(timeout);
    }

    public void clearAppData() {
        clearAppData(Configuration.getMobileApp());
    }

    public void clearAppData(String app) {
        if (!Configuration.getPlatform().equalsIgnoreCase(SpecialKeywords.ANDROID)) {
            return;
        }

        if (isNull())
            return;

        // adb -s UDID shell pm clear com.myfitnesspal.android
        String packageName = getApkPackageName(app);

        String[] cmd = CmdLine.insertCommandsAfter(executor.getDefaultCmd(), "-s", getAdbName(), "shell", "pm", "clear", packageName);
        executor.execute(cmd);
    }

    public String getApkPackageName(String apkFile) {
        // aapt dump badging <apk_file> | grep versionCode
        // aapt dump badging <apk_file> | grep versionName
        // output:
        // package: name='com.myfitnesspal.android' versionCode='9025' versionName='develop-QA' platformBuildVersionName='6.0-2704002'

        String packageName = "";

        String[] cmd = CmdLine.insertCommandsAfter("aapt dump badging".split(" "), apkFile);
        List<String> output = executor.execute(cmd);
        // parse output command and get appropriate data

        for (String line : output) {
            if (line.contains("versionCode") && line.contains("versionName")) {
                LOGGER.debug(line);
                String[] outputs = line.split("'");
                packageName = outputs[1]; // package
            }
        }

        return packageName;
    }

    public void uninstallApp(String packageName) {
        if (isNull())
            return;

        // adb -s UDID uninstall com.myfitnesspal.android
        String[] cmd = CmdLine.insertCommandsAfter(executor.getDefaultCmd(), "-s", getAdbName(), "uninstall", packageName);
        executor.execute(cmd);
    }

    public void installApp(String apkPath) {
        if (isNull())
            return;

        // adb -s UDID install com.myfitnesspal.android
        String[] cmd = CmdLine.insertCommandsAfter(executor.getDefaultCmd(), "-s", getAdbName(), "install", "-r", apkPath);
        executor.execute(cmd);
    }

    public synchronized void installAppSync(String apkPath) {
        if (isNull())
            return;

        // adb -s UDID install com.myfitnesspal.android
        String[] cmd = CmdLine.insertCommandsAfter(executor.getDefaultCmd(), "-s", getAdbName(), "install", "-r", apkPath);
        executor.execute(cmd);
    }

    /*
     * public void reinstallApp() {
     * if (!Configuration.getPlatform().equalsIgnoreCase(SpecialKeywords.ANDROID)) {
     * return;
     * }
     * 
     * if (isNull())
     * return;
     * 
     * String mobileApp = Configuration.getMobileApp();
     * String oldMobileApp = Configuration.get(Parameter.MOBILE_APP_PREUPGRADE);
     * 
     * if (!oldMobileApp.isEmpty()) {
     * //redefine strategy to do upgrade scenario
     * R.CONFIG.put(Parameter.MOBILE_APP_UNINSTALL.getKey(), "true");
     * R.CONFIG.put(Parameter.MOBILE_APP_INSTALL.getKey(), "true");
     * }
     * 
     * if (Configuration.getBoolean(Parameter.MOBILE_APP_UNINSTALL)) {
     * // explicit reinstall the apk
     * String[] apkVersions = getApkVersion(mobileApp);
     * if (apkVersions != null) {
     * String appPackage = apkVersions[0];
     * 
     * String[] apkInstalledVersions = getInstalledApkVersion(appPackage);
     * 
     * LOGGER.info("installed app: " + apkInstalledVersions[2] + "-" + apkInstalledVersions[1]);
     * LOGGER.info("new app: " + apkVersions[2] + "-" + apkVersions[1]);
     * 
     * if (apkVersions[1].equals(apkInstalledVersions[1]) && apkVersions[2].equals(apkInstalledVersions[2]) && oldMobileApp.isEmpty()) {
     * LOGGER.info(
     * "Skip application uninstall and cache cleanup as exactly the same version is already installed.");
     * } else {
     * uninstallApp(appPackage);
     * clearAppData(appPackage);
     * isAppInstalled = false;
     * if (!oldMobileApp.isEmpty()) {
     * LOGGER.info("Starting sync install operation for preupgrade app: " + oldMobileApp);
     * installAppSync(oldMobileApp);
     * }
     * 
     * if (Configuration.getBoolean(Parameter.MOBILE_APP_INSTALL)) {
     * // install application in single thread to fix issue with gray Google maps
     * LOGGER.info("Starting sync install operation for app: " + mobileApp);
     * installAppSync(mobileApp);
     * }
     * }
     * }
     * } else if (Configuration.getBoolean(Parameter.MOBILE_APP_INSTALL) && !isAppInstalled) {
     * LOGGER.info("Starting install operation for app: " + mobileApp);
     * installApp(mobileApp);
     * isAppInstalled = true;
     * }
     * }
     */

    public String[] getInstalledApkVersion(String packageName) {
        // adb -s UDID shell dumpsys package PACKAGE | grep versionCode
        if (isNull())
            return null;

        String[] res = new String[3];
        res[0] = packageName;

        String[] cmd = CmdLine.insertCommandsAfter(executor.getDefaultCmd(), "-s", getAdbName(), "shell", "dumpsys", "package", packageName);
        List<String> output = executor.execute(cmd);

        for (String line : output) {
            LOGGER.debug(line);
            if (line.contains("versionCode")) {
                // versionCode=17040000 targetSdk=25
                LOGGER.info("Line for parsing installed app: " + line);
                String[] outputs = line.split("=");
                String tmp = outputs[1]; // everything after '=' sign
                res[1] = tmp.split(" ")[0];
            }

            if (line.contains("versionName")) {
                // versionName=8.5.0
                LOGGER.info("Line for parsing installed app: " + line);
                String[] outputs = line.split("=");
                res[2] = outputs[1];
            }
        }

        if (res[0] == null && res[1] == null && res[2] == null) {
            return null;
        }
        return res;
    }

    public String[] getApkVersion(String apkFile) {
        // aapt dump badging <apk_file> | grep versionCode
        // aapt dump badging <apk_file> | grep versionName
        // output:
        // package: name='com.myfitnesspal.android' versionCode='9025' versionName='develop-QA' platformBuildVersionName='6.0-2704002'

        String[] res = new String[3];
        res[0] = "";
        res[1] = "";
        res[2] = "";

        String[] cmd = CmdLine.insertCommandsAfter("aapt dump badging".split(" "), apkFile);
        List<String> output = executor.execute(cmd);
        // parse output command and get appropriate data

        for (String line : output) {
            if (line.contains("versionCode") && line.contains("versionName")) {
                LOGGER.debug(line);
                String[] outputs = line.split("'");
                res[0] = outputs[1]; // package
                res[1] = outputs[3]; // versionCode
                res[2] = outputs[5]; // versionName
            }
        }

        return res;
    }

    public List<String> execute(String[] cmd) {
        return executor.execute(cmd);
    }

    public void setProxy(final String host, final String port, final String ssid, final String password) {
        if (!getOs().equalsIgnoreCase(DeviceType.Type.ANDROID_PHONE.getFamily())) {
            LOGGER.error("Proxy configuration is available for Android ONLY");
            throw new RuntimeException("Proxy configuration is available for Android ONLY");
        }
        if (!isAppInstall(SpecialKeywords.PROXY_SETTER_PACKAGE)) {
            final String proxySetterFileName = "./proxy-setter-temp.apk";
            File targetFile = new File(proxySetterFileName);
            downloadFileFromJar(SpecialKeywords.PROXY_SETTER_RES_PATH, targetFile);
            installApp(proxySetterFileName);
        }
        String deviceUdid = getAdbName();
        LOGGER.info("Device udid: ".concat(deviceUdid));
        String[] cmd = CmdLine.createPlatformDependentCommandLine("adb", "-s", deviceUdid, "shell", "am", "start", "-n",
                "tk.elevenk.proxysetter/.MainActivity", "-e", "host", host, "-e", "port", port, "-e", "ssid", ssid, "-e", "key", password);
        LOGGER.info("Following cmd will be executed: " + Arrays.toString(cmd));
        executor.execute(cmd);
    }

    private void downloadFileFromJar(final String path, final File targetFile) {
        InputStream initialStream = Device.class.getClassLoader().getResourceAsStream(path);
        try {
            FileUtils.copyInputStreamToFile(initialStream, targetFile);
        } catch (IOException e) {
            LOGGER.error("Error during copying of file from the resources. ".concat(e.getMessage()));
        }
    }

    public String getAdbName() {
        if (!StringUtils.isEmpty(getRemoteURL())) {
            return getRemoteURL();
        } else if (!StringUtils.isEmpty(getUdid())) {
            return getUdid();
        } else {
            return "";
        }
    }

    /**
     * Related apps will be uninstall just once for a test launch.
     */
    public void uninstallRelatedApps() {
        if (getOs().equalsIgnoreCase(Type.ANDROID_PHONE.getFamily()) && Configuration.getBoolean(Parameter.UNINSTALL_RELATED_APPS)
                && !clearedDeviceUdids.contains(getUdid())) {
            String mobileApp = Configuration.getMobileApp();
            LOGGER.debug("Current mobile app: ".concat(mobileApp));
            String tempPackage;
            try {
                tempPackage = getApkPackageName(mobileApp);
            } catch (Exception e) {
                LOGGER.info("Error during extraction of package using aapt. It will be extracted from config");
                tempPackage = R.CONFIG.get(SpecialKeywords.MOBILE_APP_PACKAGE);
            }
            final String mobilePackage = tempPackage;
            LOGGER.debug("Current mobile package: ".concat(mobilePackage));
            // in general it has following naming convention:
            // com.projectname.app
            // so we need to remove all apps realted to 1 project
            String projectName = mobilePackage.split("\\.")[1];
            LOGGER.debug("Apps related to current project will be uninstalled. Extracted project: ".concat(projectName));
            List<String> installedPackages = getInstalledPackages();
            // extracted package syntax: package:com.project.app
            installedPackages.parallelStream()
                    .filter(packageName -> (packageName.matches(String.format(".*\\.%s\\..*", projectName))
                            && !packageName.equalsIgnoreCase(String.format("package:%s", mobilePackage))))
                    .collect(Collectors.toList()).forEach((k) -> uninstallApp(k.split(":")[1]));
            clearedDeviceUdids.add(getUdid());
            LOGGER.debug("Udids of devices where applciation was already reinstalled: ".concat(clearedDeviceUdids.toString()));
        } else {
            LOGGER.debug("Related apps had been already uninstalled or flag uninstall_related_apps is disabled.");
        }

    }
    
    /**
     * Extract sys log using adb
     * 
     * @return sys log
     */
    public String getSysLog() {
        int extractionTimeout = 15;
        
        if (isNull()) {
            return "";
        }
        
        if (!DeviceType.Type.ANDROID_PHONE.getFamily().equalsIgnoreCase(getOs())) {
            LOGGER.debug("Logcat log is empty since device is not Android");
            return "";
        }
        LOGGER.debug("Extraction of sys log: " + getAdbName());

        // launch extractor in separate thread to avoid possible hang out
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<String> future = executorService.submit(new Callable<String>() {

         // adb -s UDID logcat -d
            @Override
            public String call() throws Exception {
                LOGGER.debug("Start Syslog extraction");
                String[] cmd = CmdLine.insertCommandsAfter(executor.getDefaultCmd(), "-s", getAdbName(), "logcat", "-d");
                LOGGER.debug("Logcat log has been extracted.");
                StringBuilder tempStr = new StringBuilder();
                executor.execute(cmd).stream().forEach((k) -> tempStr.append(k.concat("\n")));
                return tempStr.toString();
            }
            
        });

        try {
            String logs = future.get(extractionTimeout, TimeUnit.SECONDS);
            LOGGER.debug("Logcat logs: ".concat(logs));
            return logs;
        } catch (TimeoutException e) {
            LOGGER.info(String.format("Sys log hasn't been extracted in %d seconds.", extractionTimeout));
            future.cancel(true);
            return "Syslog hasn't been extracted in seconds. Operation was interrupted.";
        } catch (Exception e) {
//            TODO: add custom handlers for each exceptions based on type
            LOGGER.info(e);
            LOGGER.info("Unknown issue was fired. Empty logs will be used.");
            return "";
        }
       
//        return tempStr.toString();
    }
    
    /**
     * Clear sys log
     */
    public void clearSysLog() {
        if (isNull()) {
            return;
        }
        
        if (!DeviceType.Type.ANDROID_PHONE.getFamily().equalsIgnoreCase(getOs())) {
            LOGGER.debug("Logcat log won't be cleared since device is not Android");
            return;
        }

		if (!isConnected()) {
			//do not use new features if execution is not inside approved cloud
			return;
		}
        
        LOGGER.info(String.format("Test will be started on device: %s:%s", getName(), getAdbName()));
        // adb -s UDID logcat -c
        String[] cmd = CmdLine.insertCommandsAfter(executor.getDefaultCmd(), "-s", getAdbName(), "logcat", "-c");
        executor.execute(cmd);
        LOGGER.debug("Logcat logs were cleared.");
    }
    
    /**
     * Save logcat log for Android (logs will be uploaded in future as artifacts)
     * TODO: for iOS
     * 
     * @return saved file
     */
    public File saveSysLog() {
		if (!isConnected()) {
			//do not use new features if execution is not inside approved cloud
			return null;
		}
		LOGGER.debug("STF is enabled. Sys log will be extracted...");
        String fileName = ReportContext.getTestDir() + "/logcat.log";
        String log = getSysLog();
        if (log.isEmpty()) {
            return null;
        }
        
        File file = null;
        try {
            file = new File(fileName);
            FileUtils.writeStringToFile(file, log, Charset.defaultCharset());
        } catch (IOException e) {
            LOGGER.info(e);
            LOGGER.info("Error has been occured during attempt to extract logcat log.");
        }
        LOGGER.debug("Logcat file path: ".concat(fileName));
        return file;
    }
    
    /**
     * Save xml layout of the application 
     * @param screenshotName - png file name to generate appropriate uix  
     * @return saved file
     */
    public File generateUiDump(String screenshotName) {
        if (isNull()) {
            return null;
        }
        
//        TODO: investigate with iOS: how does it work with iOS
		if (!isConnected()) {
			//do not use new features if execution is not inside approved cloud
			return null;
		}
        
        if (DriverPool.getDrivers().size() == 0) {
            LOGGER.debug("There is no active drivers in the pool.");
            return null;
        }
        // TODO: investigate how to connect screenshot with xml dump: screenshot
        // return File -> Zip png and uix or move this logic to zafira
        
        LOGGER.debug("UI dump generation...");
        WebDriver driver = DriverPool.getDriver();
        String fileName = ReportContext.getTestDir() + String.format("/%s.uix", screenshotName.replace(".png", ""));
        String pageSource = driver.getPageSource();
        pageSource = pageSource.replaceAll(SpecialKeywords.ANDROID_START_NODE, SpecialKeywords.ANDROID_START_UIX_NODE).
                replaceAll(SpecialKeywords.ANDROID_END_NODE, SpecialKeywords.ANDROID_END_UIX_NODE);
        
        File file = null;
        try {
            file = new File(fileName);
            FileUtils.writeStringToFile(file, pageSource, Charset.forName("ASCII"));
        } catch (IOException e) {
            LOGGER.info(e);
            LOGGER.info("Error has been met during attempt to extract xml tree.");
        }
        LOGGER.debug("XML file path: ".concat(fileName));
        return file;
    }

    private boolean isConnected() {
    	try {
	        if (getOs().equalsIgnoreCase(DeviceType.Type.ANDROID_PHONE.getFamily())) {
	            return getConnectedDevices().stream().parallel().anyMatch((m) -> m.contains(getAdbName()));
	        } else {
	            return false;
	        }
    	} catch (Throwable thr) {
    		//do nothing for now
    		return false;
    	}
    }
    
    private List<String> getConnectedDevices() {
        // regexp for connected device. Syntax: udid device
        String deviceUDID = "(.*)\\tdevice$";
        String[] cmd = CmdLine.insertCommandsAfter(executor.getDefaultCmd(), "devices");
        List<String> cmdOutput = executor.execute(cmd);
        List<String> connectedDevices = cmdOutput.stream().parallel().filter((d) -> d.matches(deviceUDID)).collect(Collectors.toList());
        LOGGER.debug("Connected devices: ".concat(connectedDevices.toString()));
        return connectedDevices;
    }

}
