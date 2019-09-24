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
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * Dec 18, 2018 2:48:02 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class MetadataFileWriterTest {

    private final Path metadataFile = Paths.get(getClass().getResource("/data/metadata/sim_mixed_intervention_metadata.json").getFile());

    public MetadataFileWriterTest() {
    }

    /**
     * Test of writeAsString method, of class MetadataFileWriter.
     *
     * @throws JsonProcessingException
     */
    @Test
    public void testWriteAsString() throws JsonProcessingException, IOException {
        List<ColumnMetadata> domainCols = new LinkedList<>();
        domainCols.add(new ColumnMetadata("X3", false));
        domainCols.add(new ColumnMetadata("X5", true));

        List<InterventionalColumn> intervCols = new LinkedList<>();
        intervCols.add(new InterventionalColumn(new ColumnMetadata("X9", true), new ColumnMetadata("X10", false)));
        intervCols.add(new InterventionalColumn(new ColumnMetadata("X8", false), null));

        Metadata metadata = new Metadata(domainCols, intervCols);

        String json = (new MetadataFileWriter()).writeAsString(metadata);
        Assert.assertTrue(!(json == null || json.isEmpty()));
    }

}
