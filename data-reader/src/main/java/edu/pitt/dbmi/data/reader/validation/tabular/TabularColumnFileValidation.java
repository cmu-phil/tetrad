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

import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.AbstractTabularColumnFileReader;
import edu.pitt.dbmi.data.reader.util.Columns;
import edu.pitt.dbmi.data.reader.validation.MessageType;
import edu.pitt.dbmi.data.reader.validation.ValidationAttribute;
import edu.pitt.dbmi.data.reader.validation.ValidationCode;
import edu.pitt.dbmi.data.reader.validation.ValidationResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Dec 12, 2018 3:28:26 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TabularColumnFileValidation extends AbstractTabularColumnFileReader implements TabularColumnValidation {

    private int maxNumOfMsg;

    public TabularColumnFileValidation(final Path dataFile, final Delimiter delimiter) {
        super(dataFile, delimiter);
        this.maxNumOfMsg = Integer.MAX_VALUE;
    }

    @Override
    public List<ValidationResult> validate() {
        return validate(Collections.EMPTY_SET);
    }

    @Override
    public List<ValidationResult> validate(final int[] excludedColumns) {
        final List<ValidationResult> results = new LinkedList<>();

        try {
            final int numOfCols = countNumberOfColumns();
            final int[] excludedCols = Columns.sortNew(excludedColumns);
            final int[] validCols = Columns.extractValidColumnNumbers(numOfCols, excludedCols);

            validateColumns(validCols, results);
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

    @Override
    public List<ValidationResult> validate(final Set<String> excludedColumns) {
        final List<ValidationResult> results = new LinkedList<>();

        try {
            if (excludedColumns == null || excludedColumns.isEmpty()) {
                validateColumns(new int[0], results);
            } else {
                final Set<String> modifiedExcludedCols = new HashSet<>();
                if (Character.isDefined(this.quoteCharacter)) {
                    excludedColumns.stream()
                            .map(e -> e.trim())
                            .filter(e -> !e.isEmpty())
                            .forEach(e -> modifiedExcludedCols.add(stripCharacter(e, this.quoteCharacter)));
                } else {
                    excludedColumns.stream()
                            .map(e -> e.trim())
                            .filter(e -> !e.isEmpty())
                            .forEach(e -> modifiedExcludedCols.add(e));
                }

                final int[] excludedCols = toColumnNumbers(modifiedExcludedCols);

                validateColumns(excludedCols, results);
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

    private void validateColumns(final int[] excludedColumns, final List<ValidationResult> results) throws IOException {
        int numOfVars = 0;

        try (final InputStream in = Files.newInputStream(this.dataFile, StandardOpenOption.READ)) {
            boolean skip = false;
            boolean hasSeenNonblankChar = false;
            boolean hasQuoteChar = false;
            boolean finished = false;

            final byte delimChar = this.delimiter.getByteValue();
            byte prevChar = -1;

            // comment marker check
            final byte[] comment = this.commentMarker.getBytes();
            int cmntIndex = 0;
            boolean checkForComment = comment.length > 0;

            // excluded columns check
            final int numOfExCols = excludedColumns.length;
            int exColsIndex = 0;

            int colNum = 0;
            int lineNum = 1;
            final StringBuilder dataBuilder = new StringBuilder();

            final byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !finished && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < len && !finished && !Thread.currentThread().isInterrupted(); i++) {
                    final byte currChar = buffer[i];

                    if (currChar == CARRIAGE_RETURN || currChar == LINE_FEED) {
                        finished = hasSeenNonblankChar && !skip;
                        if (finished) {
                            final String value = dataBuilder.toString().trim();
                            dataBuilder.delete(0, dataBuilder.length());

                            colNum++;
                            if (numOfExCols == 0 || exColsIndex >= numOfExCols || colNum != excludedColumns[exColsIndex]) {
                                numOfVars++;
                                if (value.isEmpty()) {
                                    final String errMsg = String.format("Line %d, column %d: Missing variable name.", lineNum, colNum);
                                    final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_MISSING_VALUE, errMsg);
                                    result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                    result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                    results.add(result);
                                }
                            }
                        } else {
                            dataBuilder.delete(0, dataBuilder.length());
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
                                    final String value = dataBuilder.toString().trim();
                                    dataBuilder.delete(0, dataBuilder.length());

                                    colNum++;
                                    if (numOfExCols > 0 && (exColsIndex < numOfExCols && colNum == excludedColumns[exColsIndex])) {
                                        exColsIndex++;
                                    } else {
                                        numOfVars++;
                                        if (value.isEmpty()) {
                                            final String errMsg = String.format("Line %d, column %d: Missing variable name.", lineNum, colNum);
                                            final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_MISSING_VALUE, errMsg);
                                            result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                                            result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                                            results.add(result);
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

            finished = hasSeenNonblankChar && !skip;
            if (finished) {
                final String value = dataBuilder.toString().trim();
                dataBuilder.delete(0, dataBuilder.length());

                colNum++;
                if (numOfExCols == 0 || exColsIndex >= numOfExCols || colNum != excludedColumns[exColsIndex]) {
                    numOfVars++;
                    if (value.isEmpty()) {
                        final String errMsg = String.format("Line %d, column %d: Missing variable name.", lineNum, colNum);
                        final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_MISSING_VALUE, errMsg);
                        result.setAttribute(ValidationAttribute.COLUMN_NUMBER, colNum);
                        result.setAttribute(ValidationAttribute.LINE_NUMBER, lineNum);
                        results.add(result);
                    }
                }
            }
        }

        if (numOfVars <= 0) {
            final String errMsg = "No variable was read in.";
            final ValidationResult result = new ValidationResult(ValidationCode.ERROR, MessageType.FILE_MISSING_VALUE, errMsg);
            results.add(result);
        }

        final String infoMsg = String.format("There are %d variables.", numOfVars);
        final ValidationResult result = new ValidationResult(ValidationCode.INFO, MessageType.FILE_SUMMARY, infoMsg);
        results.add(result);
    }

    @Override
    public void setMaximumNumberOfMessages(final int maxNumOfMsg) {
        this.maxNumOfMsg = maxNumOfMsg;
    }

}
