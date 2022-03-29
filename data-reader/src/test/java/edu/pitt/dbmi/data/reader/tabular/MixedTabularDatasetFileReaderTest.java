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

import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.DiscreteDataColumn;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Jan 2, 2019 4:03:44 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class MixedTabularDatasetFileReaderTest {

    private final Delimiter delimiter = Delimiter.COMMA;
    private final char quoteCharacter = '"';
    private final String commentMarker = "//";
    private final String missingValueMarker = "*";
    private final boolean hasHeader = true;
    private final int numberOfDiscreteCategories = 4;

    private final Path[] dataFiles = {
            Paths.get(getClass().getResource("/data/tabular/mixed/dos_sim_test_data.csv").getFile()),
            Paths.get(getClass().getResource("/data/tabular/mixed/mac_sim_test_data.csv").getFile()),
            Paths.get(getClass().getResource("/data/tabular/mixed/sim_test_data.csv").getFile()),
            Paths.get(getClass().getResource("/data/tabular/mixed/quotes_sim_test_data.csv").getFile())
    };

    public MixedTabularDatasetFileReaderTest() {
    }

    /**
     * Test of readInData method, of class ContinuousTabularDataReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataWithNoHeader() throws IOException {
        final Path dataFile = Paths.get(getClass().getResource("/data/tabular/mixed/no_header_sim_test_data.csv").getFile());
        final MixedTabularDatasetReader dataReader = new MixedTabularDatasetFileReader(dataFile, this.delimiter, this.numberOfDiscreteCategories);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteCharacter);
        dataReader.setMissingDataMarker(this.missingValueMarker);
        dataReader.setHasHeader(false);

        final Data data = dataReader.readInData();
        Assert.assertTrue(data instanceof MixedTabularData);

        final MixedTabularData mixedTabularData = (MixedTabularData) data;

        final DiscreteDataColumn[] dataColumns = mixedTabularData.getDataColumns();
        final double[][] continuousData = mixedTabularData.getContinuousData();
        final int[][] discreteData = mixedTabularData.getDiscreteData();

        final int numOfRows = mixedTabularData.getNumOfRows();
        final int numOfCols = (continuousData.length > 0)
                ? continuousData.length
                : (discreteData.length > 0) ? discreteData.length : 0;

        long expected = 20;
        long actual = numOfRows;
        Assert.assertEquals(expected, actual);

        expected = 10;
        actual = numOfCols;
        Assert.assertEquals(expected, actual);

        expected = 10;
        actual = discreteData.length;
        Assert.assertEquals(expected, actual);

        expected = 10;
        actual = continuousData.length;
        Assert.assertEquals(expected, actual);

        expected = 10;
        actual = dataColumns.length;
        Assert.assertEquals(expected, actual);
    }

    /**
     * Test of readInData method, of class ContinuousTabularDataReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataWithNoHeaderExcludingVariableByColumnNumbers() throws IOException {
        final Path dataFile = Paths.get(getClass().getResource("/data/tabular/mixed/no_header_sim_test_data.csv").getFile());
        final MixedTabularDatasetReader dataReader = new MixedTabularDatasetFileReader(dataFile, this.delimiter, this.numberOfDiscreteCategories);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteCharacter);
        dataReader.setMissingDataMarker(this.missingValueMarker);
        dataReader.setHasHeader(false);

        final int[] excludedColumns = {5, 3, 8, 10, 11, 9};
        final Data data = dataReader.readInData(excludedColumns);
        Assert.assertTrue(data instanceof MixedTabularData);

        final MixedTabularData mixedTabularData = (MixedTabularData) data;

        final DiscreteDataColumn[] dataColumns = mixedTabularData.getDataColumns();
        final double[][] continuousData = mixedTabularData.getContinuousData();
        final int[][] discreteData = mixedTabularData.getDiscreteData();

        final int numOfRows = mixedTabularData.getNumOfRows();
        final int numOfCols = (continuousData.length > 0)
                ? continuousData.length
                : (discreteData.length > 0) ? discreteData.length : 0;

        long expected = 20;
        long actual = numOfRows;
        Assert.assertEquals(expected, actual);

        expected = 5;
        actual = numOfCols;
        Assert.assertEquals(expected, actual);

        expected = 5;
        actual = discreteData.length;
        Assert.assertEquals(expected, actual);

        expected = 5;
        actual = continuousData.length;
        Assert.assertEquals(expected, actual);

        expected = 5;
        actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        expected = 3;
        actual = Arrays.stream(dataColumns).filter(e -> e.getDataColumn().isDiscrete()).count();
        Assert.assertEquals(expected, actual);

        expected = 2;
        actual = Arrays.stream(dataColumns).filter(e -> !e.getDataColumn().isDiscrete()).count();
        Assert.assertEquals(expected, actual);
    }

    /**
     * Test of readInData method, of class ContinuousTabularDataReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataExcludingVariableByColumnNumbers() throws IOException {
        final int[] columnsToExclude = {5, 3, 1, 8, 10, 11};
        for (final Path dataFile : this.dataFiles) {
            final MixedTabularDatasetReader dataReader = new MixedTabularDatasetFileReader(dataFile, this.delimiter, this.numberOfDiscreteCategories);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);
            dataReader.setHasHeader(this.hasHeader);

            final Data data = dataReader.readInData(columnsToExclude);
            Assert.assertTrue(data instanceof MixedTabularData);

            final MixedTabularData mixedTabularData = (MixedTabularData) data;

            final DiscreteDataColumn[] dataColumns = mixedTabularData.getDataColumns();
            final double[][] continuousData = mixedTabularData.getContinuousData();
            final int[][] discreteData = mixedTabularData.getDiscreteData();

            final int numOfRows = mixedTabularData.getNumOfRows();
            final int numOfCols = (continuousData == null)
                    ? (discreteData == null) ? 0 : discreteData.length
                    : continuousData.length;

            long expected = 20;
            long actual = numOfRows;
            Assert.assertEquals(expected, actual);

            expected = 5;
            actual = numOfCols;
            Assert.assertEquals(expected, actual);

            expected = 5;
            actual = dataColumns.length;
            Assert.assertEquals(expected, actual);
        }
    }

    /**
     * Test of readInData method, of class ContinuousTabularDataReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataExcludingDiscreteVariableByNames() throws IOException {
        final Set<String> namesOfColumnsToExclude = new HashSet<>(Arrays.asList("X2", "X4", "X5", "X7", "X10"));
        for (final Path dataFile : this.dataFiles) {
            final MixedTabularDatasetReader dataReader = new MixedTabularDatasetFileReader(dataFile, this.delimiter, this.numberOfDiscreteCategories);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);
            dataReader.setHasHeader(this.hasHeader);

            final Data data = dataReader.readInData(namesOfColumnsToExclude);
            Assert.assertTrue(data instanceof MixedTabularData);

            final MixedTabularData mixedTabularData = (MixedTabularData) data;

            final DiscreteDataColumn[] dataColumns = mixedTabularData.getDataColumns();
            final double[][] continuousData = mixedTabularData.getContinuousData();
            final int[][] discreteData = mixedTabularData.getDiscreteData();

            final int numOfRows = mixedTabularData.getNumOfRows();
            final int numOfCols = (continuousData.length > 0)
                    ? continuousData.length
                    : (discreteData.length > 0) ? discreteData.length : 0;

            long expected = 20;
            long actual = numOfRows;
            Assert.assertEquals(expected, actual);

            expected = 5;
            actual = numOfCols;
            Assert.assertEquals(expected, actual);

            expected = 5;
            actual = continuousData.length;
            Assert.assertEquals(expected, actual);

            expected = 0;
            actual = discreteData.length;
            Assert.assertEquals(expected, actual);

            expected = 5;
            actual = dataColumns.length;
            Assert.assertEquals(expected, actual);
        }
    }

    /**
     * Test of readInData method, of class ContinuousTabularDataReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataExcludingContinuousVariableByNames() throws IOException {
        final Set<String> namesOfColumnsToExclude = new HashSet<>(Arrays.asList("X1", "X3", "X6", "X8", "X9"));
        for (final Path dataFile : this.dataFiles) {
            final MixedTabularDatasetReader dataReader = new MixedTabularDatasetFileReader(dataFile, this.delimiter, this.numberOfDiscreteCategories);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);
            dataReader.setHasHeader(this.hasHeader);

            final Data data = dataReader.readInData(namesOfColumnsToExclude);
            Assert.assertTrue(data instanceof MixedTabularData);

            final MixedTabularData mixedTabularData = (MixedTabularData) data;

            final DiscreteDataColumn[] dataColumns = mixedTabularData.getDataColumns();
            final double[][] continuousData = mixedTabularData.getContinuousData();
            final int[][] discreteData = mixedTabularData.getDiscreteData();

            final int numOfRows = mixedTabularData.getNumOfRows();
            final int numOfCols = (continuousData.length > 0)
                    ? continuousData.length
                    : (discreteData.length > 0) ? discreteData.length : 0;

            long expected = 20;
            long actual = numOfRows;
            Assert.assertEquals(expected, actual);

            expected = 5;
            actual = numOfCols;
            Assert.assertEquals(expected, actual);

            expected = 5;
            actual = discreteData.length;
            Assert.assertEquals(expected, actual);

            expected = 0;
            actual = continuousData.length;
            Assert.assertEquals(expected, actual);

            expected = 5;
            actual = dataColumns.length;
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
        final Set<String> namesOfColumnsToExclude = new HashSet<>(Arrays.asList("X1", "X3", "X4", "X6", "X8", "X10"));
        for (final Path dataFile : this.dataFiles) {
            final MixedTabularDatasetReader dataReader = new MixedTabularDatasetFileReader(dataFile, this.delimiter, this.numberOfDiscreteCategories);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);
            dataReader.setHasHeader(this.hasHeader);

            final Data data = dataReader.readInData(namesOfColumnsToExclude);
            Assert.assertTrue(data instanceof MixedTabularData);

            final MixedTabularData mixedTabularData = (MixedTabularData) data;

            final DiscreteDataColumn[] dataColumns = mixedTabularData.getDataColumns();
            final double[][] continuousData = mixedTabularData.getContinuousData();
            final int[][] discreteData = mixedTabularData.getDiscreteData();

            final int numOfRows = mixedTabularData.getNumOfRows();
            final int numOfCols = (continuousData == null)
                    ? (discreteData == null) ? 0 : discreteData.length
                    : continuousData.length;

            long expected = 20;
            long actual = numOfRows;
            Assert.assertEquals(expected, actual);

            expected = 4;
            actual = numOfCols;
            Assert.assertEquals(expected, actual);

            expected = 4;
            actual = dataColumns.length;
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
        for (final Path dataFile : this.dataFiles) {
            final MixedTabularDatasetReader dataReader = new MixedTabularDatasetFileReader(dataFile, this.delimiter, this.numberOfDiscreteCategories);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);
            dataReader.setHasHeader(this.hasHeader);

            final Data data = dataReader.readInData();
            Assert.assertTrue(data instanceof MixedTabularData);

            final MixedTabularData mixedTabularData = (MixedTabularData) data;

            final DiscreteDataColumn[] dataColumns = mixedTabularData.getDataColumns();
            final double[][] continuousData = mixedTabularData.getContinuousData();
            final int[][] discreteData = mixedTabularData.getDiscreteData();

            final int numOfRows = mixedTabularData.getNumOfRows();
            final int numOfCols = (continuousData == null)
                    ? (discreteData == null) ? 0 : discreteData.length
                    : continuousData.length;

            long expected = 20;
            long actual = numOfRows;
            Assert.assertEquals(expected, actual);

            expected = 10;
            actual = numOfCols;
            Assert.assertEquals(expected, actual);

            expected = 10;
            actual = dataColumns.length;
            Assert.assertEquals(expected, actual);
        }
    }

}
