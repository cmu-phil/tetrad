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
package edu.pitt.dbmi.data.reader.tabular;

import edu.pitt.dbmi.data.reader.ContinuousData;
import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.Delimiter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * Jan 2, 2019 2:19:53 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class ContinuousTabularDatasetFileReaderTest {

    private final Delimiter delimiter = Delimiter.COMMA;
    private final char quoteCharacter = '"';
    private final String commentMarker = "//";
    private final String missingValueMarker = "*";
    private final boolean hasHeader = true;

    private final Path[] dataFiles = {
        Paths.get(getClass().getResource("/data/tabular/continuous/dos_sim_test_data.csv").getFile()),
        Paths.get(getClass().getResource("/data/tabular/continuous/mac_sim_test_data.csv").getFile()),
        Paths.get(getClass().getResource("/data/tabular/continuous/sim_test_data.csv").getFile()),
        Paths.get(getClass().getResource("/data/tabular/continuous/quotes_sim_test_data.csv").getFile())
    };

    public ContinuousTabularDatasetFileReaderTest() {
    }

    /**
     * Test of readInData method, of class ContinuousTabularDataReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataWithNoHeaderExcludingVariableByColumnNumbers() throws IOException {
        Path dataFile = Paths.get(getClass().getResource("/data/tabular/continuous/no_header_sim_test_data.csv").getFile());
        ContinuousTabularDatasetReader dataReader = new ContinuousTabularDatasetFileReader(dataFile, delimiter);
        dataReader.setCommentMarker(commentMarker);
        dataReader.setQuoteCharacter(quoteCharacter);
        dataReader.setMissingDataMarker(missingValueMarker);
        dataReader.setHasHeader(false);

        int[] columnsToExclude = {5, 3, 1, 8, 10, 11};
        Data data = dataReader.readInData(columnsToExclude);
        Assert.assertTrue(data instanceof ContinuousData);

        ContinuousData continuousData = (ContinuousData) data;
        DataColumn[] dataColumns = continuousData.getDataColumns();
        double[][] contData = continuousData.getData();

        long expected = 5;
        long actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        expected = 5;
        actual = contData[0].length;
        Assert.assertEquals(expected, actual);

        expected = 18;
        actual = contData.length;
        Assert.assertEquals(expected, actual);
    }

    /**
     * Test of readInData method, of class ContinuousTabularDataReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataWithNoHeader() throws IOException {
        Path dataFile = Paths.get(getClass().getResource("/data/tabular/continuous/no_header_sim_test_data.csv").getFile());
        ContinuousTabularDatasetReader dataReader = new ContinuousTabularDatasetFileReader(dataFile, delimiter);
        dataReader.setCommentMarker(commentMarker);
        dataReader.setQuoteCharacter(quoteCharacter);
        dataReader.setMissingDataMarker(missingValueMarker);
        dataReader.setHasHeader(false);

        Data data = dataReader.readInData();
        Assert.assertTrue(data instanceof ContinuousData);

        ContinuousData continuousData = (ContinuousData) data;
        DataColumn[] dataColumns = continuousData.getDataColumns();
        double[][] contData = continuousData.getData();

        long expected = 10;
        long actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        expected = 10;
        actual = contData[0].length;
        Assert.assertEquals(expected, actual);

        expected = 18;
        actual = contData.length;
        Assert.assertEquals(expected, actual);
    }

    /**
     * Test of readInData method, of class ContinuousTabularDataReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataExcludingVariableByColumnNumbers() throws IOException {
        int[] columnsToExclude = {5, 3, 1, 8, 10, 11};
        for (Path dataFile : dataFiles) {
            ContinuousTabularDatasetReader dataReader = new ContinuousTabularDatasetFileReader(dataFile, delimiter);
            dataReader.setCommentMarker(commentMarker);
            dataReader.setQuoteCharacter(quoteCharacter);
            dataReader.setMissingDataMarker(missingValueMarker);
            dataReader.setHasHeader(hasHeader);

            Data data = dataReader.readInData(columnsToExclude);
            Assert.assertTrue(data instanceof ContinuousData);

            ContinuousData continuousData = (ContinuousData) data;
            DataColumn[] dataColumns = continuousData.getDataColumns();
            double[][] contData = continuousData.getData();

            long expected = 5;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            expected = 5;
            actual = contData[0].length;
            Assert.assertEquals(expected, actual);

            expected = 18;
            actual = contData.length;
            Assert.assertEquals(expected, actual);
        }
    }

    /**
     * Test of readInData method, of class ContinuousTabularDataReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataExcludingVariableByNames() throws IOException {
        Set<String> namesOfColumnsToExclude = new HashSet<>(Arrays.asList("X1", "X3", "X4", "X6", "X8", "X10"));
        for (Path dataFile : dataFiles) {
            ContinuousTabularDatasetReader dataReader = new ContinuousTabularDatasetFileReader(dataFile, delimiter);
            dataReader.setCommentMarker(commentMarker);
            dataReader.setQuoteCharacter(quoteCharacter);
            dataReader.setMissingDataMarker(missingValueMarker);
            dataReader.setHasHeader(hasHeader);

            Data data = dataReader.readInData(namesOfColumnsToExclude);
            Assert.assertTrue(data instanceof ContinuousData);

            ContinuousData continuousData = (ContinuousData) data;
            DataColumn[] dataColumns = continuousData.getDataColumns();
            double[][] contData = continuousData.getData();

            long expected = 4;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            expected = 4;
            actual = contData[0].length;
            Assert.assertEquals(expected, actual);

            expected = 18;
            actual = contData.length;
            Assert.assertEquals(expected, actual);
        }
    }

    /**
     * Test of readInData method, of class ContinuousTabularDataReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInData() throws IOException {
        for (Path dataFile : dataFiles) {
            ContinuousTabularDatasetReader dataReader = new ContinuousTabularDatasetFileReader(dataFile, delimiter);
            dataReader.setCommentMarker(commentMarker);
            dataReader.setQuoteCharacter(quoteCharacter);
            dataReader.setMissingDataMarker(missingValueMarker);
            dataReader.setHasHeader(hasHeader);

            Data data = dataReader.readInData();
            Assert.assertTrue(data instanceof ContinuousData);

            ContinuousData continuousData = (ContinuousData) data;
            DataColumn[] dataColumns = continuousData.getDataColumns();
            double[][] contData = continuousData.getData();

            long expected = 10;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            expected = 10;
            actual = contData[0].length;
            Assert.assertEquals(expected, actual);

            expected = 18;
            actual = contData.length;
            Assert.assertEquals(expected, actual);
        }
    }

}
