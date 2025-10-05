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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

/**
 * Dec 18, 2018 2:48:02 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class MetadataFileWriterTest {

    private final Path metadataFile = new File(getClass().getResource("/data/metadata/sim_mixed_intervention_metadata.json").getFile()).toPath();

    public MetadataFileWriterTest() {
    }

    /**
     * Test of writeAsString method, of class MetadataFileWriter.
     *
     * @throws JsonProcessingException
     */
    @Test
    public void testWriteAsString() throws IOException {
        List<ColumnMetadata> domainCols = new LinkedList<>();
        domainCols.add(new ColumnMetadata("X3", false));
        domainCols.add(new ColumnMetadata("X5", true));

        List<InterventionalColumn> intervCols = new LinkedList<>();
        intervCols.add(new InterventionalColumn(new ColumnMetadata("X9", true), new ColumnMetadata("X10", false)));
        intervCols.add(new InterventionalColumn(new ColumnMetadata("X8", false), null));

        Metadata metadata = new Metadata(domainCols, intervCols);

        String json = (new MetadataFileWriter()).writeAsString(metadata);
        Assert.assertFalse(json == null || json.isEmpty());
    }

}

