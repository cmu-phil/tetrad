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

import edu.pitt.dbmi.data.reader.*;
import edu.pitt.dbmi.data.reader.metadata.Metadata;
import edu.pitt.dbmi.data.reader.metadata.MetadataFileReader;
import edu.pitt.dbmi.data.reader.metadata.MetadataReader;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Nov 15, 2018 5:22:50 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TabularDataFileReaderTest {

    private final Delimiter delimiter = Delimiter.COMMA;
    private final char quoteCharacter = '"';
    private final String missingValueMarker = "*";
    private final String commentMarker = "//";
    private final boolean hasHeader = true;

    private final Path[] continuousDataFiles = {
            new File(getClass().getResource("/data/tabular/continuous/dos_sim_test_data.csv").getFile()).toPath(),
            new File(getClass().getResource("/data/tabular/continuous/mac_sim_test_data.csv").getFile()).toPath(),
            new File(getClass().getResource("/data/tabular/continuous/sim_test_data.csv").getFile()).toPath(),
            new File(getClass().getResource("/data/tabular/continuous/quotes_sim_test_data.csv").getFile()).toPath()
    };

    private final Path[] discreteDataFiles = {
            new File(getClass().getResource("/data/tabular/discrete/dos_sim_test_data.csv").getFile()).toPath(),
            new File(getClass().getResource("/data/tabular/discrete/mac_sim_test_data.csv").getFile()).toPath(),
            new File(getClass().getResource("/data/tabular/discrete/sim_test_data.csv").getFile()).toPath(),
            new File(getClass().getResource("/data/tabular/discrete/quotes_sim_test_data.csv").getFile()).toPath()
    };

    private final Path[] mixedDataFiles = {
            new File(getClass().getResource("/data/tabular/mixed/dos_sim_test_data.csv").getFile()).toPath(),
            new File(getClass().getResource("/data/tabular/mixed/mac_sim_test_data.csv").getFile()).toPath(),
            new File(getClass().getResource("/data/tabular/mixed/sim_test_data.csv").getFile()).toPath(),
            new File(getClass().getResource("/data/tabular/mixed/quotes_sim_test_data.csv").getFile()).toPath()
    };

    public TabularDataFileReaderTest() {
    }

    @Test
    public void testReadInContinuousDataWitMetadata() throws IOException {
        Path dataFile = new File(getClass().getResource("/data/metadata/sim_continuous_intervention.txt").getFile()).toPath();
        Path metadataFile = new File(getClass().getResource("/data/metadata/sim_continuous_intervention_metadata.json").getFile()).toPath();

        TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, Delimiter.TAB);
        DataColumn[] dataColumns = columnReader.readInDataColumns(false);

        long expected = 10;
        long actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        MetadataReader metadataReader = new MetadataFileReader(metadataFile);
        Metadata metadata = metadataReader.read();
        dataColumns = DataColumns.update(dataColumns, metadata);

        expected = 10;
        actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        TabularDataReader dataReader = new TabularDataFileReader(dataFile, Delimiter.TAB);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteCharacter);
        dataReader.setMissingDataMarker(this.missingValueMarker);

        Data data = dataReader.read(dataColumns, this.hasHeader, metadata);
        Assert.assertTrue(data instanceof ContinuousData);

        ContinuousData continuousData = (ContinuousData) data;
        double[][] contData = continuousData.getData();

        expected = 18;
        actual = contData.length;
        Assert.assertEquals(expected, actual);

        expected = 10;
        actual = contData[0].length;
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testReadInDiscreteDataWitMetadata() throws IOException {
        Path dataFile = new File(getClass().getResource("/data/metadata/sim_discrete_intervention.txt").getFile()).toPath();
        Path metadataFile = new File(getClass().getResource("/data/metadata/sim_discrete_intervention_metadata.json").getFile()).toPath();

        TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, Delimiter.TAB);
        DataColumn[] dataColumns = columnReader.readInDataColumns(true);

        long expected = 10;
        long actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        MetadataReader metadataReader = new MetadataFileReader(metadataFile);
        Metadata metadata = metadataReader.read();
        dataColumns = DataColumns.update(dataColumns, metadata);

        expected = 12;
        actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        TabularDataReader dataReader = new TabularDataFileReader(dataFile, Delimiter.TAB);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteCharacter);
        dataReader.setMissingDataMarker(this.missingValueMarker);

        Data data = dataReader.read(dataColumns, this.hasHeader, metadata);
        Assert.assertTrue(data instanceof DiscreteData);

        DiscreteData verticalDiscreteData = (DiscreteData) data;
        int[][] discreteData = verticalDiscreteData.getData();

        expected = 12;
        actual = discreteData.length;
        Assert.assertEquals(expected, actual);

        expected = 19;
        actual = discreteData[0].length;
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testReadInSampleMixedDataWitMetadata() throws IOException {
        Path dataFile = new File(getClass().getResource("/data/metadata/sample_dataset.txt").getFile()).toPath();
        Path metadataFile = new File(getClass().getResource("/data/metadata/sample_metadata.json").getFile()).toPath();

        TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, Delimiter.TAB);
        DataColumn[] dataColumns = columnReader.readInDataColumns(true);

        long expected = 8;
        long actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        TabularDataReader dataReader = new TabularDataFileReader(dataFile, Delimiter.TAB);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteCharacter);
        dataReader.setMissingDataMarker(this.missingValueMarker);

        final int numberOfCategories = 4;
        dataReader.determineDiscreteDataColumns(dataColumns, numberOfCategories, this.hasHeader);

        MetadataReader metadataReader = new MetadataFileReader(metadataFile);
        Metadata metadata = metadataReader.read();
        dataColumns = DataColumns.update(dataColumns, metadata);

        expected = 9;
        actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        Data data = dataReader.read(dataColumns, this.hasHeader, metadata);
        Assert.assertTrue(data instanceof MixedTabularData);

        MixedTabularData mixedTabularData = (MixedTabularData) data;

        int numOfRows = mixedTabularData.getNumOfRows();
        int numOfCols = dataColumns.length;
        DiscreteDataColumn[] discreteDataColumns = mixedTabularData.getDataColumns();
        double[][] continuousData = mixedTabularData.getContinuousData();
        int[][] discreteData = mixedTabularData.getDiscreteData();

        expected = 5;
        actual = numOfRows;
        Assert.assertEquals(expected, actual);

        expected = 9;
        actual = discreteDataColumns.length;
        Assert.assertEquals(expected, actual);

        int numOfContinuous = 0;
        int numOfDiscrete = 0;
        for (int i = 0; i < numOfCols; i++) {
            if (continuousData[i] != null) {
                numOfContinuous++;
            }
            if (discreteData[i] != null) {
                numOfDiscrete++;
            }
        }

        expected = 6;
        actual = numOfContinuous;
        Assert.assertEquals(expected, actual);

        expected = 3;
        actual = numOfDiscrete;
        Assert.assertEquals(expected, actual);
    }

    /**
     * Test of readInData method, of class TabularDataFileReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInMixedDataWitMetadata() throws IOException {
        Path dataFile = new File(getClass().getResource("/data/metadata/sim_mixed_intervention.txt").getFile()).toPath();
        Path metadataFile = new File(getClass().getResource("/data/metadata/sim_mixed_intervention_metadata.json").getFile()).toPath();

        TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, Delimiter.TAB);
        DataColumn[] dataColumns = columnReader.readInDataColumns(true);

        long expected = 10;
        long actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        TabularDataReader dataReader = new TabularDataFileReader(dataFile, Delimiter.TAB);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteCharacter);
        dataReader.setMissingDataMarker(this.missingValueMarker);

        final int numberOfCategories = 4;
        dataReader.determineDiscreteDataColumns(dataColumns, numberOfCategories, this.hasHeader);

        MetadataReader metadataReader = new MetadataFileReader(metadataFile);
        Metadata metadata = metadataReader.read();
        dataColumns = DataColumns.update(dataColumns, metadata);

        expected = 11;
        actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        Data data = dataReader.read(dataColumns, this.hasHeader, metadata);
        Assert.assertTrue(data instanceof MixedTabularData);

        MixedTabularData mixedTabularData = (MixedTabularData) data;

        int numOfRows = mixedTabularData.getNumOfRows();
        int numOfCols = dataColumns.length;
        DiscreteDataColumn[] discreteDataColumns = mixedTabularData.getDataColumns();
        double[][] continuousData = mixedTabularData.getContinuousData();
        int[][] discreteData = mixedTabularData.getDiscreteData();

        expected = 20;
        actual = numOfRows;
        Assert.assertEquals(expected, actual);

        expected = 11;
        actual = discreteDataColumns.length;
        Assert.assertEquals(expected, actual);

        int numOfContinuous = 0;
        int numOfDiscrete = 0;
        for (int i = 0; i < numOfCols; i++) {
            if (continuousData[i] != null) {
                numOfContinuous++;
            }
            if (discreteData[i] != null) {
                numOfDiscrete++;
            }
        }

        expected = 5;
        actual = numOfContinuous;
        Assert.assertEquals(expected, actual);

        expected = 6;
        actual = numOfDiscrete;
        Assert.assertEquals(expected, actual);
    }

    /**
     * Test of readInData method, of class TabularDataFileReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataMixedExcludingVariableByColumnNumbers() throws IOException {
        int[] columnsToExclude = {8, 2, 4, 11, 9};
        for (Path dataFile : this.mixedDataFiles) {
            TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, this.delimiter);
            columnReader.setCommentMarker(this.commentMarker);
            columnReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = true;
            DataColumn[] dataColumns = columnReader.readInDataColumns(columnsToExclude, isDiscrete);

            long expected = 6;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            final int numberOfCategories = 4;
            dataReader.determineDiscreteDataColumns(dataColumns, numberOfCategories, this.hasHeader);

            Data data = dataReader.read(dataColumns, this.hasHeader);
            Assert.assertTrue(data instanceof MixedTabularData);

            MixedTabularData mixedTabularData = (MixedTabularData) data;

            int numOfRows = mixedTabularData.getNumOfRows();
            int numOfCols = dataColumns.length;
            DiscreteDataColumn[] discreteDataColumns = mixedTabularData.getDataColumns();
            double[][] continuousData = mixedTabularData.getContinuousData();
            int[][] discreteData = mixedTabularData.getDiscreteData();

            expected = 20;
            actual = numOfRows;
            Assert.assertEquals(expected, actual);

            expected = 6;
            actual = discreteDataColumns.length;
            Assert.assertEquals(expected, actual);

            int numOfContinuous = 0;
            int numOfDiscrete = 0;
            for (int i = 0; i < numOfCols; i++) {
                if (continuousData[i] != null) {
                    numOfContinuous++;
                }
                if (discreteData[i] != null) {
                    numOfDiscrete++;
                }
            }

            expected = 3;
            actual = numOfContinuous;
            Assert.assertEquals(expected, actual);

            expected = 3;
            actual = numOfDiscrete;
            Assert.assertEquals(expected, actual);
        }
    }

    /**
     * Test of readInData method, of class TabularDataFileReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataMixedExcludingVariableByNames() throws IOException {
        Set<String> namesOfColumnsToExclude = new HashSet<>(Arrays.asList("X10", "X11", "X3", "X4", "X1", "X6", "X8"));
        for (Path dataFile : this.mixedDataFiles) {
            TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, this.delimiter);
            columnReader.setCommentMarker(this.commentMarker);
            columnReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = true;
            DataColumn[] dataColumns = columnReader.readInDataColumns(namesOfColumnsToExclude, isDiscrete);

            long expected = 4;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            final int numberOfCategories = 4;
            dataReader.determineDiscreteDataColumns(dataColumns, numberOfCategories, this.hasHeader);

            Data data = dataReader.read(dataColumns, this.hasHeader);
            Assert.assertTrue(data instanceof MixedTabularData);

            MixedTabularData mixedTabularData = (MixedTabularData) data;

            int numOfRows = mixedTabularData.getNumOfRows();
            int numOfCols = dataColumns.length;
            DiscreteDataColumn[] discreteDataColumns = mixedTabularData.getDataColumns();
            double[][] continuousData = mixedTabularData.getContinuousData();
            int[][] discreteData = mixedTabularData.getDiscreteData();

            expected = 20;
            actual = numOfRows;
            Assert.assertEquals(expected, actual);

            expected = 4;
            actual = discreteDataColumns.length;
            Assert.assertEquals(expected, actual);

            int numOfContinuous = 0;
            int numOfDiscrete = 0;
            for (int i = 0; i < numOfCols; i++) {
                if (continuousData[i] != null) {
                    numOfContinuous++;
                }
                if (discreteData[i] != null) {
                    numOfDiscrete++;
                }
            }

            expected = 1;
            actual = numOfContinuous;
            Assert.assertEquals(expected, actual);

            expected = 3;
            actual = numOfDiscrete;
            Assert.assertEquals(expected, actual);
        }
    }

    /**
     * Test of readInData method, of class TabularDataFileReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataMixed() throws IOException {
        for (Path dataFile : this.mixedDataFiles) {
            TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, this.delimiter);
            columnReader.setCommentMarker(this.commentMarker);
            columnReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = true;
            DataColumn[] dataColumns = columnReader.readInDataColumns(isDiscrete);

            long expected = 10;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            final int numberOfCategories = 4;
            dataReader.determineDiscreteDataColumns(dataColumns, numberOfCategories, this.hasHeader);

            Data data = dataReader.read(dataColumns, this.hasHeader);
            Assert.assertTrue(data instanceof MixedTabularData);

            MixedTabularData mixedTabularData = (MixedTabularData) data;

            int numOfRows = mixedTabularData.getNumOfRows();
            int numOfCols = dataColumns.length;
            DiscreteDataColumn[] discreteDataColumns = mixedTabularData.getDataColumns();
            double[][] continuousData = mixedTabularData.getContinuousData();
            int[][] discreteData = mixedTabularData.getDiscreteData();

            expected = 20;
            actual = numOfRows;
            Assert.assertEquals(expected, actual);

            expected = 10;
            actual = discreteDataColumns.length;
            Assert.assertEquals(expected, actual);

            int numOfContinuous = 0;
            int numOfDiscrete = 0;
            for (int i = 0; i < numOfCols; i++) {
                if (continuousData[i] != null) {
                    numOfContinuous++;
                }
                if (discreteData[i] != null) {
                    numOfDiscrete++;
                }
            }

            expected = 5;
            actual = numOfContinuous;
            Assert.assertEquals(expected, actual);

            expected = 5;
            actual = numOfDiscrete;
            Assert.assertEquals(expected, actual);
        }
    }

    /**
     * Test of readInData method, of class TabularDataFileReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataDiscreteExcludingVariableByColumnNumbers() throws IOException {
        int[] columnsToExclude = {8, 2, 4, 11, 9};
        for (Path dataFile : this.discreteDataFiles) {
            TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, this.delimiter);
            columnReader.setCommentMarker(this.commentMarker);
            columnReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = true;
            DataColumn[] dataColumns = columnReader.readInDataColumns(columnsToExclude, isDiscrete);

            long expected = 6;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            Data data = dataReader.read(dataColumns, this.hasHeader);
            Assert.assertTrue(data instanceof DiscreteData);

            DiscreteData verticalDiscreteData = (DiscreteData) data;

            DiscreteDataColumn[] columns = verticalDiscreteData.getDataColumns();
            expected = 6;
            actual = columns.length;
            Assert.assertEquals(expected, actual);

            int[][] discreteData = verticalDiscreteData.getData();

            expected = 6;
            actual = discreteData.length;
            Assert.assertEquals(expected, actual);

            expected = 19;
            actual = discreteData[0].length;
            Assert.assertEquals(expected, actual);
        }
    }

    /**
     * Test of readInData method, of class TabularDataFileReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataDiscreteExcludingVariableByNames() throws IOException {
        Set<String> namesOfColumnsToExclude = new HashSet<>(Arrays.asList("X10", "X11", "X3", "X4", "X1", "X6", "X8"));
        for (Path dataFile : this.discreteDataFiles) {
            TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, this.delimiter);
            columnReader.setCommentMarker(this.commentMarker);
            columnReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = true;
            DataColumn[] dataColumns = columnReader.readInDataColumns(namesOfColumnsToExclude, isDiscrete);

            long expected = 4;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            Data data = dataReader.read(dataColumns, this.hasHeader);
            Assert.assertTrue(data instanceof DiscreteData);

            DiscreteData verticalDiscreteData = (DiscreteData) data;

            DiscreteDataColumn[] columns = verticalDiscreteData.getDataColumns();
            expected = 4;
            actual = columns.length;
            Assert.assertEquals(expected, actual);

            int[][] discreteData = verticalDiscreteData.getData();

            expected = 4;
            actual = discreteData.length;
            Assert.assertEquals(expected, actual);

            expected = 19;
            actual = discreteData[0].length;
            Assert.assertEquals(expected, actual);
        }
    }

    /**
     * Test of readInData method, of class TabularDataFileReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataDiscrete() throws IOException {
        for (Path dataFile : this.discreteDataFiles) {
            TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, this.delimiter);
            columnReader.setCommentMarker(this.commentMarker);
            columnReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = true;
            DataColumn[] dataColumns = columnReader.readInDataColumns(isDiscrete);

            long expected = 10;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            Data data = dataReader.read(dataColumns, this.hasHeader);
            Assert.assertTrue(data instanceof DiscreteData);

            DiscreteData verticalDiscreteData = (DiscreteData) data;

            DiscreteDataColumn[] columns = verticalDiscreteData.getDataColumns();
            expected = 10;
            actual = columns.length;
            Assert.assertEquals(expected, actual);

            int[][] discreteData = verticalDiscreteData.getData();

            expected = 10;
            actual = discreteData.length;
            Assert.assertEquals(expected, actual);

            expected = 19;
            actual = discreteData[0].length;
            Assert.assertEquals(expected, actual);
        }
    }

    /**
     * Test of readInData method, of class TabularDataFileReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataContinuousExcludingVariableByColumnNumbers() throws IOException {
        int[] columnsToExclude = {8, 2, 4, 11, 9};
        for (Path dataFile : this.continuousDataFiles) {
            TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, this.delimiter);
            columnReader.setCommentMarker(this.commentMarker);
            columnReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = false;
            DataColumn[] dataColumns = columnReader.readInDataColumns(columnsToExclude, isDiscrete);

            long expected = 6;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            Data data = dataReader.read(dataColumns, this.hasHeader);
            Assert.assertTrue(data instanceof ContinuousData);

            ContinuousData continuousData = (ContinuousData) data;
            double[][] contData = continuousData.getData();

            expected = 18;
            actual = contData.length;
            Assert.assertEquals(expected, actual);

            expected = 6;
            actual = contData[0].length;
            Assert.assertEquals(expected, actual);
        }
    }

    /**
     * Test of readInData method, of class TabularDataFileReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataContinuousExcludingVariableByNames() throws IOException {
        Set<String> namesOfColumnsToExclude = new HashSet<>(Arrays.asList("X10", "X11", "X3", "X4", "X1", "X6", "X8"));
        for (Path dataFile : this.continuousDataFiles) {
            TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, this.delimiter);
            columnReader.setCommentMarker(this.commentMarker);
            columnReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = false;
            DataColumn[] dataColumns = columnReader.readInDataColumns(namesOfColumnsToExclude, isDiscrete);

            long expected = 4;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            Data data = dataReader.read(dataColumns, this.hasHeader);
            Assert.assertTrue(data instanceof ContinuousData);

            ContinuousData continuousData = (ContinuousData) data;
            double[][] contData = continuousData.getData();

            expected = 18;
            actual = contData.length;
            Assert.assertEquals(expected, actual);

            expected = 4;
            actual = contData[0].length;
            Assert.assertEquals(expected, actual);
        }
    }

    /**
     * Test of readInData method, of class TabularDataFileReader.
     *
     * @throws IOException
     */
    @Test
    public void testReadInDataContinuous() throws IOException {
        for (Path dataFile : this.continuousDataFiles) {
            TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, this.delimiter);
            columnReader.setCommentMarker(this.commentMarker);
            columnReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = false;
            DataColumn[] dataColumns = columnReader.readInDataColumns(isDiscrete);

            long expected = 10;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            Data data = dataReader.read(dataColumns, this.hasHeader);
            Assert.assertTrue(data instanceof ContinuousData);

            ContinuousData continuousData = (ContinuousData) data;
            double[][] contData = continuousData.getData();

            expected = 18;
            actual = contData.length;
            Assert.assertEquals(expected, actual);

            expected = 10;
            actual = contData[0].length;
            Assert.assertEquals(expected, actual);
        }
    }

    /**
     * Test of determineDiscreteDataColumns method, of class TabularColumnFileReader.
     *
     * @throws IOException
     */
    @Test
    public void testDetermineDiscreteDataColumns() throws IOException {
        for (Path dataFile : this.mixedDataFiles) {
            TabularColumnReader fileReader = new TabularColumnFileReader(dataFile, this.delimiter);
            fileReader.setCommentMarker(this.commentMarker);
            fileReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = false;
            DataColumn[] dataColumns = fileReader.readInDataColumns(isDiscrete);

            TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            dataReader.determineDiscreteDataColumns(dataColumns, 4, this.hasHeader);
            long numOfDiscrete = Arrays.stream(dataColumns)
                    .filter(DataColumn::isDiscrete)
                    .count();

            final long expected = 5;
            Assert.assertEquals(expected, numOfDiscrete);
        }
    }

}

