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
package edu.pitt.dbmi.data.reader.preview;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * Mar 6, 2017 8:41:16 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class BasicDataPreviewerTest {

    public BasicDataPreviewerTest() {
    }

    /**
     * Test of getPreviews method, of class BasicDataPreviewer.
     *
     * @throws IOException
     */
    @Test
    public void testGetPreviews() throws IOException {
        Path dataFile = Paths.get(getClass().getResource("/data/tabular/continuous/sim_test_data.csv").getFile());

        DataPreviewer dataPreviewer = new BasicDataPreviewer(dataFile);

        int fromLine = 3;
        int toLine = 5;
        int numOfCharacters = 25;

        List<String> linePreviews = dataPreviewer.getPreviews(fromLine, toLine, numOfCharacters);
        long expected = 3;
        long actual = linePreviews.size();
        Assert.assertEquals(expected, actual);
    }

}
