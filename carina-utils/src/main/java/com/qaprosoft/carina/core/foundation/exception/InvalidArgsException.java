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
package com.qaprosoft.carina.core.foundation.exception;

/*
 * Exception may be thrown when invalid test arguments were specified.
 * 
 * @author Alex Khursevich
 */
public class InvalidArgsException extends RuntimeException {
    private static final long serialVersionUID = 1204359727358878608L;

    public InvalidArgsException() {
        super("Invalid test arguments exception");
    }

    public InvalidArgsException(String msg) {
        super("Invalid test arguments exception: " + msg);
    }
}
