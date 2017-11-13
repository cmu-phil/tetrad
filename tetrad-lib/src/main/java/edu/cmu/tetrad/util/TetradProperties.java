/*
 * Copyright (C) 2017 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 *
 * Nov 10, 2017 4:14:31 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TetradProperties {

    private static final TetradProperties INSTANCE = new TetradProperties();

    private final Map<String, String> props = new HashMap<>();

    private TetradProperties() {
        final Properties properties = new Properties();
        try (InputStream inputStream = TetradProperties.class.getResourceAsStream("/tetrad-lib.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
        }

        properties.stringPropertyNames().forEach(e -> props.put(e, properties.getProperty(e)));
    }

    public static TetradProperties getInstance() {
        return INSTANCE;
    }

    public Set<String> getProperties() {
        return props.keySet();
    }

    public String getValue(String property) {
        if (property == null) {
            return null;
        }

        return props.get(property);
    }

}
