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

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dec 18, 2018 2:05:46 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class MetadataFileReader implements MetadataReader {

    /**
     * The metadata file.
     */
    protected final Path metadataFile;

    /**
     * Constructor.
     *
     * @param metadataFile The metadata file.
     */
    public MetadataFileReader(Path metadataFile) {
        this.metadataFile = metadataFile;
    }

    /**
     * Reads in the metadata.
     *
     * @return the metadata.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public Metadata read() throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(this.metadataFile)) {
            return (new ObjectMapper()).readValue(reader, Metadata.class);
        }
    }

}
