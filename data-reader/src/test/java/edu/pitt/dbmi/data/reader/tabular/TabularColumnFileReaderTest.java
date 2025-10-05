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

package edu.pitt.dbmi.data.reader.tabular;

import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.Delimiter;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Dec 9, 2018 2:07:46 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TabularColumnFileReaderTest {

    private final Delimiter delimiter = Delimiter.COMMA;
    private final char quoteCharacter = '"';
    private final String commentMarker = "//";

    private final Path[] dataFiles = {
            new File(getClass().getResource("/data/tabular/mixed/dos_sim_test_data.csv").getFile()).toPath(),
            new File(getClass().getResource("/data/tabular/mixed/mac_sim_test_data.csv").getFile()).toPath(),
            new File(getClass().getResource("/data/tabular/mixed/sim_test_data.csv").getFile()).toPath(),
            new File(getClass().getResource("/data/tabular/mixed/quotes_sim_test_data.csv").getFile()).toPath()
    };

    public TabularColumnFileReaderTest() {
    }

    /**
     * Test of readInDataColumns method, of class TabularColumnFileReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataColumns() throws IOException {
    }

    /**
     * Test of readInDataColumns method, of class TabularColumnFileReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataColumnsWithSetOfColumnsToExclude() throws IOException {
        Set<String> columnNames = new HashSet<>(Arrays.asList("X1", "\"X3\"", "X5", " ", "X7", "X9", "", "X10", "X11"));
        for (Path dataFile : this.dataFiles) {
            TabularColumnReader fileReader = new TabularColumnFileReader(dataFile, this.delimiter);
            fileReader.setCommentMarker(this.commentMarker);
            fileReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = false;
            DataColumn[] dataColumns = fileReader.readInDataColumns(Collections.EMPTY_SET, isDiscrete);

            long expected = 10;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            dataColumns = fileReader.readInDataColumns(columnNames, isDiscrete);

            expected = 4;
            actual = dataColumns.length;
            Assert.assertEquals(expected, actual);
        }
    }

    /**
     * Test of readInDataColumns method, of class TabularColumnFileReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataColumnsWithColumnsToExclude() throws IOException {
        int[] columnsToExclude = {0, 1, 5, 3, 7, -1};
        for (Path dataFile : this.dataFiles) {
            TabularColumnReader fileReader = new TabularColumnFileReader(dataFile, this.delimiter);
            fileReader.setCommentMarker(this.commentMarker);
            fileReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = false;
            DataColumn[] dataColumns = fileReader.readInDataColumns(new int[0], isDiscrete);

            long expected = 10;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            dataColumns = fileReader.readInDataColumns(columnsToExclude, isDiscrete);

            expected = 6;
            actual = dataColumns.length;
            Assert.assertEquals(expected, actual);
        }
    }

    /**
     * Test of generateColumns method, of class TabularColumnFileReader.
     *
     * @throws IOException
     */
    @Test
    public void testGenerateColumns() throws IOException {
        for (Path dataFile : this.dataFiles) {
            TabularColumnReader fileReader = new TabularColumnFileReader(dataFile, this.delimiter);
            fileReader.setCommentMarker(this.commentMarker);
            fileReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = true;
            DataColumn[] dataColumns = fileReader.generateColumns(new int[0], isDiscrete);
            long expected = 10;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            dataColumns = fileReader.generateColumns(new int[]{5, 1, 11, 9}, isDiscrete);

            expected = 7;
            actual = dataColumns.length;
            Assert.assertEquals(expected, actual);
        }
    }

}

