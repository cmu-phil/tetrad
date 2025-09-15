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

package edu.pitt.dbmi.data.reader.validation.covariance;

import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.validation.ValidationResult;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

/**
 * Nov 20, 2018 2:04:40 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class LowerCovarianceDataFileValidationTest {

    private final Delimiter delimiter = Delimiter.SPACE;

    private final Path dataFile = new File(getClass().getResource("/data/covariance/bad_spartina.txt").getFile()).toPath();

    public LowerCovarianceDataFileValidationTest() {
    }

    /**
     * Test of validate method, of class LowerCovarianceDataFileValidation.
     */
    @Test
    public void testValidate() {
        CovarianceValidation validation = new LowerCovarianceDataFileValidation(this.dataFile, this.delimiter);
        String commentMarker = "//";
        validation.setCommentMarker(commentMarker);
        char quoteCharacter = '"';
        validation.setQuoteCharacter(quoteCharacter);

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

        expected = 4;
        actual = errors.size();
        Assert.assertEquals(expected, actual);
    }

}

