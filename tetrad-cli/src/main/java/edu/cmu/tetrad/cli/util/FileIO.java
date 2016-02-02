/*
 * Copyright (C) 2015 University of Pittsburgh.
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
package edu.cmu.tetrad.cli.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 *
 * Jan 29, 2016 5:45:07 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class FileIO {

    private FileIO() {
    }

    public static List<String> extractLineByLine(Path file) throws IOException {
        List<String> lines = new LinkedList<>();

        if (file != null) {
            try (BufferedReader reader = Files.newBufferedReader(file, Charset.defaultCharset())) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    lines.add(line);
                }
            }
        }

        return lines;
    }

    public static Set<String> extractUniqueLine(Path file) throws IOException {
        Set<String> lines = new HashSet<>();

        if (file != null) {
            try (BufferedReader reader = Files.newBufferedReader(file, Charset.defaultCharset())) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    lines.add(line);
                }
            }
        }

        return lines;
    }

}
