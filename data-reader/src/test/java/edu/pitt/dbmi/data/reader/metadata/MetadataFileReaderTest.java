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

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Dec 18, 2018 2:22:25 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class MetadataFileReaderTest {

    private final Path metadataFile = new File(getClass().getResource("/data/metadata/sim_mixed_intervention_metadata.json").getFile()).toPath();

    public MetadataFileReaderTest() {
    }

    /**
     * Test of read method, of class MetadataFileReader.
     *
     * @throws IOException
     */
    @Test
    public void testRead() throws IOException {
        MetadataReader metadataReader = new MetadataFileReader(this.metadataFile);
        Metadata metadata = metadataReader.read();

        List<ColumnMetadata> domainCols = metadata.getDomainColumnns();
        List<InterventionalColumn> intervCols = metadata.getInterventionalColumns();

        long expected = 2;
        long actual = domainCols.size();
        Assert.assertEquals(expected, actual);

        expected = 2;
        actual = intervCols.size();
        Assert.assertEquals(expected, actual);
    }

}

