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

import edu.pitt.dbmi.data.reader.DataFileReader;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.validation.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;

/**
 * Dec 12, 2018 11:04:58 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class LowerCovarianceDataFileValidation extends AbstractDataFileValidation implements CovarianceValidation {

    public LowerCovarianceDataFileValidation(final Path dataFile, final Delimiter delimiter) {
        super(dataFile, delimiter);
    }

    @Override
    public List<ValidationResult> validate() {
        final List<ValidationResult> validationResults = new LinkedList<>();

        try {
            final int numOfCases = validateNumberOfCases(validationResults);
            final int numOfVars = validateVariables(validationResults);
            validateData(numOfVars, validationResults);

            if (validationResults.size() <= this.maxNumOfMsg) {
                final String infoMsg = String.format("There are %d cases and %d variables.", numOfCases, numOfVars);
                final ValidationResult result = new ValidationResult(ValidationCode.INFO, MessageType.FILE_SUMMARY, infoMsg);
                result.setAttribute(ValidationAttribute.ROW_NUMBER, numOfCases);
                result.setAttribute(ValidationAttribute.COLUMN_NUMBER, numOfVars);
                validationResults.add(result);
            }
        } catch (final IOException exception) {
            if (validationResults.size() <= this.maxNumOfMsg) {
                final String errMsg = String.format("Unable to read file %s.", this.dataFile.getFileName());
                final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_IO_ERROR, errMsg);
                result.setAttribute(ValidationAttribute.FILE_NAME, this.dataFile.getFileName());
                validationResults.add(result);
            }
        }

        return validationResults;
    }

    private void validateData(final int numOfVars, final List<ValidationResult> results) throws IOException {
        try (final InputStream in = Files.newInputStream(this.dataFile, StandardOpenOption.READ)) {
            boolean skip = false;
            boolean hasSeenNonblankChar = false;
            boolean hasQuoteChar = false;

            final byte delimChar = this.delimiter.getByteValue();

            // comment marker check
            final byte[] comment = this.commentMarker.getBytes();
            int cmntIndex = 0;
            boolean checkForComment = comment.length > 0;

            int lineDataNum = 1;
            int lineNum = 1;
            int colNum = 0;
            int rowNum = 1;

            final StringBuilder dataBuilder = new StringBuilder();
            byte prevChar = -1;
            final byte[] buffer = new byte[DataFileReader.BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && results.size() <= this.maxNumOfMsg && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < len && !Thread.currentThread().isInterrupted(); i++) {
                    final byte currChar = buffer[i];

                    if (currChar == DataFileReader.CARRIAGE_RETURN || currChar == DataFileReader.LINE_FEED) {
                        if (currChar == DataFileReader.LINE_FEED && prevChar == DataFileReader.CARRIAGE_RETURN) {
                            prevChar = DataFileReader.LINE_FEED;
                            continue;
                        }

                        if (hasSeenNonblankChar && !skip) {
                            if (lineDataNum >= 3) {
                                colNum++;
                                final String value = dataBuilder.toString().trim();

                                if (colNum > rowNum) {
                                    if (results.size() <= this.maxNumOfMsg) {
                                        final String errMsg = String.format(
                                                "Line %d: Excess data.  Expect %d value(s) but encounter %d.",
                                                lineNum, rowNum, colNum);
                                        final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_EXCESS_DATA, errMsg);
                                        result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                        result.setAttribute(ValidationAttribute.EXPECTED_COUNT, rowNum);
                                        result.setAttribute(ValidationAttribute.ACTUAL_COUNT, colNum);
                                        results.add(result);
                                    }
                                } else if (colNum < rowNum) {
                                    if (results.size() <= this.maxNumOfMsg) {
                                        final String errMsg = String.format(
                                                "Line %d: Insufficient data.  Expect %d value(s) but encounter %d.",
                                                lineNum, rowNum, colNum);
                                        final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_INSUFFICIENT_DATA, errMsg);
                                        result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                        result.setAttribute(ValidationAttribute.EXPECTED_COUNT, rowNum);
                                        result.setAttribute(ValidationAttribute.ACTUAL_COUNT, colNum);
                                        results.add(result);
                                    }
                                } else {
                                    if (value.isEmpty()) {
                                        if (results.size() <= this.maxNumOfMsg) {
                                            final String errMsg = String.format("Line %d, column %d: Missing value.", lineNum, colNum);
                                            final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_MISSING_VALUE, errMsg);
                                            result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                            result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                            results.add(result);
                                        }
                                    } else {
                                        try {
                                            Double.parseDouble(value);
                                        } catch (final NumberFormatException exception) {
                                            if (results.size() <= this.maxNumOfMsg) {
                                                final String errMsg = String.format("Line %d, column %d: Invalid number %s.", lineNum, colNum, value);
                                                final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_INVALID_NUMBER, errMsg);
                                                result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                                result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                                result.setAttribute(ValidationAttribute.VALUE, value);
                                                results.add(result);
                                            }
                                        }
                                    }
                                }

                                rowNum++;
                            }

                            lineDataNum++;
                        }

                        lineNum++;

                        // clear data
                        dataBuilder.delete(0, dataBuilder.length());

                        // reset states
                        skip = false;
                        hasSeenNonblankChar = false;
                        cmntIndex = 0;
                        colNum = 0;
                        checkForComment = comment.length > 0;
                    } else if (!skip) {
                        if (currChar > DataFileReader.SPACE_CHAR) {
                            hasSeenNonblankChar = true;
                        }

                        // skip blank chars at the begining of the line
                        if (currChar <= DataFileReader.SPACE_CHAR && !hasSeenNonblankChar) {
                            continue;
                        }

                        // check for comment marker to skip line
                        if (checkForComment) {
                            if (currChar == comment[cmntIndex]) {
                                cmntIndex++;
                                if (cmntIndex == comment.length) {
                                    skip = true;
                                    prevChar = currChar;
                                    continue;
                                }
                            } else {
                                checkForComment = false;
                            }
                        }

                        if (lineDataNum >= 3) {
                            if (currChar == this.quoteCharacter) {
                                hasQuoteChar = !hasQuoteChar;
                            } else if (!hasQuoteChar) {
                                final boolean isDelimiter;
                                switch (this.delimiter) {
                                    case WHITESPACE:
                                        isDelimiter = (currChar <= DataFileReader.SPACE_CHAR) && (prevChar > DataFileReader.SPACE_CHAR);
                                        break;
                                    default:
                                        isDelimiter = (currChar == delimChar);
                                }

                                if (isDelimiter) {
                                    colNum++;
                                    final String value = dataBuilder.toString().trim();
                                    dataBuilder.delete(0, dataBuilder.length());

                                    if (colNum > rowNum) {
                                        if (results.size() <= this.maxNumOfMsg) {
                                            final String errMsg = String.format(
                                                    "Line %d: Excess data.  Expect %d value(s) but encounter %d.",
                                                    lineNum, rowNum, colNum);
                                            final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_EXCESS_DATA, errMsg);
                                            result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                            result.setAttribute(ValidationAttribute.EXPECTED_COUNT, rowNum);
                                            result.setAttribute(ValidationAttribute.ACTUAL_COUNT, colNum);
                                            results.add(result);
                                        }
                                    } else {
                                        if (value.isEmpty()) {
                                            if (results.size() <= this.maxNumOfMsg) {
                                                final String errMsg = String.format("Line %d, column %d: Missing value.", lineNum, colNum);
                                                final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_MISSING_VALUE, errMsg);
                                                result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                                result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                                results.add(result);
                                            }
                                        } else {
                                            try {
                                                Double.parseDouble(value);
                                            } catch (final NumberFormatException exception) {
                                                if (results.size() <= this.maxNumOfMsg) {
                                                    final String errMsg = String.format("Line %d, column %d: Invalid number %s.", lineNum, colNum, value);
                                                    final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_INVALID_NUMBER, errMsg);
                                                    result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                                    result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                                    result.setAttribute(ValidationAttribute.VALUE, value);
                                                    results.add(result);
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    dataBuilder.append((char) currChar);
                                }
                            }
                        }
                    }

                    prevChar = currChar;
                }
            }

            if (hasSeenNonblankChar && !skip) {
                if (lineDataNum >= 3) {
                    colNum++;
                    final String value = dataBuilder.toString().trim();

                    if (colNum > rowNum) {
                        if (results.size() <= this.maxNumOfMsg) {
                            final String errMsg = String.format(
                                    "Line %d: Excess data.  Expect %d value(s) but encounter %d.",
                                    lineNum, rowNum, colNum);
                            final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_EXCESS_DATA, errMsg);
                            result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                            result.setAttribute(ValidationAttribute.EXPECTED_COUNT, rowNum);
                            result.setAttribute(ValidationAttribute.ACTUAL_COUNT, colNum);
                            results.add(result);
                        }
                    } else if (colNum < rowNum) {
                        if (results.size() <= this.maxNumOfMsg) {
                            final String errMsg = String.format(
                                    "Line %d: Insufficient data.  Expect %d value(s) but encounter %d.",
                                    lineNum, rowNum, colNum);
                            final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_INSUFFICIENT_DATA, errMsg);
                            result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                            result.setAttribute(ValidationAttribute.EXPECTED_COUNT, rowNum);
                            result.setAttribute(ValidationAttribute.ACTUAL_COUNT, colNum);
                            results.add(result);
                        }
                    } else {
                        if (value.isEmpty()) {
                            if (results.size() <= this.maxNumOfMsg) {
                                final String errMsg = String.format("Line %d, column %d: Missing value.", lineNum, colNum);
                                final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_MISSING_VALUE, errMsg);
                                result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                results.add(result);
                            }
                        } else {
                            try {
                                Double.parseDouble(value);
                            } catch (final NumberFormatException exception) {
                                if (results.size() <= this.maxNumOfMsg) {
                                    final String errMsg = String.format("Line %d, column %d: Invalid number %s.", lineNum, colNum, value);
                                    final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_INVALID_NUMBER, errMsg);
                                    result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                    result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                    result.setAttribute(ValidationAttribute.VALUE, value);
                                    results.add(result);
                                }
                            }
                        }
                    }

                    rowNum++;
                }
            }

            rowNum--;  // minus the extra count for possibly the next line
            if (rowNum > numOfVars) {
                if (results.size() <= this.maxNumOfMsg) {
                    final String errMsg = String.format(
                            "Excess data.  Expect %d row(s) but encounter %d.",
                            numOfVars, rowNum);
                    final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_EXCESS_DATA, errMsg);
                    result.setAttribute(ValidationAttribute.EXPECTED_COUNT, numOfVars);
                    result.setAttribute(ValidationAttribute.ACTUAL_COUNT, rowNum);
                    results.add(result);
                }
            } else if (rowNum < numOfVars) {
                if (results.size() <= this.maxNumOfMsg) {
                    final String errMsg = String.format(
                            "Insufficient data.  Expect %d row(s) but encounter %d.",
                            numOfVars, rowNum);
                    final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_EXCESS_DATA, errMsg);
                    result.setAttribute(ValidationAttribute.EXPECTED_COUNT, numOfVars);
                    result.setAttribute(ValidationAttribute.ACTUAL_COUNT, rowNum);
                    results.add(result);
                }
            }
        }
    }

    private int validateVariables(final List<ValidationResult> results) throws IOException {
        int numOfVars = 0;

        try (final InputStream in = Files.newInputStream(this.dataFile, StandardOpenOption.READ)) {
            boolean skip = false;
            boolean hasSeenNonblankChar = false;
            boolean hasQuoteChar = false;
            boolean finished = false;

            final byte delimChar = this.delimiter.getByteValue();

            // comment marker check
            final byte[] comment = this.commentMarker.getBytes();
            int cmntIndex = 0;
            boolean checkForComment = comment.length > 0;

            int lineDataNum = 1;
            int colNum = 0;
            int lineNum = 1;

            final StringBuilder dataBuilder = new StringBuilder();
            byte prevChar = -1;
            final byte[] buffer = new byte[DataFileReader.BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !finished && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < len && !finished && !Thread.currentThread().isInterrupted(); i++) {
                    final byte currChar = buffer[i];

                    if (currChar == DataFileReader.CARRIAGE_RETURN || currChar == DataFileReader.LINE_FEED) {
                        if (currChar == DataFileReader.LINE_FEED && prevChar == DataFileReader.CARRIAGE_RETURN) {
                            prevChar = DataFileReader.LINE_FEED;
                            continue;
                        }

                        if (hasSeenNonblankChar && !skip) {
                            if (lineDataNum == 2) {
                                final String value = dataBuilder.toString().trim();

                                colNum++;
                                if (value.isEmpty()) {
                                    if (results.size() <= this.maxNumOfMsg) {
                                        final String errMsg = String.format("Line %d, column %d: Missing value.", lineNum, colNum);
                                        final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_MISSING_VALUE, errMsg);
                                        result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                        result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                        results.add(result);
                                    }
                                }

                                numOfVars++;

                                finished = true;
                            }

                            lineDataNum++;
                        }

                        lineNum++;

                        // clear data
                        dataBuilder.delete(0, dataBuilder.length());

                        // reset states
                        skip = false;
                        hasSeenNonblankChar = false;
                        cmntIndex = 0;
                        checkForComment = comment.length > 0;
                    } else if (!skip) {
                        if (currChar > DataFileReader.SPACE_CHAR) {
                            hasSeenNonblankChar = true;
                        }

                        // skip blank chars at the begining of the line
                        if (currChar <= DataFileReader.SPACE_CHAR && !hasSeenNonblankChar) {
                            continue;
                        }

                        // check for comment marker to skip line
                        if (checkForComment) {
                            if (currChar == comment[cmntIndex]) {
                                cmntIndex++;
                                if (cmntIndex == comment.length) {
                                    skip = true;
                                    prevChar = currChar;
                                    continue;
                                }
                            } else {
                                checkForComment = false;
                            }
                        }

                        if (lineDataNum == 2) {
                            if (currChar == this.quoteCharacter) {
                                hasQuoteChar = !hasQuoteChar;
                            } else if (!hasQuoteChar) {
                                final boolean isDelimiter;
                                switch (this.delimiter) {
                                    case WHITESPACE:
                                        isDelimiter = (currChar <= DataFileReader.SPACE_CHAR) && (prevChar > DataFileReader.SPACE_CHAR);
                                        break;
                                    default:
                                        isDelimiter = (currChar == delimChar);
                                }

                                if (isDelimiter) {
                                    final String value = dataBuilder.toString().trim();
                                    dataBuilder.delete(0, dataBuilder.length());

                                    colNum++;
                                    if (value.isEmpty()) {
                                        if (results.size() <= this.maxNumOfMsg) {
                                            final String errMsg = String.format("Line %d, column %d: Missing value.", lineNum, colNum);
                                            final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_MISSING_VALUE, errMsg);
                                            result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                            result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                            results.add(result);
                                        }
                                    }

                                    numOfVars++;
                                } else {
                                    dataBuilder.append((char) currChar);
                                }
                            }
                        }
                    }

                    prevChar = currChar;
                }
            }

            if (hasSeenNonblankChar && !skip) {
                if (lineDataNum == 2) {
                    final String value = dataBuilder.toString().trim();
                    dataBuilder.delete(0, dataBuilder.length());

                    colNum++;
                    if (value.isEmpty()) {
                        if (results.size() <= this.maxNumOfMsg) {
                            final String errMsg = String.format("Line %d, column %d: Missing value.", lineNum, colNum);
                            final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_MISSING_VALUE, errMsg);
                            result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                            result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                            results.add(result);
                        }
                    }

                    numOfVars++;
                }
            }
        }

        if (numOfVars == 0) {
            if (results.size() <= this.maxNumOfMsg) {
                final String errMsg = "Covariance file does not contain variable names.";
                final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_MISSING_VALUE, errMsg);
                results.add(result);
            }
        }

        return numOfVars;
    }

    private int validateNumberOfCases(final List<ValidationResult> results) throws IOException {
        int count = 0;

        try (final InputStream in = Files.newInputStream(this.dataFile, StandardOpenOption.READ)) {
            boolean skip = false;
            boolean hasSeenNonblankChar = false;
            boolean hasQuoteChar = false;
            boolean finished = false;

            // comment marker check
            final byte[] comment = this.commentMarker.getBytes();
            int cmntIndex = 0;
            boolean checkForComment = comment.length > 0;

            int lineNum = 1;

            final StringBuilder dataBuilder = new StringBuilder();
            byte prevChar = -1;
            final byte[] buffer = new byte[DataFileReader.BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !finished && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < len && !finished && !Thread.currentThread().isInterrupted(); i++) {
                    final byte currChar = buffer[i];

                    if (currChar == DataFileReader.CARRIAGE_RETURN || currChar == DataFileReader.LINE_FEED) {
                        if (currChar == DataFileReader.LINE_FEED && prevChar == DataFileReader.CARRIAGE_RETURN) {
                            prevChar = DataFileReader.LINE_FEED;
                            continue;
                        }

                        finished = hasSeenNonblankChar && !skip;
                        if (!finished) {
                            lineNum++;

                            // clear data
                            dataBuilder.delete(0, dataBuilder.length());

                            // reset states
                            skip = false;
                            hasSeenNonblankChar = false;
                            cmntIndex = 0;
                            checkForComment = comment.length > 0;
                        }
                    } else if (!skip) {
                        if (currChar > DataFileReader.SPACE_CHAR) {
                            hasSeenNonblankChar = true;
                        }

                        // skip blank chars at the begining of the line
                        if (currChar <= DataFileReader.SPACE_CHAR && !hasSeenNonblankChar) {
                            continue;
                        }

                        // check for comment marker to skip line
                        if (checkForComment) {
                            if (currChar == comment[cmntIndex]) {
                                cmntIndex++;
                                if (cmntIndex == comment.length) {
                                    skip = true;
                                    prevChar = currChar;

                                    continue;
                                }
                            } else {
                                checkForComment = false;
                            }
                        }

                        if (currChar == this.quoteCharacter) {
                            hasQuoteChar = !hasQuoteChar;
                        } else if (!hasQuoteChar) {
                            dataBuilder.append((char) currChar);
                        }
                    }

                    prevChar = currChar;
                }
            }

            final String value = dataBuilder.toString().trim();
            if (value.isEmpty()) {
                if (results.size() <= this.maxNumOfMsg) {
                    final String errMsg = String.format("Line %d: Missing number of cases.", lineNum);
                    final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_MISSING_VALUE, errMsg);
                    result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                    results.add(result);
                }
            } else {
                try {
                    count += Integer.parseInt(value);
                } catch (final NumberFormatException exception) {
                    if (results.size() <= this.maxNumOfMsg) {
                        final String errMsg = String.format("Line %d: Invalid number %s.", lineNum, value);
                        final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_INVALID_NUMBER, errMsg);
                        result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                        result.setAttribute(ValidationAttribute.VALUE, value);
                        results.add(result);
                    }
                }
            }
        }

        return count;
    }

}
