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
package com.qaprosoft.carina.core.foundation.retry;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import com.qaprosoft.carina.core.foundation.utils.R;

public class RetryTest {

    @Test(priority = 1)
    public void testInitRetryCounter() {
        RetryCounter.initCounter();
        int count = RetryCounter.getRunCount();
        Assert.assertEquals(count, 0);
    }

    @Test(priority = 2)
    public void testDoubleInitRetryCounter() {
        RetryCounter.initCounter();
        RetryCounter.initCounter();
        int count = RetryCounter.getRunCount();
        Assert.assertEquals(count, 0);
    }

    @Test(priority = 3)
    public void testRetryCounter() {
        RetryCounter.initCounter();
        RetryCounter.incrementRunCount();
        int count = RetryCounter.getRunCount();
        Assert.assertEquals(count, 1);
    }

    @Test(priority = 4)
    public void testResetRetryCount() {
        RetryCounter.initCounter();
        RetryCounter.incrementRunCount();
        int count = RetryCounter.getRunCount();
        Assert.assertEquals(count, 1);

        RetryCounter.resetCounter();
        count = RetryCounter.getRunCount();
        Assert.assertEquals(count, 0);
    }

    @Test(priority = 5)
    public void testGetMaxRetryCountForTest() {
        R.CONFIG.put("retry_count", "1");
        Assert.assertEquals(RetryAnalyzer.getMaxRetryCountForTest(), 1);
    }

    @AfterMethod
    public void resetCounter() {
        RetryCounter.resetCounter();
    }

}
