/*
 * Copyright (C) 2018 University of Pittsburgh.
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
package edu.pitt.dbmi.data.reader.metadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 *
 * Dec 18, 2018 2:39:25 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class MetadataFileWriter implements MetadataWriter {

    @Override
    public String writeAsString(Metadata metadata) throws JsonProcessingException {
        return new ObjectMapper()
                .writer()
                .withDefaultPrettyPrinter()
                .writeValueAsString(metadata);
    }

    @Override
    public void write(Metadata metadata, Path outputFile) throws JsonProcessingException, IOException {
        ObjectWriter writer = new ObjectMapper().writer().withDefaultPrettyPrinter();
        if (Files.exists(outputFile)) {
            if (Files.deleteIfExists(outputFile)) {
                Files.write(outputFile, writer.writeValueAsBytes(metadata), StandardOpenOption.CREATE);
            }
        } else {
            Files.write(outputFile, writer.writeValueAsBytes(metadata), StandardOpenOption.CREATE);
        }
    }

}
