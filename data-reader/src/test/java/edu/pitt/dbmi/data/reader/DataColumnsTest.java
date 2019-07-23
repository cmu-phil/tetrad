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
package edu.pitt.dbmi.data.reader;

import edu.pitt.dbmi.data.reader.metadata.Metadata;
import edu.pitt.dbmi.data.reader.metadata.MetadataFileReader;
import edu.pitt.dbmi.data.reader.metadata.MetadataReader;
import edu.pitt.dbmi.data.reader.tabular.TabularColumnFileReader;
import edu.pitt.dbmi.data.reader.tabular.TabularColumnReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * Jan 2, 2019 10:49:19 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class DataColumnsTest {

    Path dataFile = Paths.get(getClass().getResource("/data/metadata/sim_mixed_intervention.txt").getFile());
    Path metadataFile = Paths.get(getClass().getResource("/data/metadata/sim_mixed_intervention_metadata.json").getFile());

    private final Delimiter delimiter = Delimiter.TAB;

    public DataColumnsTest() {
    }

    /**
     * Test of update method, of class DataColumns.
     *
     * @throws IOException
     */
    @Test
    public void testUpdate() throws IOException {
        TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, delimiter);
        DataColumn[] dataColumns = columnReader.readInDataColumns(true);

        long expected = 10;
        long actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        MetadataReader metadataReader = new MetadataFileReader(metadataFile);
        Metadata metadata = metadataReader.read();
        dataColumns = DataColumns.update(dataColumns, metadata);

        expected = 11;
        actual = dataColumns.length;
        Assert.assertEquals(expected, actual);
    }

}
