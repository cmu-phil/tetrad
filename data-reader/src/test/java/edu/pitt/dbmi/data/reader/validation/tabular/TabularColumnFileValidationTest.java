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

import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.validation.ValidationResult;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

/**
 * Dec 12, 2018 4:16:55 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TabularColumnFileValidationTest {

    private final Delimiter delimiter = Delimiter.COMMA;
    private final char quoteCharacter = '"';
    private final String commentMarker = "//";

    private final Path dataFile = new File(getClass().getResource("/data/tabular/continuous/bad_column_sim_test_data.csv").getFile()).toPath();

    public TabularColumnFileValidationTest() {
    }

    /**
     * Test of validate method, of class TabularColumnFileValidation.
     */
    @Test
    public void testValidate() {
        TabularColumnValidation validation = new TabularColumnFileValidation(this.dataFile, this.delimiter);
        validation.setCommentMarker(this.commentMarker);
        validation.setQuoteCharacter(this.quoteCharacter);

        List<ValidationResult> results = validation.validate();
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

        expected = 1;
        actual = errors.size();
        Assert.assertEquals(expected, actual);
    }

    /**
     * Test of validate method, of class TabularColumnFileValidation.
     */
    @Test
    public void testValidateWithExcludedColumnSet() {
        TabularColumnValidation validation = new TabularColumnFileValidation(this.dataFile, this.delimiter);
        validation.setCommentMarker(this.commentMarker);
        validation.setQuoteCharacter(this.quoteCharacter);

        Set<String> columnNames = new HashSet<>(Arrays.asList("\"X3\"", "X5", "X1", " ", "X7", "X9", "", "X10", "X11"));

        List<ValidationResult> results = validation.validate(columnNames);
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

        expected = 1;
        actual = errors.size();
        Assert.assertEquals(expected, actual);
    }

    /**
     * Test of validate method, of class TabularColumnFileValidation.
     */
    @Test
    public void testValidateWithExcludedColumnArray() {
        TabularColumnValidation validation = new TabularColumnFileValidation(this.dataFile, this.delimiter);
        validation.setCommentMarker(this.commentMarker);
        validation.setQuoteCharacter(this.quoteCharacter);

        int[] excludedColumns = {7, 5};

        List<ValidationResult> results = validation.validate(excludedColumns);
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

}

