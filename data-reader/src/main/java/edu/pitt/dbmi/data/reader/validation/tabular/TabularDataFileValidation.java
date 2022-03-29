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
import edu.pitt.dbmi.data.reader.DatasetFileReader;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.validation.MessageType;
import edu.pitt.dbmi.data.reader.validation.ValidationAttribute;
import edu.pitt.dbmi.data.reader.validation.ValidationCode;
import edu.pitt.dbmi.data.reader.validation.ValidationResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;

/**
 * Dec 12, 2018 10:56:57 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TabularDataFileValidation extends DatasetFileReader implements TabularDataValidation {

    private int maxNumOfMsg;

    public TabularDataFileValidation(final Path dataFile, final Delimiter delimiter) {
        super(dataFile, delimiter);
        this.maxNumOfMsg = Integer.MAX_VALUE;
    }

    @Override
    public List<ValidationResult> validate(final DataColumn[] dataColumns, final boolean hasHeader) {
        final List<ValidationResult> results = new LinkedList<>();

        boolean isDiscrete = false;
        boolean isContinuous = false;
        for (final DataColumn dataColumn : dataColumns) {
            if (dataColumn.isDiscrete()) {
                isDiscrete = true;
            } else {
                isContinuous = true;
            }

            if (isDiscrete && isContinuous) {
                break;
            }
        }

        try {
            if (isDiscrete && isContinuous) {
                validateMixedData(dataColumns, hasHeader, results);
            } else if (isContinuous) {
                validateContinuousData(dataColumns, hasHeader, results);
            } else if (isDiscrete) {
                validateDiscreteData(dataColumns, hasHeader, results);
            } else {
                // do nothing because dataColumns is empty
            }
        } catch (final IOException exception) {
            if (results.size() <= this.maxNumOfMsg) {
                final String errMsg = String.format("Unable to read file %s.", this.dataFile.getFileName());
                final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_IO_ERROR, errMsg);
                result.setAttribute(ValidationAttribute.FILE_NAME, this.dataFile.getFileName());
                results.add(result);
            }
        }

        return results;
    }

    private void validateDiscreteData(final DataColumn[] dataColumns, final boolean hasHeader, final List<ValidationResult> results) throws IOException {
        final int numOfCols = dataColumns.length;
        int numOfRows = 0;
        try (final InputStream in = Files.newInputStream(this.dataFile, StandardOpenOption.READ)) {
            boolean skipHeader = hasHeader;
            boolean skip = false;
            boolean hasSeenNonblankChar = false;
            boolean hasQuoteChar = false;

            final byte delimChar = this.delimiter.getByteValue();

            // comment marker check
            final byte[] comment = this.commentMarker.getBytes();
            int cmntIndex = 0;
            boolean checkForComment = comment.length > 0;

            int colNum = 0;
            int lineNum = 1;

            int columnIndex = 0;

            final StringBuilder dataBuilder = new StringBuilder();
            byte prevChar = -1;
            final byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !Thread.currentThread().isInterrupted()) {
                int i = 0; // buffer array index

                if (skipHeader) {
                    boolean finished = false;
                    for (; i < len && !finished && !Thread.currentThread().isInterrupted(); i++) {
                        final byte currChar = buffer[i];

                        if (currChar == CARRIAGE_RETURN || currChar == LINE_FEED) {
                            if (currChar == LINE_FEED && prevChar == CARRIAGE_RETURN) {
                                prevChar = currChar;
                                continue;
                            }

                            finished = hasSeenNonblankChar && !skip;
                            if (finished) {
                                skipHeader = false;
                            }

                            lineNum++;

                            // reset states
                            skip = false;
                            hasSeenNonblankChar = false;
                            cmntIndex = 0;
                            checkForComment = comment.length > 0;
                        } else if (!skip) {
                            if (currChar > SPACE_CHAR) {
                                hasSeenNonblankChar = true;
                            }

                            // skip blank chars at the begining of the line
                            if (currChar <= SPACE_CHAR && !hasSeenNonblankChar) {
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
                        }

                        prevChar = currChar;
                    }
                }

                for (; i < len && !Thread.currentThread().isInterrupted(); i++) {
                    final byte currChar = buffer[i];

                    if (currChar == CARRIAGE_RETURN || currChar == LINE_FEED) {
                        if (currChar == LINE_FEED && prevChar == CARRIAGE_RETURN) {
                            prevChar = currChar;
                            continue;
                        }

                        if (hasSeenNonblankChar && !skip) {
                            colNum++;

                            final DataColumn dataColumn = dataColumns[columnIndex];
                            if (dataColumn.getColumnNumber() == colNum) {
                                final String value = dataBuilder.toString().trim();
                                if (value.isEmpty()) {
                                    final String errMsg = String.format("Line %d, column %d: Missing value.  No missing marker was found. Assumed value is missing.", lineNum, colNum);
                                    final ValidationResult result = new ValidationResult(ValidationCode.WARNING, MessageType.FILE_MISSING_VALUE, errMsg);
                                    result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                    result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                    results.add(result);
                                }

                                columnIndex++;
                            }

                            // ensure we have enough data
                            if (columnIndex < numOfCols) {
                                final int numOfValues = columnIndex + 1;
                                final String errMsg = String.format(
                                        "Line %d, column %d: Insufficient data.  Expect %d value(s) but encounter %d.",
                                        lineNum, colNum, numOfCols, numOfValues);
                                final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_INSUFFICIENT_DATA, errMsg);
                                result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                result.setAttribute(ValidationAttribute.EXPECTED_COUNT, numOfCols);
                                result.setAttribute(ValidationAttribute.ACTUAL_COUNT, numOfValues);
                                results.add(result);
                            }

                            numOfRows++;
                        }

                        lineNum++;

                        // clear data
                        dataBuilder.delete(0, dataBuilder.length());

                        // reset states
                        skip = false;
                        hasSeenNonblankChar = false;
                        cmntIndex = 0;
                        checkForComment = comment.length > 0;
                        columnIndex = 0;
                        colNum = 0;
                    } else if (!skip) {
                        if (currChar > SPACE_CHAR) {
                            hasSeenNonblankChar = true;
                        }

                        // skip blank chars at the begining of the line
                        if (currChar <= SPACE_CHAR && !hasSeenNonblankChar) {
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
                        } else {
                            if (hasQuoteChar) {
                                dataBuilder.append((char) currChar);
                            } else {
                                final boolean isDelimiter;
                                switch (this.delimiter) {
                                    case WHITESPACE:
                                        isDelimiter = (currChar <= SPACE_CHAR) && (prevChar > SPACE_CHAR);
                                        break;
                                    default:
                                        isDelimiter = (currChar == delimChar);
                                }

                                if (isDelimiter) {
                                    colNum++;

                                    final DataColumn dataColumn = dataColumns[columnIndex];
                                    if (dataColumn.getColumnNumber() == colNum) {
                                        final String value = dataBuilder.toString().trim();
                                        if (value.isEmpty()) {
                                            final String errMsg = String.format("Line %d, column %d: Missing value.  No missing marker was found. Assumed value is missing.", lineNum, colNum);
                                            final ValidationResult result = new ValidationResult(ValidationCode.WARNING, MessageType.FILE_MISSING_VALUE, errMsg);
                                            result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                            result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                            results.add(result);
                                        }

                                        columnIndex++;
                                        if (columnIndex == numOfCols) {
                                            skip = true;
                                            numOfRows++;
                                        }
                                    }

                                    // clear data
                                    dataBuilder.delete(0, dataBuilder.length());
                                } else {
                                    dataBuilder.append((char) currChar);
                                }
                            }
                        }
                    }

                    prevChar = currChar;
                }
            }

            if (!skipHeader && hasSeenNonblankChar && !skip) {
                colNum++;

                final DataColumn dataColumn = dataColumns[columnIndex];
                if (dataColumn.getColumnNumber() == colNum) {
                    final String value = dataBuilder.toString().trim();
                    if (value.isEmpty()) {
                        final String errMsg = String.format("Line %d, column %d: Missing value.  No missing marker was found. Assumed value is missing.", lineNum, colNum);
                        final ValidationResult result = new ValidationResult(ValidationCode.WARNING, MessageType.FILE_MISSING_VALUE, errMsg);
                        result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                        result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                        results.add(result);
                    }

                    columnIndex++;
                }

                // ensure we have enough data
                if (columnIndex < numOfCols) {
                    final int numOfValues = columnIndex + 1;
                    final String errMsg = String.format(
                            "Line %d, column %d: Insufficient data.  Expect %d value(s) but encounter %d.",
                            lineNum, colNum, numOfCols, numOfValues);
                    final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_INSUFFICIENT_DATA, errMsg);
                    result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                    result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                    result.setAttribute(ValidationAttribute.EXPECTED_COUNT, numOfCols);
                    result.setAttribute(ValidationAttribute.ACTUAL_COUNT, numOfValues);
                    results.add(result);
                }

                numOfRows++;
            }
        }

        final String infoMsg = String.format("There are %d cases and %d variables.", numOfRows, numOfCols);
        final ValidationResult result = new ValidationResult(ValidationCode.INFO, MessageType.FILE_SUMMARY, infoMsg);
        result.setAttribute(ValidationAttribute.ROW_NUMBER, numOfRows);
        result.setAttribute(ValidationAttribute.COLUMN_NUMBER, numOfCols);
        results.add(result);
    }

    private void validateContinuousData(final DataColumn[] dataColumns, final boolean hasHeader, final List<ValidationResult> results) throws IOException {
        final int numOfCols = dataColumns.length;
        int numOfRows = 0;
        try (final InputStream in = Files.newInputStream(this.dataFile, StandardOpenOption.READ)) {
            boolean skipHeader = hasHeader;
            boolean skip = false;
            boolean hasSeenNonblankChar = false;
            boolean hasQuoteChar = false;

            final byte delimChar = this.delimiter.getByteValue();

            // comment marker check
            final byte[] comment = this.commentMarker.getBytes();
            int cmntIndex = 0;
            boolean checkForComment = comment.length > 0;

            int colNum = 0;
            int lineNum = 1;

            int columnIndex = 0;

            final StringBuilder dataBuilder = new StringBuilder();
            byte prevChar = -1;
            final byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !Thread.currentThread().isInterrupted()) {
                int i = 0; // buffer array index

                if (skipHeader) {
                    boolean finished = false;
                    for (; i < len && !finished && !Thread.currentThread().isInterrupted(); i++) {
                        final byte currChar = buffer[i];

                        if (currChar == CARRIAGE_RETURN || currChar == LINE_FEED) {
                            if (currChar == LINE_FEED && prevChar == CARRIAGE_RETURN) {
                                prevChar = currChar;
                                continue;
                            }

                            finished = hasSeenNonblankChar && !skip;
                            if (finished) {
                                skipHeader = false;
                            }

                            lineNum++;

                            // reset states
                            skip = false;
                            hasSeenNonblankChar = false;
                            cmntIndex = 0;
                            checkForComment = comment.length > 0;
                        } else if (!skip) {
                            if (currChar > SPACE_CHAR) {
                                hasSeenNonblankChar = true;
                            }

                            // skip blank chars at the begining of the line
                            if (currChar <= SPACE_CHAR && !hasSeenNonblankChar) {
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
                        }

                        prevChar = currChar;
                    }
                }

                for (; i < len && !Thread.currentThread().isInterrupted(); i++) {
                    final byte currChar = buffer[i];

                    if (currChar == CARRIAGE_RETURN || currChar == LINE_FEED) {
                        if (currChar == LINE_FEED && prevChar == CARRIAGE_RETURN) {
                            prevChar = currChar;
                            continue;
                        }

                        if (hasSeenNonblankChar && !skip) {
                            colNum++;

                            final DataColumn dataColumn = dataColumns[columnIndex];
                            if (dataColumn.getColumnNumber() == colNum) {
                                final String value = dataBuilder.toString().trim();
                                if (value.isEmpty()) {
                                    final String errMsg = String.format("Line %d, column %d: Missing value.  No missing marker was found. Assumed value is missing.", lineNum, colNum);
                                    final ValidationResult result = new ValidationResult(ValidationCode.WARNING, MessageType.FILE_MISSING_VALUE, errMsg);
                                    result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                    result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                    results.add(result);
                                } else if (!value.equals(this.missingDataMarker)) {
                                    try {
                                        Double.parseDouble(value);
                                    } catch (final NumberFormatException exception) {
                                        final String errMsg = String.format("Line %d, column %d: Non-continuous number %s.", lineNum, colNum, value);
                                        final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_INVALID_NUMBER, errMsg);
                                        result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                        result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                        result.setAttribute(ValidationAttribute.VALUE, value);
                                        results.add(result);
                                    }
                                }

                                columnIndex++;
                            }

                            // ensure we have enough data
                            if (columnIndex < numOfCols) {
                                final int numOfValues = columnIndex + 1;
                                final String errMsg = String.format(
                                        "Line %d, column %d: Insufficient data.  Expect %d value(s) but encounter %d.",
                                        lineNum, colNum, numOfCols, numOfValues);
                                final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_INSUFFICIENT_DATA, errMsg);
                                result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                result.setAttribute(ValidationAttribute.EXPECTED_COUNT, numOfCols);
                                result.setAttribute(ValidationAttribute.ACTUAL_COUNT, numOfValues);
                                results.add(result);
                            }

                            numOfRows++;
                        }

                        lineNum++;

                        // clear data
                        dataBuilder.delete(0, dataBuilder.length());

                        // reset states
                        skip = false;
                        hasSeenNonblankChar = false;
                        cmntIndex = 0;
                        checkForComment = comment.length > 0;
                        columnIndex = 0;
                        colNum = 0;
                    } else if (!skip) {
                        if (currChar > SPACE_CHAR) {
                            hasSeenNonblankChar = true;
                        }

                        // skip blank chars at the begining of the line
                        if (currChar <= SPACE_CHAR && !hasSeenNonblankChar) {
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
                        } else {
                            if (hasQuoteChar) {
                                dataBuilder.append((char) currChar);
                            } else {
                                final boolean isDelimiter;
                                switch (this.delimiter) {
                                    case WHITESPACE:
                                        isDelimiter = (currChar <= SPACE_CHAR) && (prevChar > SPACE_CHAR);
                                        break;
                                    default:
                                        isDelimiter = (currChar == delimChar);
                                }

                                if (isDelimiter) {
                                    colNum++;

                                    final DataColumn dataColumn = dataColumns[columnIndex];
                                    if (dataColumn.getColumnNumber() == colNum) {
                                        final String value = dataBuilder.toString().trim();
                                        if (value.isEmpty()) {
                                            final String errMsg = String.format("Line %d, column %d: Missing value.  No missing marker was found. Assumed value is missing.", lineNum, colNum);
                                            final ValidationResult result = new ValidationResult(ValidationCode.WARNING, MessageType.FILE_MISSING_VALUE, errMsg);
                                            result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                            result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                            results.add(result);
                                        } else if (!value.equals(this.missingDataMarker)) {
                                            try {
                                                Double.parseDouble(value);
                                            } catch (final NumberFormatException exception) {
                                                final String errMsg = String.format("Line %d, column %d: Non-continuous number %s.", lineNum, colNum, value);
                                                final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_INVALID_NUMBER, errMsg);
                                                result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                                result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                                result.setAttribute(ValidationAttribute.VALUE, value);
                                                results.add(result);
                                            }
                                        }

                                        columnIndex++;
                                        if (columnIndex == numOfCols) {
                                            skip = true;
                                            numOfRows++;
                                        }
                                    }

                                    // clear data
                                    dataBuilder.delete(0, dataBuilder.length());
                                } else {
                                    dataBuilder.append((char) currChar);
                                }
                            }
                        }
                    }

                    prevChar = currChar;
                }
            }

            if (!skipHeader && hasSeenNonblankChar && !skip) {
                colNum++;

                final DataColumn dataColumn = dataColumns[columnIndex];
                if (dataColumn.getColumnNumber() == colNum) {
                    final String value = dataBuilder.toString().trim();
                    if (value.isEmpty()) {
                        final String errMsg = String.format("Line %d, column %d: Missing value.  No missing marker was found. Assumed value is missing.", lineNum, colNum);
                        final ValidationResult result = new ValidationResult(ValidationCode.WARNING, MessageType.FILE_MISSING_VALUE, errMsg);
                        result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                        result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                        results.add(result);
                    } else if (!value.equals(this.missingDataMarker)) {
                        try {
                            Double.parseDouble(value);
                        } catch (final NumberFormatException exception) {
                            final String errMsg = String.format("Line %d, column %d: Non-continuous number %s.", lineNum, colNum, value);
                            final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_INVALID_NUMBER, errMsg);
                            result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                            result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                            result.setAttribute(ValidationAttribute.VALUE, value);
                            results.add(result);
                        }
                    }

                    columnIndex++;
                }

                // ensure we have enough data
                if (columnIndex < numOfCols) {
                    final int numOfValues = columnIndex + 1;
                    final String errMsg = String.format(
                            "Line %d, column %d: Insufficient data.  Expect %d value(s) but encounter %d.",
                            lineNum, colNum, numOfCols, numOfValues);
                    final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_INSUFFICIENT_DATA, errMsg);
                    result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                    result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                    result.setAttribute(ValidationAttribute.EXPECTED_COUNT, numOfCols);
                    result.setAttribute(ValidationAttribute.ACTUAL_COUNT, numOfValues);
                    results.add(result);
                }

                numOfRows++;
            }
        }

        final String infoMsg = String.format("There are %d cases and %d variables.", numOfRows, numOfCols);
        final ValidationResult result = new ValidationResult(ValidationCode.INFO, MessageType.FILE_SUMMARY, infoMsg);
        result.setAttribute(ValidationAttribute.ROW_NUMBER, numOfRows);
        result.setAttribute(ValidationAttribute.COLUMN_NUMBER, numOfCols);
        results.add(result);
    }

    private void validateMixedData(final DataColumn[] dataColumns, final boolean hasHeader, final List<ValidationResult> results) throws IOException {
        final int numOfCols = dataColumns.length;
        int numOfRows = 0;
        try (final InputStream in = Files.newInputStream(this.dataFile, StandardOpenOption.READ)) {
            boolean skipHeader = hasHeader;
            boolean skip = false;
            boolean hasSeenNonblankChar = false;
            boolean hasQuoteChar = false;

            final byte delimChar = this.delimiter.getByteValue();

            // comment marker check
            final byte[] comment = this.commentMarker.getBytes();
            int cmntIndex = 0;
            boolean checkForComment = comment.length > 0;

            int colNum = 0;
            int lineNum = 1;

            int columnIndex = 0;

            final StringBuilder dataBuilder = new StringBuilder();
            byte prevChar = -1;
            final byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !Thread.currentThread().isInterrupted()) {
                int i = 0; // buffer array index

                if (skipHeader) {
                    boolean finished = false;
                    for (; i < len && !finished && !Thread.currentThread().isInterrupted(); i++) {
                        final byte currChar = buffer[i];

                        if (currChar == CARRIAGE_RETURN || currChar == LINE_FEED) {
                            if (currChar == LINE_FEED && prevChar == CARRIAGE_RETURN) {
                                prevChar = currChar;
                                continue;
                            }

                            finished = hasSeenNonblankChar && !skip;
                            if (finished) {
                                skipHeader = false;
                            }

                            lineNum++;

                            // reset states
                            skip = false;
                            hasSeenNonblankChar = false;
                            cmntIndex = 0;
                            checkForComment = comment.length > 0;
                        } else if (!skip) {
                            if (currChar > SPACE_CHAR) {
                                hasSeenNonblankChar = true;
                            }

                            // skip blank chars at the begining of the line
                            if (currChar <= SPACE_CHAR && !hasSeenNonblankChar) {
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
                        }

                        prevChar = currChar;
                    }
                }

                for (; i < len && !Thread.currentThread().isInterrupted(); i++) {
                    final byte currChar = buffer[i];

                    if (currChar == CARRIAGE_RETURN || currChar == LINE_FEED) {
                        if (currChar == LINE_FEED && prevChar == CARRIAGE_RETURN) {
                            prevChar = currChar;
                            continue;
                        }

                        if (hasSeenNonblankChar && !skip) {
                            colNum++;

                            final DataColumn dataColumn = dataColumns[columnIndex];
                            if (dataColumn.getColumnNumber() == colNum) {
                                final String value = dataBuilder.toString().trim();
                                if (value.isEmpty()) {
                                    final String errMsg = String.format("Line %d, column %d: Missing value.  No missing marker was found. Assumed value is missing.", lineNum, colNum);
                                    final ValidationResult result = new ValidationResult(ValidationCode.WARNING, MessageType.FILE_MISSING_VALUE, errMsg);
                                    result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                    result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                    results.add(result);
                                } else if (!value.equals(this.missingDataMarker)) {
                                    if (!dataColumn.isDiscrete()) {
                                        try {
                                            Double.parseDouble(value);
                                        } catch (final NumberFormatException exception) {
                                            final String errMsg = String.format("Line %d, column %d: Non-continuous number %s.", lineNum, colNum, value);
                                            final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_INVALID_NUMBER, errMsg);
                                            result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                            result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                            result.setAttribute(ValidationAttribute.VALUE, value);
                                            results.add(result);
                                        }
                                    }
                                }

                                columnIndex++;
                            }

                            // ensure we have enough data
                            if (columnIndex < numOfCols) {
                                final int numOfValues = columnIndex + 1;
                                final String errMsg = String.format(
                                        "Line %d, column %d: Insufficient data.  Expect %d value(s) but encounter %d.",
                                        lineNum, colNum, numOfCols, numOfValues);
                                final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_INSUFFICIENT_DATA, errMsg);
                                result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                result.setAttribute(ValidationAttribute.EXPECTED_COUNT, numOfCols);
                                result.setAttribute(ValidationAttribute.ACTUAL_COUNT, numOfValues);
                                results.add(result);
                            }

                            numOfRows++;
                        }

                        lineNum++;

                        // clear data
                        dataBuilder.delete(0, dataBuilder.length());

                        // reset states
                        skip = false;
                        hasSeenNonblankChar = false;
                        cmntIndex = 0;
                        checkForComment = comment.length > 0;
                        columnIndex = 0;
                        colNum = 0;
                    } else if (!skip) {
                        if (currChar > SPACE_CHAR) {
                            hasSeenNonblankChar = true;
                        }

                        // skip blank chars at the begining of the line
                        if (currChar <= SPACE_CHAR && !hasSeenNonblankChar) {
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
                        } else {
                            if (hasQuoteChar) {
                                dataBuilder.append((char) currChar);
                            } else {
                                final boolean isDelimiter;
                                switch (this.delimiter) {
                                    case WHITESPACE:
                                        isDelimiter = (currChar <= SPACE_CHAR) && (prevChar > SPACE_CHAR);
                                        break;
                                    default:
                                        isDelimiter = (currChar == delimChar);
                                }

                                if (isDelimiter) {
                                    colNum++;

                                    final DataColumn dataColumn = dataColumns[columnIndex];
                                    if (dataColumn.getColumnNumber() == colNum) {
                                        final String value = dataBuilder.toString().trim();
                                        if (value.isEmpty()) {
                                            final String errMsg = String.format("Line %d, column %d: Missing value.  No missing marker was found. Assumed value is missing.", lineNum, colNum);
                                            final ValidationResult result = new ValidationResult(ValidationCode.WARNING, MessageType.FILE_MISSING_VALUE, errMsg);
                                            result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                            result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                            results.add(result);
                                        } else if (!value.equals(this.missingDataMarker)) {
                                            if (!dataColumn.isDiscrete()) {
                                                try {
                                                    Double.parseDouble(value);
                                                } catch (final NumberFormatException exception) {
                                                    final String errMsg = String.format("Line %d, column %d: Non-continuous number %s.", lineNum, colNum, value);
                                                    final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_INVALID_NUMBER, errMsg);
                                                    result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                                    result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                                    result.setAttribute(ValidationAttribute.VALUE, value);
                                                    results.add(result);
                                                }
                                            }
                                        }

                                        columnIndex++;
                                        if (columnIndex == numOfCols) {
                                            skip = true;
                                            numOfRows++;
                                        }
                                    }

                                    // clear data
                                    dataBuilder.delete(0, dataBuilder.length());
                                } else {
                                    dataBuilder.append((char) currChar);
                                }
                            }
                        }
                    }

                    prevChar = currChar;
                }
            }

            if (!skipHeader && hasSeenNonblankChar && !skip) {
                colNum++;

                final DataColumn dataColumn = dataColumns[columnIndex];
                if (dataColumn.getColumnNumber() == colNum) {
                    final String value = dataBuilder.toString().trim();
                    if (value.isEmpty()) {
                        final String errMsg = String.format("Line %d, column %d: Missing value.  No missing marker was found. Assumed value is missing.", lineNum, colNum);
                        final ValidationResult result = new ValidationResult(ValidationCode.WARNING, MessageType.FILE_MISSING_VALUE, errMsg);
                        result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                        result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                        results.add(result);
                    } else if (!value.equals(this.missingDataMarker)) {
                        if (!dataColumn.isDiscrete()) {
                            try {
                                Double.parseDouble(value);
                            } catch (final NumberFormatException exception) {
                                final String errMsg = String.format("Line %d, column %d: Non-continuous number %s.", lineNum, colNum, value);
                                final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_INVALID_NUMBER, errMsg);
                                result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                result.setAttribute(ValidationAttribute.VALUE, value);
                                results.add(result);
                            }
                        }
                    }

                    columnIndex++;
                }

                // ensure we have enough data
                if (columnIndex < numOfCols) {
                    final int numOfValues = columnIndex + 1;
                    final String errMsg = String.format(
                            "Line %d, column %d: Insufficient data.  Expect %d value(s) but encounter %d.",
                            lineNum, colNum, numOfCols, numOfValues);
                    final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_INSUFFICIENT_DATA, errMsg);
                    result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                    result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                    result.setAttribute(ValidationAttribute.EXPECTED_COUNT, numOfCols);
                    result.setAttribute(ValidationAttribute.ACTUAL_COUNT, numOfValues);
                    results.add(result);
                }

                numOfRows++;
            }
        }

        final String infoMsg = String.format("There are %d cases and %d variables.", numOfRows, numOfCols);
        final ValidationResult result = new ValidationResult(ValidationCode.INFO, MessageType.FILE_SUMMARY, infoMsg);
        result.setAttribute(ValidationAttribute.ROW_NUMBER, numOfRows);
        result.setAttribute(ValidationAttribute.COLUMN_NUMBER, numOfCols);
        results.add(result);
    }

    @Override
    public void setMaximumNumberOfMessages(final int maxNumOfMsg) {
        this.maxNumOfMsg = maxNumOfMsg;
    }

}
