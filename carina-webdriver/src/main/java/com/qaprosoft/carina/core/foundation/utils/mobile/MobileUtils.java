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
package com.qaprosoft.carina.core.foundation.utils.mobile;

import java.time.Duration;

import org.apache.log4j.Logger;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.events.EventFiringWebDriver;
import org.testng.Assert;

import com.qaprosoft.carina.core.foundation.commons.SpecialKeywords;
import com.qaprosoft.carina.core.foundation.utils.Configuration;
import com.qaprosoft.carina.core.foundation.utils.Messager;
import com.qaprosoft.carina.core.foundation.utils.Configuration.Parameter;
import com.qaprosoft.carina.core.foundation.utils.android.AndroidService;
import com.qaprosoft.carina.core.foundation.utils.android.DeviceTimeZone;
import com.qaprosoft.carina.core.foundation.webdriver.DriverHelper;
import com.qaprosoft.carina.core.foundation.webdriver.DriverPool;
import com.qaprosoft.carina.core.foundation.webdriver.decorator.ExtendedWebElement;
import com.qaprosoft.carina.core.foundation.webdriver.device.DevicePool;

import io.appium.java_client.MobileDriver;
import io.appium.java_client.TouchAction;
import io.appium.java_client.touch.LongPressOptions;
import io.appium.java_client.touch.WaitOptions;
import io.appium.java_client.touch.offset.ElementOption;
import io.appium.java_client.touch.offset.PointOption;

public class MobileUtils {
    protected static final Logger LOGGER = Logger.getLogger(MobileUtils.class);

    public enum Direction {
        LEFT,
        RIGHT,
        UP,
        DOWN,
        VERTICAL,
        HORIZONTAL,
        VERTICAL_DOWN_FIRST,
        HORIZONTAL_RIGHT_FIRST
    }

    protected static final long EXPLICIT_TIMEOUT = Configuration.getLong(Parameter.EXPLICIT_TIMEOUT);

    protected static final int MINIMUM_TIMEOUT = 2;

    private static final int DEFAULT_TOUCH_ACTION_DURATION = 1000;
    private static final int DEFAULT_MAX_SWIPE_COUNT = 50;
    private static final int DEFAULT_MIN_SWIPE_COUNT = 1;
    
    protected static DriverHelper helper = new DriverHelper();

    /**
     * Tap with TouchAction by the center of element
     *
     * @param element ExtendedWebElement
     */
    public static void tap(ExtendedWebElement element) {
        Point point = element.getLocation();
        Dimension size = helper.performIgnoreException(() -> element.getSize());
		
        tap(point.getX() + size.getWidth() / 2, point.getY() + size.getHeight() / 2);
    }

    /**
     * Tap with TouchAction by coordinates with default 1000ms duration
     *
     * @param startx int
     * @param starty int
     */
    public static void tap(int startx, int starty) {
        tap(startx, starty, DEFAULT_TOUCH_ACTION_DURATION);
    }

    /**
     * tap with TouchActions slowly to imitate log tap on element
     * 
     * @param elem ExtendedWebElement
     *            element
     */
    public static void longTap(ExtendedWebElement elem) {
    	Dimension size = helper.performIgnoreException(() -> elem.getSize());
    	
        int width = size.getWidth();
        int height = size.getHeight();

        int x = elem.getLocation().getX() + width / 2;
        int y = elem.getLocation().getY() + height / 2;
        try {
            MobileUtils.swipe(x, y, x, y, 2500);
        } catch (Exception e) {
            LOGGER.error("Exception: " + e);
        }
    }

    /**
     * Tap and Hold (LongPress) on element
     *
     * @param element ExtendedWebElement
     * @return boolean
     */
    public static boolean longPress(ExtendedWebElement element) {
    	//TODO: SZ migrate to FluentWaits
        try {
            WebDriver driver = getDriver();
            @SuppressWarnings("rawtypes")
			TouchAction<?> action = new TouchAction((MobileDriver<?>) driver);
            LongPressOptions options = LongPressOptions.longPressOptions().withElement(ElementOption.element(element.getElement()));
            action.longPress(options).release().perform();
            return true;
        } catch (Exception e) {
            LOGGER.info("Error occurs during longPress: " + e, e);
        }
        return false;
    }

