/*
 * Copyright (C) 2019 University of Pittsburgh.
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
package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.data.DelimiterType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Sep 27, 2019 12:00:14 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public final class KnowledgeUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(KnowledgeUtils.class);

    private static final String KNOWLEDGE_HEADER = "/knowledge";
    private static final String ADDTEMPORAL_HEADER = "addtemporal";
    private static final String FORBIDDIRECT_HEADER = "forbiddirect";
    private static final String FORBIDDENGROUP_HEADER = "forbiddengroup";
    private static final String REQUIREDIRECT_HEADER = "requiredirect";
    private static final String REQUIREDGROUP_HEADER = "requiredgroup";

    private KnowledgeUtils() {
    }

    public static IKnowledge parse(File knowledgeFile) {
        return parse(knowledgeFile, "//", DelimiterType.WHITESPACE.getPattern());
    }

    public static IKnowledge parse(File knowledgeFile, String commentMarker, Pattern delimiter) {
        IKnowledge knowledge = new Knowledge2();
        try (BufferedReader reader = Files.newBufferedReader(knowledgeFile.toPath())) {
            int lineNum = 0;
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith(commentMarker)) {
                    continue;
                }

                if (!KNOWLEDGE_HEADER.equalsIgnoreCase(line)) {
                    throw new IllegalArgumentException(String.format("Missing header: %s%n", KNOWLEDGE_HEADER));
                }

                break;
            }

            boolean addTemporal = false;
            boolean forbiddenGroup = false;
            boolean requiredGroup = false;
            boolean forbiddirect = false;
            boolean requireDirect = false;
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith(commentMarker)) {
                    continue;
                }

                if (ADDTEMPORAL_HEADER.equalsIgnoreCase(line)) {
                    addTemporal = true;
                    forbiddenGroup = false;
                    requiredGroup = false;
                    forbiddirect = false;
                    requireDirect = false;
                    continue;
                } else if (FORBIDDENGROUP_HEADER.equalsIgnoreCase(line)) {
                    addTemporal = false;
                    forbiddenGroup = true;
                    requiredGroup = false;
                    forbiddirect = false;
                    requireDirect = false;
                    continue;
                } else if (REQUIREDGROUP_HEADER.equalsIgnoreCase(line)) {
                    addTemporal = false;
                    forbiddenGroup = false;
                    requiredGroup = true;
                    forbiddirect = false;
                    requireDirect = false;
                    continue;
                } else if (FORBIDDIRECT_HEADER.equalsIgnoreCase(line)) {
                    addTemporal = false;
                    forbiddenGroup = false;
                    requiredGroup = false;
                    forbiddirect = true;
                    requireDirect = false;
                    continue;
                } else if (REQUIREDIRECT_HEADER.equalsIgnoreCase(line)) {
                    addTemporal = false;
                    forbiddenGroup = false;
                    requiredGroup = false;
                    forbiddirect = false;
                    requireDirect = true;
                    continue;
                }

                if (addTemporal) {
                    int tier = -1;
                    String[] fields = delimiter.split(line);
                    for (int i = 0; i < fields.length; i++) {
                        String s = fields[i].trim();
                        if (i == 0) {
                            boolean forbiddenWithin = s.endsWith("*");
                            String tierStr = forbiddenWithin
                                    ? s.substring(0, s.length() - 1)
                                    : s;

                            // parse tier
                            try {
                                tier = Integer.parseInt(tierStr) - 1;
                            } catch (NumberFormatException exception) {
                                String errMsg = String.format("Line %d: Unable to parse tier.", lineNum);
                                LOGGER.error(errMsg, exception);

                                throw new NumberFormatException(errMsg);
                            }

                            // validate tier
                            if (tier < 0) {
                                String errMsg = String.format("Line %d: Tiers must be 1, 2...", lineNum);
                                throw new IllegalArgumentException(errMsg);
                            }

                            // set forbidden, if any
                            if (forbiddenWithin) {
                                knowledge.setTierForbiddenWithin(tier, true);
                            }
                        } else {
                            String name = s.replaceAll(" ", ".");
                            knowledge.addToTier(tier, name);

                            LOGGER.info(String.format("Adding to tier %d %s.", tier, name));
                        }
                    }
                } else if (forbiddenGroup) {
                    // not implemented
                } else if (requiredGroup) {
                    // not implemented
                } else if (forbiddirect) {
                    String[] fields = delimiter.split(line);
                    if (fields.length < 2) {
                        String errMsg = String.format("Line %d contains fewer than two elements.", lineNum);
                        throw new IllegalArgumentException(errMsg);
                    } else if (fields.length > 2) {
                        String errMsg = String.format("Line %d contains more than two elements.", lineNum);
                        throw new IllegalArgumentException(errMsg);
                    }

                    knowledge.setForbidden(fields[0], fields[1]);
                } else if (requireDirect) {
                    String[] fields = delimiter.split(line);
                    if (fields.length < 2) {
                        String errMsg = String.format("Line %d contains fewer than two elements.", lineNum);
                        throw new IllegalArgumentException(errMsg);
                    } else if (fields.length > 2) {
                        String errMsg = String.format("Line %d contains more than two elements.", lineNum);
                        throw new IllegalArgumentException(errMsg);
                    }

                    knowledge.removeForbidden(fields[0], fields[1]);
                    knowledge.setRequired(fields[0], fields[1]);
                } else {
                    String errMsg = String.format("Line %d: Expecting 'addtemporal', 'forbiddirect' or 'requiredirect'.", lineNum);
                    throw new IllegalArgumentException(errMsg);
                }
            }
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
        }

        return knowledge;
    }

}
