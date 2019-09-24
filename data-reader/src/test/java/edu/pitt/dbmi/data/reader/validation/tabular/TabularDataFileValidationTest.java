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
package edu.pitt.dbmi.data.reader.validation.tabular;

import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.DataColumns;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.metadata.Metadata;
import edu.pitt.dbmi.data.reader.metadata.MetadataFileReader;
import edu.pitt.dbmi.data.reader.metadata.MetadataReader;
import edu.pitt.dbmi.data.reader.tabular.TabularColumnFileReader;
import edu.pitt.dbmi.data.reader.tabular.TabularColumnReader;
import edu.pitt.dbmi.data.reader.tabular.TabularDataFileReader;
import edu.pitt.dbmi.data.reader.tabular.TabularDataReader;
import edu.pitt.dbmi.data.reader.validation.ValidationResult;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * Dec 12, 2018 11:11:32 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TabularDataFileValidationTest {

    private final Delimiter delimiter = Delimiter.COMMA;
    private final char quoteCharacter = '"';
    private final String missingValueMarker = "*";
    private final String commentMarker = "//";
    private final boolean hasHeader = true;

    private final Path continuousDataFile = Paths.get(getClass()
            .getResource("/data/tabular/continuous/bad_data_sim_test_data.csv").getFile());

    private final Path discreteDataFile = Paths.get(getClass()
            .getResource("/data/tabular/discrete/bad_data_sim_test_data.csv").getFile());

    private final Path mixedDataFile = Paths.get(getClass()
            .getResource("/data/tabular/mixed/bad_data_sim_test_data.csv").getFile());

    public TabularDataFileValidationTest() {
    }

    @Test
    public void testValidateForMixedDataWithDateColumn() throws IOException {
        final Path dataFile = Paths.get(getClass().getResource("/data/metadata/mixed_with_dates.csv").getFile());
        final Path metadataFile = Paths.get(getClass().getResource("/data/metadata/mixed_with_dates_metadata.json").getFile());

        List<ValidationResult> infos = new LinkedList<>();
        List<ValidationResult> warnings = new LinkedList<>();
        List<ValidationResult> errors = new LinkedList<>();
        for (ValidationResult result : (new TabularColumnFileValidation(dataFile, delimiter)).validate()) {
            switch (result.getCode()) {
                case INFO:
                    infos.add(result);
                    break;
                case WARNING:
                    warnings.add(result);
                    break;
                default:
                    errors.add(result);
            }
        }

        long expected = 1;
        long actual = infos.size();
        Assert.assertEquals(expected, actual);

        expected = 0;
        actual = warnings.size();
        Assert.assertEquals(expected, actual);

        expected = 0;
        actual = errors.size();
        Assert.assertEquals(expected, actual);

        TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, delimiter);

        boolean isDiscrete = true;
        DataColumn[] dataColumns = columnReader.readInDataColumns(isDiscrete);

        TabularDataReader dataReader = new TabularDataFileReader(dataFile, delimiter);

        int numberOfCategories = 7;
        dataReader.determineDiscreteDataColumns(dataColumns, numberOfCategories, hasHeader);

        MetadataReader metadataReader = new MetadataFileReader(metadataFile);
        Metadata metadata = metadataReader.read();
        dataColumns = DataColumns.update(dataColumns, metadata);

        infos.clear();
        warnings.clear();
        errors.clear();
        for (ValidationResult result : (new TabularDataFileValidation(dataFile, delimiter)).validate(dataColumns, hasHeader)) {
            switch (result.getCode()) {
                case INFO:
                    infos.add(result);
                    break;
                case WARNING:
                    warnings.add(result);
                    break;
                default:
                    errors.add(result);
            }
        }

        expected = 1;
        actual = infos.size();
        Assert.assertEquals(expected, actual);

        expected = 0;
        actual = warnings.size();
        Assert.assertEquals(expected, actual);

        expected = 0;
        actual = errors.size();
        Assert.assertEquals(expected, actual);
    }

    /**
     * Test of validate method, of class TabularDataFileValidation.
     *
     * @throws IOException
     */
    @Test
    public void testValidateForMixedDataWithExcludedColumns() throws IOException {
        TabularColumnReader columnReader = new TabularColumnFileReader(mixedDataFile, delimiter);
        columnReader.setCommentMarker(commentMarker);
        columnReader.setQuoteCharacter(quoteCharacter);

        int[] excludedColumns = {1, 11, 9, 10, 3, 5};
        boolean isDiscrete = true;
        DataColumn[] dataColumns = columnReader.readInDataColumns(excludedColumns, isDiscrete);

        TabularDataReader dataReader = new TabularDataFileReader(mixedDataFile, delimiter);
        dataReader.setCommentMarker(commentMarker);
        dataReader.setQuoteCharacter(quoteCharacter);
        dataReader.setMissingDataMarker(missingValueMarker);

        int numberOfCategories = 4;
        dataReader.determineDiscreteDataColumns(dataColumns, numberOfCategories, hasHeader);

        TabularDataValidation validation = new TabularDataFileValidation(mixedDataFile, delimiter);
        validation.setCommentMarker(commentMarker);
        validation.setQuoteCharacter(quoteCharacter);
        validation.setMissingDataMarker(missingValueMarker);

        List<ValidationResult> results = validation.validate(dataColumns, hasHeader);
        List<ValidationResult> infos = new LinkedList<>();
        List<ValidationResult> warnings = new LinkedList<>();
        List<ValidationResult> errors = new LinkedList<>();
        for (ValidationResult result : results) {
            switch (result.getCode()) {
                case INFO:
                    infos.add(result);
                    break;
                case WARNING:
                    warnings.add(result);
                    break;
                default:
                    errors.add(result);
            }
        }

        long expected = 1;
        long actual = infos.size();
        Assert.assertEquals(expected, actual);

        expected = 0;
        actual = warnings.size();
        Assert.assertEquals(expected, actual);

        expected = 0;
        actual = errors.size();
        Assert.assertEquals(expected, actual);
    }

    /**
     * Test of validate method, of class TabularDataFileValidation.
     *
     * @throws IOException
     */
    @Test
    public void testValidateForMixedData() throws IOException {
        TabularColumnReader columnReader = new TabularColumnFileReader(mixedDataFile, delimiter);
        columnReader.setCommentMarker(commentMarker);
        columnReader.setQuoteCharacter(quoteCharacter);

        boolean isDiscrete = true;
        DataColumn[] dataColumns = columnReader.readInDataColumns(isDiscrete);

        TabularDataReader dataReader = new TabularDataFileReader(mixedDataFile, delimiter);
        dataReader.setCommentMarker(commentMarker);
        dataReader.setQuoteCharacter(quoteCharacter);
        dataReader.setMissingDataMarker(missingValueMarker);

        int numberOfCategories = 4;
        dataReader.determineDiscreteDataColumns(dataColumns, numberOfCategories, hasHeader);

        TabularDataValidation validation = new TabularDataFileValidation(mixedDataFile, delimiter);
        validation.setCommentMarker(commentMarker);
        validation.setQuoteCharacter(quoteCharacter);
        validation.setMissingDataMarker(missingValueMarker);

        List<ValidationResult> results = validation.validate(dataColumns, hasHeader);
        List<ValidationResult> infos = new LinkedList<>();
        List<ValidationResult> warnings = new LinkedList<>();
        List<ValidationResult> errors = new LinkedList<>();
        for (ValidationResult result : results) {
            switch (result.getCode()) {
                case INFO:
                    infos.add(result);
                    break;
                case WARNING:
                    warnings.add(result);
                    break;
                default:
                    errors.add(result);
            }
        }

        long expected = 1;
        long actual = infos.size();
        Assert.assertEquals(expected, actual);

        expected = 3;
        actual = warnings.size();
        Assert.assertEquals(expected, actual);

        expected = 0;
        actual = errors.size();
        Assert.assertEquals(expected, actual);
    }

    /**
     * Test of validate method, of class TabularDataFileValidation.
     *
     * @throws IOException
     */
    @Test
    public void testValidateForDiscreteDataWithExcludedColumns() throws IOException {
        TabularColumnReader columnReader = new TabularColumnFileReader(discreteDataFile, delimiter);
        columnReader.setCommentMarker(commentMarker);
        columnReader.setQuoteCharacter(quoteCharacter);

        int[] excludedColumns = {1, 11, 9, 10, 3};
        boolean isDiscrete = true;
        DataColumn[] dataColumns = columnReader.readInDataColumns(excludedColumns, isDiscrete);

        TabularDataValidation validation = new TabularDataFileValidation(discreteDataFile, delimiter);
        validation.setCommentMarker(commentMarker);
        validation.setQuoteCharacter(quoteCharacter);
        validation.setMissingDataMarker(missingValueMarker);

        List<ValidationResult> results = validation.validate(dataColumns, hasHeader);
        List<ValidationResult> infos = new LinkedList<>();
        List<ValidationResult> warnings = new LinkedList<>();
        List<ValidationResult> errors = new LinkedList<>();
        for (ValidationResult result : results) {
            switch (result.getCode()) {
                case INFO:
                    infos.add(result);
                    break;
                case WARNING:
                    warnings.add(result);
                    break;
                default:
                    errors.add(result);
            }
        }

        long expected = 1;
        long actual = infos.size();
        Assert.assertEquals(expected, actual);

        expected = 1;
        actual = warnings.size();
        Assert.assertEquals(expected, actual);

        expected = 0;
        actual = errors.size();
        Assert.assertEquals(expected, actual);
    }

    /**
     * Test of validate method, of class TabularDataFileValidation.
     *
     * @throws IOException
     */
    @Test
    public void testValidateForDiscreteData() throws IOException {
        TabularColumnReader columnReader = new TabularColumnFileReader(discreteDataFile, delimiter);
        columnReader.setCommentMarker(commentMarker);
        columnReader.setQuoteCharacter(quoteCharacter);

        boolean isDiscrete = true;
        DataColumn[] dataColumns = columnReader.readInDataColumns(isDiscrete);

        TabularDataValidation validation = new TabularDataFileValidation(discreteDataFile, delimiter);
        validation.setCommentMarker(commentMarker);
        validation.setQuoteCharacter(quoteCharacter);
        validation.setMissingDataMarker(missingValueMarker);

        List<ValidationResult> results = validation.validate(dataColumns, hasHeader);
        List<ValidationResult> infos = new LinkedList<>();
        List<ValidationResult> warnings = new LinkedList<>();
        List<ValidationResult> errors = new LinkedList<>();
        for (ValidationResult result : results) {
            switch (result.getCode()) {
                case INFO:
                    infos.add(result);
                    break;
                case WARNING:
                    warnings.add(result);
                    break;
                default:
                    errors.add(result);
            }
        }

        long expected = 1;
        long actual = infos.size();
        Assert.assertEquals(expected, actual);

        expected = 3;
        actual = warnings.size();
        Assert.assertEquals(expected, actual);

        expected = 0;
        actual = errors.size();
        Assert.assertEquals(expected, actual);
    }

    /**
     * Test of validate method, of class TabularDataFileValidation.
     *
     * @throws IOException
     */
    @Test
    public void testValidateForContinuousDataWithExcludedColumns() throws IOException {
        TabularColumnReader columnReader = new TabularColumnFileReader(continuousDataFile, delimiter);
        columnReader.setCommentMarker(commentMarker);
        columnReader.setQuoteCharacter(quoteCharacter);

        int[] excludedColumns = {6, 10, 1};
        boolean isDiscrete = false;
        DataColumn[] dataColumns = columnReader.readInDataColumns(excludedColumns, isDiscrete);

        TabularDataValidation validation = new TabularDataFileValidation(continuousDataFile, delimiter);
        validation.setCommentMarker(commentMarker);
        validation.setQuoteCharacter(quoteCharacter);
        validation.setMissingDataMarker(missingValueMarker);

        List<ValidationResult> results = validation.validate(dataColumns, hasHeader);
        List<ValidationResult> infos = new LinkedList<>();
        List<ValidationResult> warnings = new LinkedList<>();
        List<ValidationResult> errors = new LinkedList<>();
        for (ValidationResult result : results) {
            switch (result.getCode()) {
                case INFO:
                    infos.add(result);
                    break;
                case WARNING:
                    warnings.add(result);
                    break;
                default:
                    errors.add(result);
            }
        }

        long expected = 1;
        long actual = infos.size();
        Assert.assertEquals(expected, actual);

        expected = 0;
        actual = warnings.size();
        Assert.assertEquals(expected, actual);

        expected = 0;
        actual = errors.size();
        Assert.assertEquals(expected, actual);
    }

    /**
     * Test of validate method, of class TabularDataFileValidation.
     *
     * @throws IOException
     */
    @Test
    public void testValidateForContinuousData() throws IOException {
        TabularColumnReader columnReader = new TabularColumnFileReader(continuousDataFile, delimiter);
        columnReader.setCommentMarker(commentMarker);
        columnReader.setQuoteCharacter(quoteCharacter);

        boolean isDiscrete = false;
        DataColumn[] dataColumns = columnReader.readInDataColumns(isDiscrete);

        TabularDataValidation validation = new TabularDataFileValidation(continuousDataFile, delimiter);
        validation.setCommentMarker(commentMarker);
        validation.setQuoteCharacter(quoteCharacter);
        validation.setMissingDataMarker(missingValueMarker);

        List<ValidationResult> results = validation.validate(dataColumns, hasHeader);
        List<ValidationResult> infos = new LinkedList<>();
        List<ValidationResult> warnings = new LinkedList<>();
        List<ValidationResult> errors = new LinkedList<>();
        for (ValidationResult result : results) {
            switch (result.getCode()) {
                case INFO:
                    infos.add(result);
                    break;
                case WARNING:
                    warnings.add(result);
                    break;
                default:
                    errors.add(result);
            }
        }

        long expected = 1;
        long actual = infos.size();
        Assert.assertEquals(expected, actual);

        expected = 3;
        actual = warnings.size();
        Assert.assertEquals(expected, actual);

        expected = 1;
        actual = errors.size();
        Assert.assertEquals(expected, actual);
    }

}
