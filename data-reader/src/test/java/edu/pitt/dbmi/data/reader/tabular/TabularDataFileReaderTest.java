/*
 * Copyright (C) 2018 kvb2.
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

import edu.pitt.dbmi.data.reader.*;
import edu.pitt.dbmi.data.reader.metadata.Metadata;
import edu.pitt.dbmi.data.reader.metadata.MetadataFileReader;
import edu.pitt.dbmi.data.reader.metadata.MetadataReader;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            Paths.get(getClass().getResource("/data/tabular/continuous/dos_sim_test_data.csv").getFile()),
            Paths.get(getClass().getResource("/data/tabular/continuous/mac_sim_test_data.csv").getFile()),
            Paths.get(getClass().getResource("/data/tabular/continuous/sim_test_data.csv").getFile()),
            Paths.get(getClass().getResource("/data/tabular/continuous/quotes_sim_test_data.csv").getFile())
    };

    private final Path[] discreteDataFiles = {
            Paths.get(getClass().getResource("/data/tabular/discrete/dos_sim_test_data.csv").getFile()),
            Paths.get(getClass().getResource("/data/tabular/discrete/mac_sim_test_data.csv").getFile()),
            Paths.get(getClass().getResource("/data/tabular/discrete/sim_test_data.csv").getFile()),
            Paths.get(getClass().getResource("/data/tabular/discrete/quotes_sim_test_data.csv").getFile())
    };

    private final Path[] mixedDataFiles = {
            Paths.get(getClass().getResource("/data/tabular/mixed/dos_sim_test_data.csv").getFile()),
            Paths.get(getClass().getResource("/data/tabular/mixed/mac_sim_test_data.csv").getFile()),
            Paths.get(getClass().getResource("/data/tabular/mixed/sim_test_data.csv").getFile()),
            Paths.get(getClass().getResource("/data/tabular/mixed/quotes_sim_test_data.csv").getFile())
    };

    public TabularDataFileReaderTest() {
    }

    @Test
    public void testReadInContinuousDataWitMetadata() throws IOException {
        final Path dataFile = Paths.get(getClass().getResource("/data/metadata/sim_continuous_intervention.txt").getFile());
        final Path metadataFile = Paths.get(getClass().getResource("/data/metadata/sim_continuous_intervention_metadata.json").getFile());

        final TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, Delimiter.TAB);
        DataColumn[] dataColumns = columnReader.readInDataColumns(false);

        long expected = 10;
        long actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        final MetadataReader metadataReader = new MetadataFileReader(metadataFile);
        final Metadata metadata = metadataReader.read();
        dataColumns = DataColumns.update(dataColumns, metadata);

        expected = 10;
        actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        final TabularDataReader dataReader = new TabularDataFileReader(dataFile, Delimiter.TAB);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteCharacter);
        dataReader.setMissingDataMarker(this.missingValueMarker);

        final Data data = dataReader.read(dataColumns, this.hasHeader, metadata);
        Assert.assertTrue(data instanceof ContinuousData);

        final ContinuousData continuousData = (ContinuousData) data;
        final double[][] contData = continuousData.getData();

        expected = 18;
        actual = contData.length;
        Assert.assertEquals(expected, actual);

        expected = 10;
        actual = contData[0].length;
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testReadInDiscreteDataWitMetadata() throws IOException {
        final Path dataFile = Paths.get(getClass().getResource("/data/metadata/sim_discrete_intervention.txt").getFile());
        final Path metadataFile = Paths.get(getClass().getResource("/data/metadata/sim_discrete_intervention_metadata.json").getFile());

        final TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, Delimiter.TAB);
        DataColumn[] dataColumns = columnReader.readInDataColumns(true);

        long expected = 10;
        long actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        final MetadataReader metadataReader = new MetadataFileReader(metadataFile);
        final Metadata metadata = metadataReader.read();
        dataColumns = DataColumns.update(dataColumns, metadata);

        expected = 12;
        actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        final TabularDataReader dataReader = new TabularDataFileReader(dataFile, Delimiter.TAB);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteCharacter);
        dataReader.setMissingDataMarker(this.missingValueMarker);

        final Data data = dataReader.read(dataColumns, this.hasHeader, metadata);
        Assert.assertTrue(data instanceof DiscreteData);

        final DiscreteData verticalDiscreteData = (DiscreteData) data;
        final int[][] discreteData = verticalDiscreteData.getData();

        expected = 12;
        actual = discreteData.length;
        Assert.assertEquals(expected, actual);

        expected = 19;
        actual = discreteData[0].length;
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testReadInSampleMixedDataWitMetadata() throws IOException {
        final Path dataFile = Paths.get(getClass().getResource("/data/metadata/sample_dataset.txt").getFile());
        final Path metadataFile = Paths.get(getClass().getResource("/data/metadata/sample_metadata.json").getFile());

        final TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, Delimiter.TAB);
        DataColumn[] dataColumns = columnReader.readInDataColumns(true);

        long expected = 8;
        long actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        final TabularDataReader dataReader = new TabularDataFileReader(dataFile, Delimiter.TAB);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteCharacter);
        dataReader.setMissingDataMarker(this.missingValueMarker);

        final int numberOfCategories = 4;
        dataReader.determineDiscreteDataColumns(dataColumns, numberOfCategories, this.hasHeader);

        final MetadataReader metadataReader = new MetadataFileReader(metadataFile);
        final Metadata metadata = metadataReader.read();
        dataColumns = DataColumns.update(dataColumns, metadata);

        expected = 9;
        actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        final Data data = dataReader.read(dataColumns, this.hasHeader, metadata);
        Assert.assertTrue(data instanceof MixedTabularData);

        final MixedTabularData mixedTabularData = (MixedTabularData) data;

        final int numOfRows = mixedTabularData.getNumOfRows();
        final int numOfCols = dataColumns.length;
        final DiscreteDataColumn[] discreteDataColumns = mixedTabularData.getDataColumns();
        final double[][] continuousData = mixedTabularData.getContinuousData();
        final int[][] discreteData = mixedTabularData.getDiscreteData();

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
        final Path dataFile = Paths.get(getClass().getResource("/data/metadata/sim_mixed_intervention.txt").getFile());
        final Path metadataFile = Paths.get(getClass().getResource("/data/metadata/sim_mixed_intervention_metadata.json").getFile());

        final TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, Delimiter.TAB);
        DataColumn[] dataColumns = columnReader.readInDataColumns(true);

        long expected = 10;
        long actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        final TabularDataReader dataReader = new TabularDataFileReader(dataFile, Delimiter.TAB);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteCharacter);
        dataReader.setMissingDataMarker(this.missingValueMarker);

        final int numberOfCategories = 4;
        dataReader.determineDiscreteDataColumns(dataColumns, numberOfCategories, this.hasHeader);

        final MetadataReader metadataReader = new MetadataFileReader(metadataFile);
        final Metadata metadata = metadataReader.read();
        dataColumns = DataColumns.update(dataColumns, metadata);

        expected = 11;
        actual = dataColumns.length;
        Assert.assertEquals(expected, actual);

        final Data data = dataReader.read(dataColumns, this.hasHeader, metadata);
        Assert.assertTrue(data instanceof MixedTabularData);

        final MixedTabularData mixedTabularData = (MixedTabularData) data;

        final int numOfRows = mixedTabularData.getNumOfRows();
        final int numOfCols = dataColumns.length;
        final DiscreteDataColumn[] discreteDataColumns = mixedTabularData.getDataColumns();
        final double[][] continuousData = mixedTabularData.getContinuousData();
        final int[][] discreteData = mixedTabularData.getDiscreteData();

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
        final int[] columnsToExclude = {8, 2, 4, 11, 9};
        for (final Path dataFile : this.mixedDataFiles) {
            final TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, this.delimiter);
            columnReader.setCommentMarker(this.commentMarker);
            columnReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = true;
            final DataColumn[] dataColumns = columnReader.readInDataColumns(columnsToExclude, isDiscrete);

            long expected = 6;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            final TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            final int numberOfCategories = 4;
            dataReader.determineDiscreteDataColumns(dataColumns, numberOfCategories, this.hasHeader);

            final Data data = dataReader.read(dataColumns, this.hasHeader);
            Assert.assertTrue(data instanceof MixedTabularData);

            final MixedTabularData mixedTabularData = (MixedTabularData) data;

            final int numOfRows = mixedTabularData.getNumOfRows();
            final int numOfCols = dataColumns.length;
            final DiscreteDataColumn[] discreteDataColumns = mixedTabularData.getDataColumns();
            final double[][] continuousData = mixedTabularData.getContinuousData();
            final int[][] discreteData = mixedTabularData.getDiscreteData();

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
        final Set<String> namesOfColumnsToExclude = new HashSet<>(Arrays.asList("X10", "X11", "X3", "X4", "X1", "X6", "X8"));
        for (final Path dataFile : this.mixedDataFiles) {
            final TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, this.delimiter);
            columnReader.setCommentMarker(this.commentMarker);
            columnReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = true;
            final DataColumn[] dataColumns = columnReader.readInDataColumns(namesOfColumnsToExclude, isDiscrete);

            long expected = 4;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            final TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            final int numberOfCategories = 4;
            dataReader.determineDiscreteDataColumns(dataColumns, numberOfCategories, this.hasHeader);

            final Data data = dataReader.read(dataColumns, this.hasHeader);
            Assert.assertTrue(data instanceof MixedTabularData);

            final MixedTabularData mixedTabularData = (MixedTabularData) data;

            final int numOfRows = mixedTabularData.getNumOfRows();
            final int numOfCols = dataColumns.length;
            final DiscreteDataColumn[] discreteDataColumns = mixedTabularData.getDataColumns();
            final double[][] continuousData = mixedTabularData.getContinuousData();
            final int[][] discreteData = mixedTabularData.getDiscreteData();

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
        for (final Path dataFile : this.mixedDataFiles) {
            final TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, this.delimiter);
            columnReader.setCommentMarker(this.commentMarker);
            columnReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = true;
            final DataColumn[] dataColumns = columnReader.readInDataColumns(isDiscrete);

            long expected = 10;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            final TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            final int numberOfCategories = 4;
            dataReader.determineDiscreteDataColumns(dataColumns, numberOfCategories, this.hasHeader);

            final Data data = dataReader.read(dataColumns, this.hasHeader);
            Assert.assertTrue(data instanceof MixedTabularData);

            final MixedTabularData mixedTabularData = (MixedTabularData) data;

            final int numOfRows = mixedTabularData.getNumOfRows();
            final int numOfCols = dataColumns.length;
            final DiscreteDataColumn[] discreteDataColumns = mixedTabularData.getDataColumns();
            final double[][] continuousData = mixedTabularData.getContinuousData();
            final int[][] discreteData = mixedTabularData.getDiscreteData();

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
        final int[] columnsToExclude = {8, 2, 4, 11, 9};
        for (final Path dataFile : this.discreteDataFiles) {
            final TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, this.delimiter);
            columnReader.setCommentMarker(this.commentMarker);
            columnReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = true;
            final DataColumn[] dataColumns = columnReader.readInDataColumns(columnsToExclude, isDiscrete);

            long expected = 6;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            final TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            final Data data = dataReader.read(dataColumns, this.hasHeader);
            Assert.assertTrue(data instanceof DiscreteData);

            final DiscreteData verticalDiscreteData = (DiscreteData) data;

            final DiscreteDataColumn[] columns = verticalDiscreteData.getDataColumns();
            expected = 6;
            actual = columns.length;
            Assert.assertEquals(expected, actual);

            final int[][] discreteData = verticalDiscreteData.getData();

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
        final Set<String> namesOfColumnsToExclude = new HashSet<>(Arrays.asList("X10", "X11", "X3", "X4", "X1", "X6", "X8"));
        for (final Path dataFile : this.discreteDataFiles) {
            final TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, this.delimiter);
            columnReader.setCommentMarker(this.commentMarker);
            columnReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = true;
            final DataColumn[] dataColumns = columnReader.readInDataColumns(namesOfColumnsToExclude, isDiscrete);

            long expected = 4;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            final TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            final Data data = dataReader.read(dataColumns, this.hasHeader);
            Assert.assertTrue(data instanceof DiscreteData);

            final DiscreteData verticalDiscreteData = (DiscreteData) data;

            final DiscreteDataColumn[] columns = verticalDiscreteData.getDataColumns();
            expected = 4;
            actual = columns.length;
            Assert.assertEquals(expected, actual);

            final int[][] discreteData = verticalDiscreteData.getData();

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
        for (final Path dataFile : this.discreteDataFiles) {
            final TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, this.delimiter);
            columnReader.setCommentMarker(this.commentMarker);
            columnReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = true;
            final DataColumn[] dataColumns = columnReader.readInDataColumns(isDiscrete);

            long expected = 10;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            final TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            final Data data = dataReader.read(dataColumns, this.hasHeader);
            Assert.assertTrue(data instanceof DiscreteData);

            final DiscreteData verticalDiscreteData = (DiscreteData) data;

            final DiscreteDataColumn[] columns = verticalDiscreteData.getDataColumns();
            expected = 10;
            actual = columns.length;
            Assert.assertEquals(expected, actual);

            final int[][] discreteData = verticalDiscreteData.getData();

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
        final int[] columnsToExclude = {8, 2, 4, 11, 9};
        for (final Path dataFile : this.continuousDataFiles) {
            final TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, this.delimiter);
            columnReader.setCommentMarker(this.commentMarker);
            columnReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = false;
            final DataColumn[] dataColumns = columnReader.readInDataColumns(columnsToExclude, isDiscrete);

            long expected = 6;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            final TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            final Data data = dataReader.read(dataColumns, this.hasHeader);
            Assert.assertTrue(data instanceof ContinuousData);

            final ContinuousData continuousData = (ContinuousData) data;
            final double[][] contData = continuousData.getData();

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
        final Set<String> namesOfColumnsToExclude = new HashSet<>(Arrays.asList("X10", "X11", "X3", "X4", "X1", "X6", "X8"));
        for (final Path dataFile : this.continuousDataFiles) {
            final TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, this.delimiter);
            columnReader.setCommentMarker(this.commentMarker);
            columnReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = false;
            final DataColumn[] dataColumns = columnReader.readInDataColumns(namesOfColumnsToExclude, isDiscrete);

            long expected = 4;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            final TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            final Data data = dataReader.read(dataColumns, this.hasHeader);
            Assert.assertTrue(data instanceof ContinuousData);

            final ContinuousData continuousData = (ContinuousData) data;
            final double[][] contData = continuousData.getData();

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
        for (final Path dataFile : this.continuousDataFiles) {
            final TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, this.delimiter);
            columnReader.setCommentMarker(this.commentMarker);
            columnReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = false;
            final DataColumn[] dataColumns = columnReader.readInDataColumns(isDiscrete);

            long expected = 10;
            long actual = dataColumns.length;
            Assert.assertEquals(expected, actual);

            final TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            final Data data = dataReader.read(dataColumns, this.hasHeader);
            Assert.assertTrue(data instanceof ContinuousData);

            final ContinuousData continuousData = (ContinuousData) data;
            final double[][] contData = continuousData.getData();

            expected = 18;
            actual = contData.length;
            Assert.assertEquals(expected, actual);

            expected = 10;
            actual = contData[0].length;
            Assert.assertEquals(expected, actual);
        }
    }

    /**
     * Test of determineDiscreteDataColumns method, of class
     * TabularColumnFileReader.
     *
     * @throws IOException
     */
    @Test
    public void testDetermineDiscreteDataColumns() throws IOException {
        for (final Path dataFile : this.mixedDataFiles) {
            final TabularColumnReader fileReader = new TabularColumnFileReader(dataFile, this.delimiter);
            fileReader.setCommentMarker(this.commentMarker);
            fileReader.setQuoteCharacter(this.quoteCharacter);

            final boolean isDiscrete = false;
            final DataColumn[] dataColumns = fileReader.readInDataColumns(isDiscrete);

            final TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);
            dataReader.setCommentMarker(this.commentMarker);
            dataReader.setQuoteCharacter(this.quoteCharacter);
            dataReader.setMissingDataMarker(this.missingValueMarker);

            dataReader.determineDiscreteDataColumns(dataColumns, 4, this.hasHeader);
            final long numOfDiscrete = Arrays.stream(dataColumns)
                    .filter(DataColumn::isDiscrete)
                    .count();

            final long expected = 5;
            final long actual = numOfDiscrete;
            Assert.assertEquals(expected, actual);
        }
    }

}
