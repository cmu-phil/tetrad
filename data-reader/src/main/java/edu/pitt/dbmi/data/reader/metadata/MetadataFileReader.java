///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

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
 * @version $Id: $Id
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
     * {@inheritDoc}
     * <p>
     * Reads in the metadata.
     */
    @Override
    public Metadata read() throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(this.metadataFile)) {
            return (new ObjectMapper()).readValue(reader, Metadata.class);
        }
    }

}

