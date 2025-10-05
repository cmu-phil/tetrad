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

package edu.pitt.dbmi.data.reader.preview;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
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
        Path dataFile = new File(getClass().getResource("/data/tabular/continuous/sim_test_data.csv").getFile()).toPath();

        DataPreviewer dataPreviewer = new BasicDataPreviewer(dataFile);

        final int fromLine = 3;
        final int toLine = 5;
        final int numOfCharacters = 25;

        List<String> linePreviews = dataPreviewer.getPreviews(fromLine, toLine, numOfCharacters);
        final long expected = 3;
        long actual = linePreviews.size();
        Assert.assertEquals(expected, actual);
    }

}

