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
package edu.pitt.dbmi.data.reader.covariance;

import edu.pitt.dbmi.data.reader.DataFileReader;
import edu.pitt.dbmi.data.reader.DataReaderException;
import edu.pitt.dbmi.data.reader.Delimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;

/**
 * Nov 19, 2018 11:04:51 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class LowerCovarianceDataFileReader extends DataFileReader implements CovarianceDataReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(LowerCovarianceDataFileReader.class);

    public LowerCovarianceDataFileReader(final Path dataFile, final Delimiter delimiter) {
        super(dataFile, delimiter);
    }

    @Override
    public CovarianceData readInData() throws IOException {
        final int numOfCases = getNumberOfCases();
        final List<String> variables = getVariables();
        final double[][] data = getCovarianceData(variables.size());

        return new LowerCovarianceData(numOfCases, variables, data);
    }

    private double[][] getCovarianceData(final int matrixSize) throws IOException {
        final double[][] data = new double[matrixSize][matrixSize];

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
            int col = 0;
            int row = 0;

            final StringBuilder dataBuilder = new StringBuilder();
            byte prevChar = -1;
            final byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < len && !Thread.currentThread().isInterrupted(); i++) {
                    final byte currChar = buffer[i];

                    if (currChar == CARRIAGE_RETURN || currChar == LINE_FEED) {
                        if (currChar == LINE_FEED && prevChar == CARRIAGE_RETURN) {
                            prevChar = currChar;
                            continue;
                        }

                        if (hasSeenNonblankChar && !skip) {
                            if (lineDataNum >= 3) {
                                if (col > row) {
                                    final String errMsg = String.format("Excess data on line %d.  Extracted %d value(s) but expected %d.", lineNum, col + 1, row + 1);
                                    LOGGER.error(errMsg);
                                    throw new DataReaderException(errMsg);
                                } else if (col < row) {
                                    final String errMsg = String.format("Insufficent data on line %d.  Extracted %d value(s) but expected %d.", lineNum, col + 1, row + 1);
                                    LOGGER.error(errMsg);
                                    throw new DataReaderException(errMsg);
                                } else {
                                    final String value = dataBuilder.toString().trim();
                                    dataBuilder.delete(0, dataBuilder.length());

                                    colNum++;
                                    if (value.isEmpty()) {
                                        final String errMsg = String.format("Missing value on line %d at column %d.", lineNum, colNum);
                                        LOGGER.error(errMsg);
                                        throw new DataReaderException(errMsg);
                                    } else {
                                        try {
                                            final double covariance = Double.parseDouble(value);
                                            data[row][col] = covariance;
                                            data[col][row] = covariance;
                                        } catch (final NumberFormatException exception) {
                                            final String errMsg = String.format("Invalid number %s on line %d at column %d.", value, lineNum, colNum);
                                            LOGGER.error(errMsg, exception);
                                            throw new DataReaderException(errMsg);
                                        }
                                    }
                                }

                                row++;
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
                        col = 0;
                        colNum = 0;
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

                        if (lineDataNum >= 3) {
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
                                        if (col > row) {
                                            final String errMsg = String.format("Excess data on line %d.  Extracted %d value(s) but expected %d.", lineNum, col + 1, row + 1);
                                            LOGGER.error(errMsg);
                                            throw new DataReaderException(errMsg);
                                        }

                                        final String value = dataBuilder.toString().trim();
                                        dataBuilder.delete(0, dataBuilder.length());

                                        colNum++;
                                        if (value.isEmpty()) {
                                            final String errMsg = String.format("Missing value on line %d at column %d.", lineNum, colNum);
                                            LOGGER.error(errMsg);
                                            throw new DataReaderException(errMsg);
                                        } else {
                                            try {
                                                final double covariance = Double.parseDouble(value);
                                                data[row][col] = covariance;
                                                data[col][row] = covariance;
                                            } catch (final NumberFormatException exception) {
                                                final String errMsg = String.format("Invalid number %s on line %d at column %d.", value, lineNum, colNum);
                                                LOGGER.error(errMsg, exception);
                                                throw new DataReaderException(errMsg);
                                            }
                                        }

                                        col++;
                                    } else {
                                        dataBuilder.append((char) currChar);
                                    }
                                }
                            }
                        }
                    }

                    prevChar = currChar;
                }
            }

            if (hasSeenNonblankChar && !skip) {
                if (lineDataNum >= 3) {
                    if (col > row) {
                        final String errMsg = String.format("Excess data on line %d.  Extracted %d value(s) but expected %d.", lineNum, col + 1, row + 1);
                        LOGGER.error(errMsg);
                        throw new DataReaderException(errMsg);
                    } else if (col < row) {
                        final String errMsg = String.format("Insufficent data on line %d.  Extracted %d value(s) but expected %d.", lineNum, col + 1, row + 1);
                        LOGGER.error(errMsg);
                        throw new DataReaderException(errMsg);
                    } else {
                        final String value = dataBuilder.toString().trim();
                        dataBuilder.delete(0, dataBuilder.length());

                        colNum++;
                        if (value.isEmpty()) {
                            final String errMsg = String.format("Missing value on line %d at column %d.", lineNum, colNum);
                            LOGGER.error(errMsg);
                            throw new DataReaderException(errMsg);
                        } else {
                            try {
                                final double covariance = Double.parseDouble(value);
                                data[row][col] = covariance;
                                data[col][row] = covariance;
                            } catch (final NumberFormatException exception) {
                                final String errMsg = String.format("Invalid number %s on line %d at column %d.", value, lineNum, colNum);
                                LOGGER.error(errMsg, exception);
                                throw new DataReaderException(errMsg);
                            }
                        }
                    }
                }
            }
        }

        return data;
    }

    private List<String> getVariables() throws IOException {
        final List<String> variables = new LinkedList<>();

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
            final byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !finished && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < len && !finished && !Thread.currentThread().isInterrupted(); i++) {
                    final byte currChar = buffer[i];

                    if (currChar == CARRIAGE_RETURN || currChar == LINE_FEED) {
                        if (currChar == LINE_FEED && prevChar == CARRIAGE_RETURN) {
                            prevChar = currChar;
                            continue;
                        }

                        if (hasSeenNonblankChar && !skip) {
                            if (lineDataNum == 2) {
                                final String value = dataBuilder.toString().trim();
                                dataBuilder.delete(0, dataBuilder.length());

                                colNum++;
                                if (value.isEmpty()) {
                                    final String errMsg = String.format("Missing variable name on line %d at column %d.", lineNum, colNum);
                                    LOGGER.error(errMsg);
                                    throw new DataReaderException(errMsg);
                                } else {
                                    variables.add(value);
                                }
                            }

                            lineDataNum++;
                            finished = lineDataNum > 2;
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

                        if (lineDataNum == 2) {
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
                                        if (value.isEmpty()) {
                                            final String errMsg = String.format("Missing variable name on line %d at column %d.", lineNum, colNum);
                                            LOGGER.error(errMsg);
                                            throw new DataReaderException(errMsg);
                                        } else {
                                            variables.add(value);
                                        }
                                    } else {
                                        dataBuilder.append((char) currChar);
                                    }
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
                        final String errMsg = String.format("Missing variable name on line %d at column %d.", lineNum, colNum);
                        LOGGER.error(errMsg);
                        throw new DataReaderException(errMsg);
                    } else {
                        variables.add(value);
                    }
                }
            }
        }

        if (variables.isEmpty()) {
            final String errMsg = "Covariance file does not contain variable names.";
            LOGGER.error(errMsg);
            throw new DataReaderException(errMsg);
        }

        return variables;
    }

    private int getNumberOfCases() throws IOException {
        int numOfCases = 0;

        try (final InputStream in = Files.newInputStream(this.dataFile, StandardOpenOption.READ)) {
            boolean skip = false;
            boolean hasSeenNonblankChar = false;
            boolean finished = false;

            // comment marker check
            final byte[] comment = this.commentMarker.getBytes();
            int cmntIndex = 0;
            boolean checkForComment = comment.length > 0;

            int lineNum = 1;

            final StringBuilder dataBuilder = new StringBuilder();
            byte prevChar = -1;
            final byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !finished && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < len && !finished && !Thread.currentThread().isInterrupted(); i++) {
                    final byte currChar = buffer[i];

                    if (currChar == CARRIAGE_RETURN || currChar == LINE_FEED) {
                        if (currChar == LINE_FEED && prevChar == CARRIAGE_RETURN) {
                            prevChar = currChar;
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
                    } else {
                        if (!skip) {
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

                            if (currChar != this.quoteCharacter) {
                                dataBuilder.append((char) currChar);
                            }
                        }
                    }

                    prevChar = currChar;
                }
            }

            final String value = dataBuilder.toString().trim();
            if (value.isEmpty()) {
                final String errMsg = String.format("Line %d: Missing number of cases.", lineNum);
                LOGGER.error(errMsg);
                throw new DataReaderException(errMsg);
            } else {
                try {
                    numOfCases += Integer.parseInt(value);
                } catch (final NumberFormatException exception) {
                    final String errMsg = String.format("Invalid number %s on line %d.", value, lineNum);
                    LOGGER.error(errMsg);
                    throw new DataReaderException(errMsg);
                }
            }
        }

        return numOfCases;
    }

    private final class LowerCovarianceData implements CovarianceData {

        private final int numberOfCases;
        private final List<String> variables;
        private final double[][] data;

        private LowerCovarianceData(final int numberOfCases, final List<String> variables, final double[][] data) {
            this.numberOfCases = numberOfCases;
            this.variables = variables;
            this.data = data;
        }

        @Override
        public int getNumberOfCases() {
            return this.numberOfCases;
        }

        @Override
        public List<String> getVariables() {
            return this.variables;
        }

        @Override
        public double[][] getData() {
            return this.data;
        }

    }

}
