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
 * Dec 18, 2018 2:39:25 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class MetadataFileWriter implements MetadataWriter {

    /**
     * Default constructor.
     */
    public MetadataFileWriter() {

    }

    /**
     * Writes the metadata as a string.
     *
     * @param metadata The metadata.
     * @return The metadata as a string.
     * @throws JsonProcessingException if an error occurs while processing the JSON.
     */
    @Override
    public String writeAsString(Metadata metadata) throws JsonProcessingException {
        return new ObjectMapper()
                .writer()
                .withDefaultPrettyPrinter()
                .writeValueAsString(metadata);
    }

    /**
     * Writes the metadata to the specified output file.
     *
     * @param metadata   The metadata to write.
     * @param outputFile The output file to write the metadata to.
     * @throws IOException If an error occurs while writing the metadata to the output file.
     */
    @Override
    public void write(Metadata metadata, Path outputFile) throws IOException {
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
