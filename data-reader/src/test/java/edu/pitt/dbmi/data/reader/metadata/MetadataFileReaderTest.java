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

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * Dec 18, 2018 2:22:25 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class MetadataFileReaderTest {

    private final Path metadataFile = Paths.get(getClass().getResource("/data/metadata/sim_mixed_intervention_metadata.json").getFile());

    public MetadataFileReaderTest() {
    }

    /**
     * Test of read method, of class MetadataFileReader.
     *
     * @throws IOException
     */
    @Test
    public void testRead() throws IOException {
        MetadataReader metadataReader = new MetadataFileReader(metadataFile);
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
