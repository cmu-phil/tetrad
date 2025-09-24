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

package edu.pitt.dbmi.data.reader;

import edu.pitt.dbmi.data.reader.metadata.Metadata;
import edu.pitt.dbmi.data.reader.metadata.MetadataFileReader;
import edu.pitt.dbmi.data.reader.metadata.MetadataReader;
import edu.pitt.dbmi.data.reader.tabular.TabularColumnFileReader;
import edu.pitt.dbmi.data.reader.tabular.TabularColumnReader;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Jan 2, 2019 10:49:19 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class DataColumnsTest {

    private final Delimiter delimiter = Delimiter.TAB;
    Path dataFile = new File(getClass().getResource("/data/metadata/sim_mixed_intervention.txt").getFile()).toPath();
    Path metadataFile = new File(getClass().getResource("/data/metadata/sim_mixed_intervention_metadata.json").getFile()).toPath();

    public DataColumnsTest() {
    }

    /**
     * Test of update method, of class DataColumns.
     *
     * @throws IOException
     */
    @Test
    public void testUpdate() throws IOException {
        TabularColumnReader columnReader = new TabularColumnFileReader(this.dataFile, this.delimiter);
        DataColumn[] dataColumns = columnReader.readInDataColumns(true);

        long expected = 10;
        long actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        MetadataReader metadataReader = new MetadataFileReader(this.metadataFile);
        Metadata metadata = metadataReader.read();
        dataColumns = DataColumns.update(dataColumns, metadata);

        expected = 11;
        actual = dataColumns.length;
        Assert.assertEquals(expected, actual);
    }

}

