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
package edu.pitt.dbmi.data.reader.tabular;

import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.Delimiter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * Dec 9, 2018 2:07:46 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TabularColumnFileReaderTest {

    private final Delimiter delimiter = Delimiter.COMMA;
    private final char quoteCharacter = '"';
    private final String commentMarker = "//";

    private final Path[] dataFiles = {
        Paths.get(getClass().getResource("/data/tabular/mixed/dos_sim_test_data.csv").getFile()),
        Paths.get(getClass().getResource("/data/tabular/mixed/mac_sim_test_data.csv").getFile()),
        Paths.get(getClass().getResource("/data/tabular/mixed/sim_test_data.csv").getFile()),
        Paths.get(getClass().getResource("/data/tabular/mixed/quotes_sim_test_data.csv").getFile())
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
        for (Path dataFile : dataFiles) {
            TabularColumnReader fileReader = new TabularColumnFileReader(dataFile, delimiter);
            fileReader.setCommentMarker(commentMarker);
            fileReader.setQuoteCharacter(quoteCharacter);

            boolean isDiscrete = false;
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
        int[] columnsToExclude = new int[]{0, 1, 5, 3, 7, -1};
        for (Path dataFile : dataFiles) {
            TabularColumnReader fileReader = new TabularColumnFileReader(dataFile, delimiter);
            fileReader.setCommentMarker(commentMarker);
            fileReader.setQuoteCharacter(quoteCharacter);

            boolean isDiscrete = false;
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
        for (Path dataFile : dataFiles) {
            TabularColumnReader fileReader = new TabularColumnFileReader(dataFile, delimiter);
            fileReader.setCommentMarker(commentMarker);
            fileReader.setQuoteCharacter(quoteCharacter);

            boolean isDiscrete = true;
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
