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
package com.qaprosoft.carina.core.foundation.utils.common;

import org.apache.log4j.Logger;

import com.qaprosoft.carina.core.foundation.performance.ACTION_NAME;
import com.qaprosoft.carina.core.foundation.performance.Timer;

public class CommonUtils {

    private static final Logger LOGGER = Logger.getLogger(CommonUtils.class);
    
    private CommonUtils() {
    	//hide public constructor
    }

    /**
     * pause
     *
     * @param timeout Number
     */
    public static void pause(Number timeout) {
    	Timer.start(ACTION_NAME.PAUSE);
        LOGGER.debug(String.format("Will wait for %s seconds", timeout));
        try {
            Float timeoutFloat = timeout.floatValue() * 1000;
            long timeoutLong = timeoutFloat.longValue();
            Thread.sleep(timeoutLong);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        LOGGER.debug("Pause is overed. Keep going..");
        Timer.stop(ACTION_NAME.PAUSE);
    }
}
