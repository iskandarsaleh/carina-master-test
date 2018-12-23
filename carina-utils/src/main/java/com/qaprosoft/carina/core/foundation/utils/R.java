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
package com.qaprosoft.carina.core.foundation.utils;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import com.qaprosoft.carina.core.foundation.commons.SpecialKeywords;
import com.qaprosoft.carina.core.foundation.exception.InvalidConfigurationException;

/**
 * R - loads properties from resource files.
 *
 * @author Aliaksei_Khursevich
 *         <a href="mailto:hursevich@gmail.com">Aliaksei_Khursevich</a>
 *
 */
public enum R {
    API("api.properties"),

    CONFIG("config.properties"),

    TESTDATA("testdata.properties"),

    EMAIL("email.properties"),

    REPORT("report.properties"),

    DATABASE("database.properties"),

    ZAFIRA("zafira.properties");

    private static final Logger LOGGER = Logger.getLogger(R.class);

    private static final String OVERRIDE_SIGN = "_";

    private String resourceFile;

    private static Map<String, Properties> propertiesHolder = new HashMap<String, Properties>();

    static {
        for (R resource : values()) {
            try {
                Properties properties = new Properties();

                URL baseResource = ClassLoader.getSystemResource(resource.resourceFile);
                if (baseResource != null) {
                    properties.load(baseResource.openStream());
                    LOGGER.info("Base properties loaded: " + resource.resourceFile);
                }

                URL overrideResource;
                String resourceName = OVERRIDE_SIGN + resource.resourceFile;
                while ((overrideResource = ClassLoader.getSystemResource(resourceName)) != null) {
                    properties.load(overrideResource.openStream());
                    LOGGER.info("Override properties loaded: " + resourceName);
                    resourceName = OVERRIDE_SIGN + resourceName;
                }

                // TODO: may we skip the validation?
                // if(CONFIG.equals(resource) && !PlaceholderResolver.isValid(properties))
                // {
                // throw new PlaceholderResolverException();
                // }

                // Overrides properties by systems values
                for (Object key : properties.keySet()) {
                    String systemValue = System.getProperty((String) key);
                    if (!StringUtils.isEmpty(systemValue)) {
                        properties.put(key, systemValue);
                    }
                }

                if (resource.resourceFile.contains("config.properties")) {
                    // TODO: investigate if we needed env variables analysis using System.getenv() as well
                    final String prefix = SpecialKeywords.CAPABILITIES + ".";
                    // read all java arguments and redefine capabilities.* items
                    @SuppressWarnings({ "unchecked", "rawtypes" })
                    Map<String, String> javaProperties = new HashMap(System.getProperties());
                    for (Map.Entry<String, String> entry : javaProperties.entrySet()) {
                        String key = entry.getKey();
                        if (key.toLowerCase().startsWith(prefix)) {
                            String value = entry.getValue();
                            if (!StringUtils.isEmpty(value)) {
                                properties.put(key, value);
                            }
                        }
                    }
                }
                propertiesHolder.put(resource.resourceFile, properties);
            } catch (Exception e) {
                throw new InvalidConfigurationException("Invalid config in '" + resource + "': " + e.getMessage());
            }
        }
    }

    R(String resourceKey) {
        this.resourceFile = resourceKey;
    }

    public void put(String key, String value) {
        propertiesHolder.get(resourceFile).put(key, value);
    }
    
    /**
     * Verify if key is declared in data map.
     * @return boolean
     */
    public boolean containsKey(String key) {
    	return CONFIG.resourceFile.equals(resourceFile) ? propertiesHolder.get(resourceFile).containsKey(key)
                : propertiesHolder.get(resourceFile).containsKey(key);
    }

    /**
     * Returns value either from systems properties or config properties context.
     * Systems properties have higher priority.
     * Decryption is performed if required.
     * 
     * @param key Requested key
     * @return config value
     */
    public String get(String key) {
        String value = CONFIG.resourceFile.equals(resourceFile) ? PlaceholderResolver.resolve(propertiesHolder.get(resourceFile), key)
                : propertiesHolder.get(resourceFile).getProperty(key);
        // TODO: why we return empty instead of null?
        // [VD] as designed empty MUST be returned
        return value != null ? value : StringUtils.EMPTY;
    }

    public int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public long getLong(String key) {
        return Long.parseLong(get(key));
    }

    public double getDouble(String key) {
        return Double.parseDouble(get(key));
    }

    public boolean getBoolean(String key) {
        return Boolean.valueOf(get(key));
    }

    public static String getResourcePath(String resource) {
        String path = StringUtils.removeStart(ClassLoader.getSystemResource(resource).getPath(), "/");
        path = StringUtils.replaceChars(path, "/", "\\");
        path = StringUtils.replaceChars(path, "!", "");
        return path;
    }

    public Properties getProperties() {
        return propertiesHolder.get(resourceFile);
    }

}
