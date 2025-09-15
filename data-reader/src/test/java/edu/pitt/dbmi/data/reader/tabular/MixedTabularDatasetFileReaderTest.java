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

import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.DiscreteDataColumn;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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
            new File(getClass().getResource("/data/tabular/mixed/dos_sim_test_data.csv").getFile()).toPath(),
            new File(getClass().getResource("/data/tabular/mixed/mac_sim_test_data.csv").getFile()).toPath(),
            new File(getClass().getResource("/data/tabular/mixed/sim_test_data.csv").getFile()).toPath(),
            new File(getClass().getResource("/data/tabular/mixed/quotes_sim_test_data.csv").getFile()).toPath()
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
        Path dataFile = new File(getClass().getResource("/data/tabular/mixed/no_header_sim_test_data.csv").getFile()).toPath();
        MixedTabularDatasetReader dataReader = new MixedTabularDatasetFileReader(dataFile, this.delimiter, this.numberOfDiscreteCategories);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteCharacter);
        dataReader.setMissingDataMarker(this.missingValueMarker);
        dataReader.setHasHeader(false);

        Data data = dataReader.readInData();
        Assert.assertTrue(data instanceof MixedTabularData);

        MixedTabularData mixedTabularData = (MixedTabularData) data;

        DiscreteDataColumn[] dataColumns = mixedTabularData.getDataColumns();
        double[][] continuousData = mixedTabularData.getContinuousData();
        int[][] discreteData = mixedTabularData.getDiscreteData();

        int numOfRows = mixedTabularData.getNumOfRows();
        int numOfCols = (continuousData.length > 0)
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
        Path dataFile = new File(getClass().getResource("/data/tabular/mixed/no_header_sim_test_data.csv").getFile()).toPath();
        MixedTabularDatasetReader dataReader = new MixedTabularDatasetFileReader(dataFile, this.delimiter, this.numberOfDiscreteCategories);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteCharacter);
        dataReader.setMissingDataMarker(this.missingValueMarker);
        dataReader.setHasHeader(false);

        int[] excludedColumns = {5, 3, 8, 10, 11, 9};
        Data data = dataReader.readInData(excludedColumns);
        Assert.assertTrue(data instanceof MixedTabularData);

        MixedTabularData mixedTabularData = (MixedTabularData) data;

        DiscreteDataColumn[] dataColumns = mixedTabularData.getDataColumns();
        double[][] continuousData = mixedTabularData.getContinuousData();
        int[][] discreteData = mixedTabularData.getDiscreteData();

        int numOfRows = mixedTabularData.getNumOfRows();
        int numOfCols = (continuousData.length > 0)
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
        int[] columnsToExclude = {5, 3, 1, 8, 10, 11};
        for (Path dataFile : this.dataFiles) {
            MixedTabularDatasetReader dataReader = new MixedTabularDatasetFileReader(dataFile, this.delimiter, this.numberOfDiscreteCategories);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);
            dataReader.setHasHeader(this.hasHeader);

            Data data = dataReader.readInData(columnsToExclude);
            Assert.assertTrue(data instanceof MixedTabularData);

            MixedTabularData mixedTabularData = (MixedTabularData) data;

            DiscreteDataColumn[] dataColumns = mixedTabularData.getDataColumns();
            double[][] continuousData = mixedTabularData.getContinuousData();
            int[][] discreteData = mixedTabularData.getDiscreteData();

            int numOfRows = mixedTabularData.getNumOfRows();
            int numOfCols = (continuousData == null)
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
        Set<String> namesOfColumnsToExclude = new HashSet<>(Arrays.asList("X2", "X4", "X5", "X7", "X10"));
        for (Path dataFile : this.dataFiles) {
            MixedTabularDatasetReader dataReader = new MixedTabularDatasetFileReader(dataFile, this.delimiter, this.numberOfDiscreteCategories);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);
            dataReader.setHasHeader(this.hasHeader);

            Data data = dataReader.readInData(namesOfColumnsToExclude);
            Assert.assertTrue(data instanceof MixedTabularData);

            MixedTabularData mixedTabularData = (MixedTabularData) data;

            DiscreteDataColumn[] dataColumns = mixedTabularData.getDataColumns();
            double[][] continuousData = mixedTabularData.getContinuousData();
            int[][] discreteData = mixedTabularData.getDiscreteData();

            int numOfRows = mixedTabularData.getNumOfRows();
            int numOfCols = (continuousData.length > 0)
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
        Set<String> namesOfColumnsToExclude = new HashSet<>(Arrays.asList("X1", "X3", "X6", "X8", "X9"));
        for (Path dataFile : this.dataFiles) {
            MixedTabularDatasetReader dataReader = new MixedTabularDatasetFileReader(dataFile, this.delimiter, this.numberOfDiscreteCategories);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);
            dataReader.setHasHeader(this.hasHeader);

            Data data = dataReader.readInData(namesOfColumnsToExclude);
            Assert.assertTrue(data instanceof MixedTabularData);

            MixedTabularData mixedTabularData = (MixedTabularData) data;

            DiscreteDataColumn[] dataColumns = mixedTabularData.getDataColumns();
            double[][] continuousData = mixedTabularData.getContinuousData();
            int[][] discreteData = mixedTabularData.getDiscreteData();

            int numOfRows = mixedTabularData.getNumOfRows();
            int numOfCols = (continuousData.length > 0)
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
        Set<String> namesOfColumnsToExclude = new HashSet<>(Arrays.asList("X1", "X3", "X4", "X6", "X8", "X10"));
        for (Path dataFile : this.dataFiles) {
            MixedTabularDatasetReader dataReader = new MixedTabularDatasetFileReader(dataFile, this.delimiter, this.numberOfDiscreteCategories);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);
            dataReader.setHasHeader(this.hasHeader);

            Data data = dataReader.readInData(namesOfColumnsToExclude);
            Assert.assertTrue(data instanceof MixedTabularData);

            MixedTabularData mixedTabularData = (MixedTabularData) data;

            DiscreteDataColumn[] dataColumns = mixedTabularData.getDataColumns();
            double[][] continuousData = mixedTabularData.getContinuousData();
            int[][] discreteData = mixedTabularData.getDiscreteData();

            int numOfRows = mixedTabularData.getNumOfRows();
            int numOfCols = (continuousData == null)
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
        for (Path dataFile : this.dataFiles) {
            MixedTabularDatasetReader dataReader = new MixedTabularDatasetFileReader(dataFile, this.delimiter, this.numberOfDiscreteCategories);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);
            dataReader.setHasHeader(this.hasHeader);

            Data data = dataReader.readInData();
            Assert.assertTrue(data instanceof MixedTabularData);

            MixedTabularData mixedTabularData = (MixedTabularData) data;

            DiscreteDataColumn[] dataColumns = mixedTabularData.getDataColumns();
            double[][] continuousData = mixedTabularData.getContinuousData();
            int[][] discreteData = mixedTabularData.getDiscreteData();

            int numOfRows = mixedTabularData.getNumOfRows();
            int numOfCols = (continuousData == null)
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

