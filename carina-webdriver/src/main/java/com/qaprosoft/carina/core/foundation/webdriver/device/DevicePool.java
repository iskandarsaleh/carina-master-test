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

import org.apache.log4j.Logger;

import com.qaprosoft.carina.core.foundation.commons.SpecialKeywords;
import com.qaprosoft.carina.core.foundation.utils.R;

public class DevicePool {
    private static final Logger LOGGER = Logger.getLogger(DevicePool.class);

    private static final Device nullDevice = new Device();

    private static ThreadLocal<Device> currentDevice = new ThreadLocal<Device>();

    public static Device registerDevice(Device device) {
    	
        boolean stfEnabled = R.CONFIG
                .getBoolean(SpecialKeywords.CAPABILITIES + "." + SpecialKeywords.STF_ENABLED);
        if (stfEnabled) {
            device.connectRemote();
        }
        
        // register current device to be able to transfer it into Zafira at the end of the test
        setDevice(device);
        Long threadId = Thread.currentThread().getId();
        LOGGER.debug("register device for current thread id: " + threadId + "; device: '" + device.getName() + "'");

        // clear logcat log for Android devices
        device.clearSysLog();
        
        return device;
    }

    public static void deregisterDevice() {
    	Device device = currentDevice.get();
    	if (device == null) {
    		LOGGER.error("Unable to deregister null device!");
    		return;
    	}
    	
        boolean stfEnabled = R.CONFIG
                .getBoolean(SpecialKeywords.CAPABILITIES + "." + SpecialKeywords.STF_ENABLED);
        if (stfEnabled) {
            device.disconnectRemote();
        }
        
    	
        currentDevice.remove();
    }

    public static Device getDevice() {
        long threadId = Thread.currentThread().getId();
        Device device = currentDevice.get();
        if (device == null) {
            LOGGER.debug("Current device is null for thread: " + threadId);
            device = nullDevice;
        } else if (device.getName().isEmpty()) {
            LOGGER.debug("Current device name is empty! nullDevice was used for thread: " + threadId);
        } else {
            LOGGER.debug("Current device name is '" + device.getName() + "' for thread: " + threadId);
        }
        return device;
    }

    public static Device getNullDevice() {
        return nullDevice;
    }

    public static boolean isRegistered() {
        Device device = currentDevice.get();
        return device != null;
    }
    
    private static void setDevice(Device device) {
        long threadId = Thread.currentThread().getId();
        LOGGER.debug("Set current device '" + device.getName() + "' to thread: " + threadId);
        currentDevice.set(device);
    }

}