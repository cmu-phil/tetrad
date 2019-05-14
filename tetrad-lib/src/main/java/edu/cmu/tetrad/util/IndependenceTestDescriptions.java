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

import edu.cmu.tetrad.annotation.TestOfIndependenceAnnotations;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * May 14, 2019 11:25:02 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public final class IndependenceTestDescriptions {

    private static final Logger LOGGER = LoggerFactory.getLogger(IndependenceTestDescriptions.class);

    private static final IndependenceTestDescriptions INSTANCE = new IndependenceTestDescriptions();

    private final Map<String, String> descriptions = new HashMap<>();

    private IndependenceTestDescriptions() {
        try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("manual/index.html")) {
            final Document doc = Jsoup.parse(inputStream, StandardCharsets.UTF_8.name(), "");
            getShortNames().forEach(shortName -> {
                Element element = doc.getElementById(shortName);
                if (element != null) {
                    Elements paragraphs = element.children();
                    String desc = paragraphs.stream()
                            .map(p -> p.text().trim())
                            .collect(Collectors.joining("\n"));
                    descriptions.put(shortName, desc);
                }
            });
        } catch (IOException ex) {
            LOGGER.error("Failed to read tetrad HTML manual 'maunal/index.html' file from within the jar.", ex);
        }
    }

    public static IndependenceTestDescriptions getInstance() {
        return INSTANCE;
    }

    public String get(String shortName) {
        String description = descriptions.get(shortName);

        return (description == null)
                ? String.format("Please add a description for %s.", shortName)
                : description;
    }

    private List<String> getShortNames() {
        return TestOfIndependenceAnnotations.getInstance().getAnnotatedClasses().stream()
                .map(e -> e.getAnnotation().command())
                .collect(Collectors.toList());
    }

}