    /**
     * Tap with TouchAction by coordinates with custom duration
     *
     * @param startx int
     * @param starty int
     * @param duration int
     */
    public static void tap(int startx, int starty, int duration) {
        //TODO: add Screenshot.capture()
        try {
            @SuppressWarnings("rawtypes")
            TouchAction<?> touchAction = new TouchAction((MobileDriver<?>) getDriver());
            PointOption<?> startPoint = PointOption.point(startx, starty);
            WaitOptions waitOptions = WaitOptions.waitOptions(Duration.ofMillis(duration));

            if (duration == 0) {
                // do not perform waiter as using 6.0.0. appium java client we do longpress instead of simple tap even with 0 wait duration
                touchAction.press(startPoint).release().perform();
            } else {
                touchAction.press(startPoint).waitAction(waitOptions).release().perform();
            }
            Messager.TAP_EXECUTED.info(String.valueOf(startx), String.valueOf(starty));
        } catch (Exception e) {
            Messager.TAP_NOT_EXECUTED.error(String.valueOf(startx), String.valueOf(starty));
            throw e;
        }
    }

    /**
     * swipe till element using TouchActions
     * 
     * @param element ExtendedWebElement
     * @return boolean
     */
    public static boolean swipe(final ExtendedWebElement element) {
        return swipe(element, null, Direction.UP, DEFAULT_MAX_SWIPE_COUNT, DEFAULT_TOUCH_ACTION_DURATION);
    }

    /**
     * swipe till element using TouchActions
     * 
     * @param element ExtendedWebElement
     * @param count int
     * @return boolean
     */
    public static boolean swipe(final ExtendedWebElement element, int count) {
        return swipe(element, null, Direction.UP, count, DEFAULT_TOUCH_ACTION_DURATION);
    }

    /**
     * swipe till element using TouchActions
     * 
     * @param element ExtendedWebElement
     * @param direction Direction
     * @return boolean
     */
    public static boolean swipe(final ExtendedWebElement element, Direction direction) {
        return swipe(element, null, direction, DEFAULT_MAX_SWIPE_COUNT, DEFAULT_TOUCH_ACTION_DURATION);
    }

    /**
     * swipe till element using TouchActions
     * 
     * @param element ExtendedWebElement
     * @param count int
     * @param duration int
     * @return boolean
     */
    public static boolean swipe(final ExtendedWebElement element, int count, int duration) {
        return swipe(element, null, Direction.UP, count, duration);
    }

    /**
     * swipe till element using TouchActions
     * 
     * @param element ExtendedWebElement
     * @param direction Direction
     * @param count int
     * @param duration int
     * @return boolean
     */
    public static boolean swipe(final ExtendedWebElement element, Direction direction, int count, int duration) {
        return swipe(element, null, direction, count, duration);
    }

    /**
     * Swipe inside container in default direction - Direction.UP
     * Number of attempts is limited by count argument
     * <p>
     *
     * @param element
     *            ExtendedWebElement
     * @param container
     *            ExtendedWebElement
     * @param count
     *            int
     * @return boolean
     */
    public static boolean swipe(ExtendedWebElement element, ExtendedWebElement container, int count) {
        return swipe(element, container, Direction.UP, count, DEFAULT_TOUCH_ACTION_DURATION);
    }

    /**
     * Swipe inside container in default direction - Direction.UP
     * Number of attempts is limited by 5
     * <p>
     *
     * @param element
     *            ExtendedWebElement
     * @param container
     *            ExtendedWebElement
     * @return boolean
     */
    public static boolean swipe(ExtendedWebElement element, ExtendedWebElement container) {
        return swipe(element, container, Direction.UP, DEFAULT_MAX_SWIPE_COUNT, DEFAULT_TOUCH_ACTION_DURATION);
    }

    /**
     * Swipe inside container in specified direction
     * Number of attempts is limited by 5
     * <p>
     *
     * @param element
     *            ExtendedWebElement
     * @param container
     *            ExtendedWebElement
     * @param direction
     *            Direction
     * @return boolean
     */
    public static boolean swipe(ExtendedWebElement element, ExtendedWebElement container, Direction direction) {
        return swipe(element, container, direction, DEFAULT_MAX_SWIPE_COUNT, DEFAULT_TOUCH_ACTION_DURATION);
    }

    /**
     * Swipe inside container in specified direction with default pulling timeout in 1000ms
     * Number of attempts is limited by count argument
     * <p>
     *
     * @param element
     *            ExtendedWebElement
     * @param container
     *            ExtendedWebElement
     * @param direction
     *            Direction
     * @param count
     *            int
     * @return boolean
     */
    public static boolean swipe(ExtendedWebElement element, ExtendedWebElement container, Direction direction,
            int count) {
        return swipe(element, container, direction, count, DEFAULT_TOUCH_ACTION_DURATION);
    }

