///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

import edu.pitt.dbmi.data.reader.DataReaderException;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.covariance.FullCovarianceFormat;
import edu.pitt.dbmi.data.reader.validation.AbstractDataFileValidation;
import edu.pitt.dbmi.data.reader.validation.MessageType;
import edu.pitt.dbmi.data.reader.validation.ValidationAttribute;
import edu.pitt.dbmi.data.reader.validation.ValidationCode;
import edu.pitt.dbmi.data.reader.validation.ValidationResult;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;

/**
 * Validates a covariance file where the covariance is stored as a full (square) matrix. The accepted formats,
 * including the classical Tetrad format (sample size line, variable-name line, then the square matrix) and its
 * tolerated relaxations (missing sample-size line, row names, corner cell), are documented on
 * {@link FullCovarianceFormat}, which does the parsing. Because this class and
 * {@link edu.pitt.dbmi.data.reader.covariance.FullCovarianceDataFileReader} delegate to the same parser, a file
 * that validates here is guaranteed to load there, and vice versa.
 * <p>
 * Format errors are reported as ERROR results; a missing sample-size line is reported as a WARNING result stating
 * the assumed sample size; and a successful validation adds an INFO summary of the number of cases and variables,
 * as before.
 *
 * @author Kevin V. Bui (kvb2@pitt.edu), adapted for square matrices
 * @author josephramsey
 * @see FullCovarianceFormat
 */
public class FullCovarianceDataFileValidation extends AbstractDataFileValidation implements CovarianceValidation {

    /**
     * Constructor.
     *
     * @param dataFile  the data file.
     * @param delimiter the delimiter.
     */
    public FullCovarianceDataFileValidation(Path dataFile, Delimiter delimiter) {
        super(dataFile, delimiter);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Validate the covariance.
     */
    @Override
    public List<ValidationResult> validate() {
        List<ValidationResult> validationResults = new LinkedList<>();

        try {
            FullCovarianceFormat.ParseResult parseResult = FullCovarianceFormat.parse(this.dataFile, this.delimiter,
                    this.quoteCharacter, this.commentMarker);

            for (String warning : parseResult.warnings()) {
                if (validationResults.size() <= this.maxNumOfMsg) {
                    validationResults.add(new ValidationResult(ValidationCode.WARNING, MessageType.FILE_SUMMARY,
                            warning));
                }
            }

            if (validationResults.size() <= this.maxNumOfMsg) {
                int numOfCases = parseResult.numberOfCases();
                int numOfVars = parseResult.variables().size();
                String infoMsg = String.format("There are %d cases and %d variables.", numOfCases, numOfVars);
                ValidationResult result = new ValidationResult(ValidationCode.INFO, MessageType.FILE_SUMMARY, infoMsg);
                result.setAttribute(ValidationAttribute.ROW_NUMBER, numOfCases);
                result.setAttribute(ValidationAttribute.COLUMN_NUMBER, numOfVars);
                validationResults.add(result);
            }
        } catch (DataReaderException exception) {
            if (validationResults.size() <= this.maxNumOfMsg) {
                validationResults.add(new ValidationResult(ValidationCode.ERROR, MessageType.FILE_EXCESS_DATA,
                        exception.getMessage()));
            }
        } catch (IOException exception) {
            if (validationResults.size() <= this.maxNumOfMsg) {
                String errMsg = String.format("Unable to read file %s.", this.dataFile.getFileName());
                ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_IO_ERROR, errMsg);
                result.setAttribute(ValidationAttribute.FILE_NAME, this.dataFile.getFileName());
                validationResults.add(result);
            }
        }

        return validationResults;
    }
}
