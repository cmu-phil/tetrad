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

import edu.cmu.tetrad.annotation.ScoreAnnotations;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * May 14, 2019 11:23:54 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public final class ScoreDescriptions {

//    private static final Logger LOGGER = LoggerFactory.getLogger(ScoreDescriptions.class);

    private static final ScoreDescriptions INSTANCE = new ScoreDescriptions();

    private final Map<String, String> descriptions = new HashMap<>();

    private ScoreDescriptions() {
        try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("docs/manual/index.html")) {
            Document doc = Jsoup.parse(inputStream, StandardCharsets.UTF_8.name(), "");
            getShortNames().forEach(shortName -> {
                Element element = doc.getElementById(shortName);
                if (element != null) {
                    Elements paragraphs = element.children();
                    String desc = paragraphs.stream()
                            .map(p -> p.text().trim())
                            .collect(Collectors.joining("\n"));
                    this.descriptions.put(shortName, desc);
                }
            });
        } catch (IOException ex) {
            TetradLogger.getInstance().log("Failed to read tetrad HTML manual 'maunal/index.html' file from within the jar.");
//            ScoreDescriptions.LOGGER.error("Failed to read tetrad HTML manual 'maunal/index.html' file from within the jar.", ex);
        }
    }

    /**
     * <p>getInstance.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.ScoreDescriptions} object
     */
    public static ScoreDescriptions getInstance() {
        return ScoreDescriptions.INSTANCE;
    }

    /**
     * <p>get.</p>
     *
     * @param shortName a {@link java.lang.String} object
     * @return a {@link java.lang.String} object
     */
    public String get(String shortName) {
        String description = this.descriptions.get(shortName);

        return (description == null)
                ? String.format("Please add a description for %s.", shortName)
                : description;
    }

    private List<String> getShortNames() {
        return ScoreAnnotations.getInstance().getAnnotatedClasses().stream()
                .map(e -> e.annotation().command())
                .collect(Collectors.toList());
    }

}