    /**
     * Swipe to element inside container in specified direction while element
     * will not be present on the screen. If element is on the screen already,
     * scrolling will not be performed.
     * <p>
     *
     * @param element
     *            element to which it will be scrolled
     * @param container
     *            element, inside which scrolling is expected. null to scroll
     * @param direction
     *            direction of scrolling. HORIZONTAL and VERTICAL support swiping in both directions automatically
     * @param count
     *            for how long to scroll, ms
     * @param duration
     *            pulling timeout, ms
     * @return boolean
     */
    public static boolean swipe(ExtendedWebElement element, ExtendedWebElement container, Direction direction,
            int count, int duration) {

        boolean isVisible = element.isVisible(1);
        if (isVisible) {
            // no sense to continue;
            LOGGER.info("element already present before swipe: " + element.getNameWithLocator().toString());
            return true;
        } else {
        	LOGGER.info("swiping to element: " + element.getNameWithLocator().toString());
        }

        Direction oppositeDirection = Direction.DOWN;
        boolean bothDirections = false;

        switch (direction) {
        case UP:
            oppositeDirection = Direction.DOWN;
            break;
        case DOWN:
            oppositeDirection = Direction.UP;
            break;
        case LEFT:
            oppositeDirection = Direction.RIGHT;
            break;
        case RIGHT:
            oppositeDirection = Direction.LEFT;
            break;
        case HORIZONTAL:
            direction = Direction.LEFT;
            oppositeDirection = Direction.RIGHT;
            bothDirections = true;
            break;
        case HORIZONTAL_RIGHT_FIRST:
            direction = Direction.RIGHT;
            oppositeDirection = Direction.LEFT;
            bothDirections = true;
            break;
        case VERTICAL:
            direction = Direction.UP;
            oppositeDirection = Direction.DOWN;
            bothDirections = true;
            break;
        case VERTICAL_DOWN_FIRST:
            direction = Direction.DOWN;
            oppositeDirection = Direction.UP;
            bothDirections = true;
            break;
        default:
            throw new RuntimeException("Unsupported direction for swipeInContainerTillElement: " + direction);
        }

        int currentCount = count;

        while (!isVisible && currentCount-- > 0) {
            LOGGER.debug("Element not present! Swipe " + direction + " will be executed to element: " + element.getNameWithLocator().toString());
            swipeInContainer(container, direction, duration);

            LOGGER.info("Swipe was executed. Attempts remain: " + currentCount);
            isVisible = element.isVisible(1);
        }

        currentCount = count;
        while (bothDirections && !isVisible && currentCount-- > 0) {
            LOGGER.debug(
                    "Element not present! Swipe " + oppositeDirection + " will be executed to element: " + element.getNameWithLocator().toString());
            swipeInContainer(container, oppositeDirection, duration);
            LOGGER.info("Swipe was executed. Attempts remain: " + currentCount);
            isVisible = element.isVisible(1);
        }

        LOGGER.info("Result: " + isVisible);
        return isVisible;
    }

    /**
     * Swipe by coordinates using TouchAction (platform independent)
     *
     * @param startx int
     * @param starty int
     * @param endx int
     * @param endy int
     * @param duration int Millis
     */
    @SuppressWarnings("rawtypes")
	public static void swipe(int startx, int starty, int endx, int endy, int duration) {
        LOGGER.debug("Starting swipe...");
        WebDriver drv = getDriver();

        LOGGER.debug("Getting driver dimension size...");
        Dimension scrSize = helper.performIgnoreException(() -> drv.manage().window().getSize());
        LOGGER.debug("Finished driver dimension size...");
        // explicitly limit range of coordinates
        if (endx >= scrSize.width) {
            LOGGER.warn("endx coordinate is bigger then device width! It will be limited!");
            endx = scrSize.width - 1;
        } else {
            endx = Math.max(1, endx);
        }

        if (endy >= scrSize.height) {
            LOGGER.warn("endy coordinate is bigger then device height! It will be limited!");
            endy = scrSize.height - 1;
        } else {
            endy = Math.max(1, endy);
        }

		LOGGER.debug("startx: " + startx + "; starty: " + starty + "; endx: " + endx + "; endy: " + endy
				+ "; duration: " + duration);

		PointOption<?> startPoint = PointOption.point(startx, starty);
		PointOption<?> endPoint = PointOption.point(endx, endy);
		WaitOptions waitOptions = WaitOptions.waitOptions(Duration.ofMillis(duration));
        
		new TouchAction((MobileDriver<?>) drv).press(startPoint).waitAction(waitOptions).moveTo(endPoint).release()
				.perform();

        LOGGER.debug("Finished swipe...");
    }

