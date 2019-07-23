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
package edu.pitt.dbmi.data.reader.validation.covariance;

import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.validation.ValidationResult;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * Nov 20, 2018 2:04:40 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class LowerCovarianceDataFileValidationTest {

    private final Delimiter delimiter = Delimiter.SPACE;
    private final char quoteCharacter = '"';
    private final String commentMarker = "//";

    private final Path dataFile = Paths.get(getClass().getResource("/data/covariance/bad_spartina.txt").getFile());

    public LowerCovarianceDataFileValidationTest() {
    }

    /**
     * Test of validate method, of class LowerCovarianceDataFileValidation.
     */
    @Test
    public void testValidate() {
        CovarianceValidation validation = new LowerCovarianceDataFileValidation(dataFile, delimiter);
        validation.setCommentMarker(commentMarker);
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
