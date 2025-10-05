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
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

/**
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

    private final Path continuousDataFile = new File(getClass()
            .getResource("/data/tabular/continuous/bad_data_sim_test_data.csv").getFile()).toPath();

    private final Path discreteDataFile = new File(getClass()
            .getResource("/data/tabular/discrete/bad_data_sim_test_data.csv").getFile()).toPath();

    private final Path mixedDataFile = new File(getClass()
            .getResource("/data/tabular/mixed/bad_data_sim_test_data.csv").getFile()).toPath();

    public TabularDataFileValidationTest() {
    }

    @Test
    public void testValidateForMixedDataWithDateColumn() throws IOException {
        Path dataFile = new File(getClass().getResource("/data/metadata/mixed_with_dates.csv").getFile()).toPath();
        Path metadataFile = new File(getClass().getResource("/data/metadata/mixed_with_dates_metadata.json").getFile()).toPath();

        List<ValidationResult> infos = new LinkedList<>();
        List<ValidationResult> warnings = new LinkedList<>();
        List<ValidationResult> errors = new LinkedList<>();
        for (ValidationResult result : (new TabularColumnFileValidation(dataFile, this.delimiter)).validate()) {
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

        TabularColumnReader columnReader = new TabularColumnFileReader(dataFile, this.delimiter);

        final boolean isDiscrete = true;
        DataColumn[] dataColumns = columnReader.readInDataColumns(isDiscrete);

        TabularDataReader dataReader = new TabularDataFileReader(dataFile, this.delimiter);

        final int numberOfCategories = 7;
        dataReader.determineDiscreteDataColumns(dataColumns, numberOfCategories, this.hasHeader);

        MetadataReader metadataReader = new MetadataFileReader(metadataFile);
        Metadata metadata = metadataReader.read();
        dataColumns = DataColumns.update(dataColumns, metadata);

        infos.clear();
        warnings.clear();
        errors.clear();
        for (ValidationResult result : (new TabularDataFileValidation(dataFile, this.delimiter)).validate(dataColumns, this.hasHeader)) {
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
        TabularColumnReader columnReader = new TabularColumnFileReader(this.mixedDataFile, this.delimiter);
        columnReader.setCommentMarker(this.commentMarker);
        columnReader.setQuoteCharacter(this.quoteCharacter);

        int[] excludedColumns = {1, 11, 9, 10, 3, 5};
        final boolean isDiscrete = true;
        DataColumn[] dataColumns = columnReader.readInDataColumns(excludedColumns, isDiscrete);

        TabularDataReader dataReader = new TabularDataFileReader(this.mixedDataFile, this.delimiter);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteCharacter);
        dataReader.setMissingDataMarker(this.missingValueMarker);

        final int numberOfCategories = 4;
        dataReader.determineDiscreteDataColumns(dataColumns, numberOfCategories, this.hasHeader);

        TabularDataValidation validation = new TabularDataFileValidation(this.mixedDataFile, this.delimiter);
        validation.setCommentMarker(this.commentMarker);
        validation.setQuoteCharacter(this.quoteCharacter);
        validation.setMissingDataMarker(this.missingValueMarker);

        List<ValidationResult> results = validation.validate(dataColumns, this.hasHeader);
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
        TabularColumnReader columnReader = new TabularColumnFileReader(this.mixedDataFile, this.delimiter);
        columnReader.setCommentMarker(this.commentMarker);
        columnReader.setQuoteCharacter(this.quoteCharacter);

        final boolean isDiscrete = true;
        DataColumn[] dataColumns = columnReader.readInDataColumns(isDiscrete);

        TabularDataReader dataReader = new TabularDataFileReader(this.mixedDataFile, this.delimiter);
        dataReader.setCommentMarker(this.commentMarker);
        dataReader.setQuoteCharacter(this.quoteCharacter);
        dataReader.setMissingDataMarker(this.missingValueMarker);

        final int numberOfCategories = 4;
        dataReader.determineDiscreteDataColumns(dataColumns, numberOfCategories, this.hasHeader);

        TabularDataValidation validation = new TabularDataFileValidation(this.mixedDataFile, this.delimiter);
        validation.setCommentMarker(this.commentMarker);
        validation.setQuoteCharacter(this.quoteCharacter);
        validation.setMissingDataMarker(this.missingValueMarker);

        List<ValidationResult> results = validation.validate(dataColumns, this.hasHeader);
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
        TabularColumnReader columnReader = new TabularColumnFileReader(this.discreteDataFile, this.delimiter);
        columnReader.setCommentMarker(this.commentMarker);
        columnReader.setQuoteCharacter(this.quoteCharacter);

        int[] excludedColumns = {1, 11, 9, 10, 3};
        final boolean isDiscrete = true;
        DataColumn[] dataColumns = columnReader.readInDataColumns(excludedColumns, isDiscrete);

        TabularDataValidation validation = new TabularDataFileValidation(this.discreteDataFile, this.delimiter);
        validation.setCommentMarker(this.commentMarker);
        validation.setQuoteCharacter(this.quoteCharacter);
        validation.setMissingDataMarker(this.missingValueMarker);

        List<ValidationResult> results = validation.validate(dataColumns, this.hasHeader);
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
        TabularColumnReader columnReader = new TabularColumnFileReader(this.discreteDataFile, this.delimiter);
        columnReader.setCommentMarker(this.commentMarker);
        columnReader.setQuoteCharacter(this.quoteCharacter);

        final boolean isDiscrete = true;
        DataColumn[] dataColumns = columnReader.readInDataColumns(isDiscrete);

        TabularDataValidation validation = new TabularDataFileValidation(this.discreteDataFile, this.delimiter);
        validation.setCommentMarker(this.commentMarker);
        validation.setQuoteCharacter(this.quoteCharacter);
        validation.setMissingDataMarker(this.missingValueMarker);

        List<ValidationResult> results = validation.validate(dataColumns, this.hasHeader);
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
        TabularColumnReader columnReader = new TabularColumnFileReader(this.continuousDataFile, this.delimiter);
        columnReader.setCommentMarker(this.commentMarker);
        columnReader.setQuoteCharacter(this.quoteCharacter);

        int[] excludedColumns = {6, 10, 1};
        final boolean isDiscrete = false;
        DataColumn[] dataColumns = columnReader.readInDataColumns(excludedColumns, isDiscrete);

        TabularDataValidation validation = new TabularDataFileValidation(this.continuousDataFile, this.delimiter);
        validation.setCommentMarker(this.commentMarker);
        validation.setQuoteCharacter(this.quoteCharacter);
        validation.setMissingDataMarker(this.missingValueMarker);

        List<ValidationResult> results = validation.validate(dataColumns, this.hasHeader);
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
        TabularColumnReader columnReader = new TabularColumnFileReader(this.continuousDataFile, this.delimiter);
        columnReader.setCommentMarker(this.commentMarker);
        columnReader.setQuoteCharacter(this.quoteCharacter);

        final boolean isDiscrete = false;
        DataColumn[] dataColumns = columnReader.readInDataColumns(isDiscrete);

        TabularDataValidation validation = new TabularDataFileValidation(this.continuousDataFile, this.delimiter);
        validation.setCommentMarker(this.commentMarker);
        validation.setQuoteCharacter(this.quoteCharacter);
        validation.setMissingDataMarker(this.missingValueMarker);

        List<ValidationResult> results = validation.validate(dataColumns, this.hasHeader);
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