    /**
     * swipeInContainer
     *
     * @param container ExtendedWebElement
     * @param direction Direction
     * @param duration int
     * @return boolean
     */
    public static boolean swipeInContainer(ExtendedWebElement container, Direction direction, int duration) {
        return swipeInContainer(container, direction, DEFAULT_MIN_SWIPE_COUNT, duration);
    }

    /**
     * swipeInContainer
     * 
     * @param container ExtendedWebElement
     * @param direction Direction
     * @param count int
     * @param duration int
     * @return boolean
     */
    public static boolean swipeInContainer(ExtendedWebElement container, Direction direction, int count, int duration) {

        int startx = 0;
        int starty = 0;
        int endx = 0;
        int endy = 0;

        Point elementLocation = null;
        Dimension elementDimensions = null;
        
		if (container == null) {
			// whole screen/driver is a container!
			WebDriver driver = getDriver();
			elementLocation = new Point(0, 0); // initial left corner for that case

			elementDimensions = helper.performIgnoreException(() -> driver.manage().window().getSize());
		} else {
			if (container.isElementNotPresent(5)) {
				Assert.fail("Cannot swipe! Impossible to find element " + container.getName());
			}
			elementLocation = container.getLocation();
			elementDimensions = helper.performIgnoreException(() -> container.getSize());
		}

        double minCoefficient = 0.3;
        double maxCoefficient = 0.6;

        // calculate default coefficient based on OS type
        String os = DevicePool.getDevice().getOs();
        if (os.equalsIgnoreCase(SpecialKeywords.ANDROID)) {
            minCoefficient = 0.25;
            maxCoefficient = 0.5;
        } else if (os.equalsIgnoreCase(SpecialKeywords.IOS) || os.equalsIgnoreCase(SpecialKeywords.MAC)) {
            minCoefficient = 0.25;
            maxCoefficient = 0.8;
        }

        switch (direction) {
        case LEFT:
            starty = endy = elementLocation.getY() + Math.round(elementDimensions.getHeight() / 2);

            startx = (int) (elementLocation.getX() + Math.round(maxCoefficient * elementDimensions.getWidth()));
            endx = (int) (elementLocation.getX() + Math.round(minCoefficient * elementDimensions.getWidth()));
            break;
        case RIGHT:
            starty = endy = elementLocation.getY() + Math.round(elementDimensions.getHeight() / 2);

            startx = (int) (elementLocation.getX() + Math.round(minCoefficient * elementDimensions.getWidth()));
            endx = (int) (elementLocation.getX() + Math.round(maxCoefficient * elementDimensions.getWidth()));
            break;
        case UP:
            startx = endx = elementLocation.getX() + Math.round(elementDimensions.getWidth() / 2);

            starty = (int) (elementLocation.getY() + Math.round(maxCoefficient * elementDimensions.getHeight()));
            endy = (int) (elementLocation.getY() + Math.round(minCoefficient * elementDimensions.getHeight()));
            break;
        case DOWN:
            startx = endx = elementLocation.getX() + Math.round(elementDimensions.getWidth() / 2);

            starty = (int) (elementLocation.getY() + Math.round(minCoefficient * elementDimensions.getHeight()));
            endy = (int) (elementLocation.getY() + Math.round(maxCoefficient * elementDimensions.getHeight()));
            break;
        default:
            throw new RuntimeException("Unsupported direction: " + direction);
        }

        LOGGER.debug(String.format("Swipe from (X = %d; Y = %d) to (X = %d; Y = %d)", startx, starty, endx, endy));

        try {
            for (int i = 0; i < count; ++i) {
                swipe(startx, starty, endx, endy, duration);
            }
            return true;
        } catch (Exception e) {
            LOGGER.error(String.format("Error during Swipe from (X = %d; Y = %d) to (X = %d; Y = %d): %s", startx, starty, endx, endy, e));
        }
        return false;
    }



    /**
     * Swipe up several times
     * 
     * @param times int
     * @param duration int
     */
    public static void swipeUp(final int times, final int duration) {
        for (int i = 0; i < times; i++) {
            swipeUp(duration);
        }
    }

    /**
     * Swipe up
     * 
     * @param duration int
     */
    public static void swipeUp(final int duration) {
        LOGGER.info("Swipe up will be executed.");
        swipeInContainer(null, Direction.UP, duration);
    }

    /**
     * Swipe down several times
     * 
     * @param times int
     * @param duration int
     */
    public static void swipeDown(final int times, final int duration) {
        for (int i = 0; i < times; i++) {
            swipeDown(duration);
        }
    }

    /**
     * Swipe down
     * 
     * @param duration int
     */
    public static void swipeDown(final int duration) {
        LOGGER.info("Swipe down will be executed.");
        swipeInContainer(null, Direction.DOWN, duration);
    }

    /**
     * Swipe left several times
     * 
     * @param times int
     * @param duration int
     */
    public static void swipeLeft(final int times, final int duration) {
        for (int i = 0; i < times; i++) {
            swipeLeft(duration);
        }
    }

    /**
     * Swipe left
     * 
     * @param duration int
     */
    public static void swipeLeft(final int duration) {
        LOGGER.info("Swipe left will be executed.");
        swipeLeft(null, duration);
    }

	/**
	 * Swipe left in container
	 * 
	 * @param container
	 *            ExtendedWebElement
	 * @param duration
	 *            int
	 */
    public static void swipeLeft(ExtendedWebElement container, final int duration) {
        LOGGER.info("Swipe left will be executed.");
        swipeInContainer(container, Direction.LEFT, duration);
    }

    /**
     * Swipe right several times
     * 
     * @param times int
     * @param duration int
     */
    public static void swipeRight(final int times, final int duration) {
        for (int i = 0; i < times; i++) {
            swipeRight(duration);
        }
    }

    /**
     * Swipe right
     * 
     * @param duration int
     */
    public static void swipeRight(final int duration) {
        LOGGER.info("Swipe right will be executed.");
        swipeRight(null, duration);
    }

	/**
	 * Swipe right in container
	 * 
	 * @param container
	 *            ExtendedWebElement
	 * @param duration
	 *            int
	 */
    public static void swipeRight(ExtendedWebElement container, final int duration) {
        LOGGER.info("Swipe right will be executed.");
        swipeInContainer(container, Direction.RIGHT, duration);
    }

    /**
     * Set Android Device Default TimeZone And Language based on config or to GMT and En
     * Without restoring actual focused apk.
     */
    public static void setDeviceDefaultTimeZoneAndLanguage() {
        setDeviceDefaultTimeZoneAndLanguage(false);
    }

    /**
     * set Android Device Default TimeZone And Language based on config or to GMT and En
     * 
     * @param returnAppFocus - if true store actual Focused apk and activity, than restore after setting Timezone and Language.
     */
    public static void setDeviceDefaultTimeZoneAndLanguage(boolean returnAppFocus) {
        try {
            String baseApp = "";
            String os = DevicePool.getDevice().getOs();
            if (os.equalsIgnoreCase(SpecialKeywords.ANDROID)) {

                AndroidService androidService = AndroidService.getInstance();

                if (returnAppFocus) {
                    baseApp = androidService.getCurrentFocusedApkDetails();
                }

                String deviceTimezone = Configuration.get(Parameter.DEFAULT_DEVICE_TIMEZONE);
                String deviceTimeFormat = Configuration.get(Parameter.DEFAULT_DEVICE_TIME_FORMAT);
                String deviceLanguage = Configuration.get(Parameter.DEFAULT_DEVICE_LANGUAGE);

                DeviceTimeZone.TimeFormat timeFormat = DeviceTimeZone.TimeFormat.parse(deviceTimeFormat);
                DeviceTimeZone.TimeZoneFormat timeZone = DeviceTimeZone.TimeZoneFormat.parse(deviceTimezone);

                LOGGER.info("Set device timezone to " + timeZone.toString());
                LOGGER.info("Set device time format to " + timeFormat.toString());
                LOGGER.info("Set device language to " + deviceLanguage);

                boolean timeZoneChanged = androidService.setDeviceTimeZone(timeZone.getTimeZone(), timeZone.getSettingsTZ(), timeFormat);
                boolean languageChanged = androidService.setDeviceLanguage(deviceLanguage);

                LOGGER.info(String.format("Device TimeZone was changed to timeZone '%s' : %s. Device Language was changed to language '%s': %s",
                        deviceTimezone,
                        timeZoneChanged, deviceLanguage, languageChanged));

                if (returnAppFocus) {
                    androidService.openApp(baseApp);
                }

            } else {
                LOGGER.info(String.format("Current OS is %s. But we can set default TimeZone and Language only for Android.", os));
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
    }

	/**
	 * Hide keyboard if needed
	 */
	public static void hideKeyboard() {
		try {
			((MobileDriver<?>) getDriver()).hideKeyboard();
		} catch (Exception e) {
			if (!e.getMessage().contains("Soft keyboard not present, cannot hide keyboard")) {
				LOGGER.error("Exception appears during hideKeyboard: " + e);
			}
		}
	}

	public static WebDriver getDriver() {
		WebDriver drv = DriverPool.getDriver();
		if (drv instanceof EventFiringWebDriver) {
			return ((EventFiringWebDriver) drv).getWrappedDriver();
		} else {
			return drv;
		}
	}

}
