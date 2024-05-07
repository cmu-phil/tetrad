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
package edu.pitt.dbmi.data.reader.tabular;

import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.DataFileReader;
import edu.pitt.dbmi.data.reader.DataReaderException;
import edu.pitt.dbmi.data.reader.Delimiter;
import edu.pitt.dbmi.data.reader.util.Columns;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Reads in columns of tabular data from file.
 * <p>
 * Nov 7, 2018 2:24:23 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public final class TabularColumnFileReader extends AbstractTabularColumnFileReader implements TabularColumnReader {

    /**
     * Constructor.
     *
     * @param dataFile  The data file.
     * @param delimiter The delimiter.
     */
    public TabularColumnFileReader(Path dataFile, Delimiter delimiter) {
        super(dataFile, delimiter);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Reads in the data columns.
     */
    @Override
    public DataColumn[] readInDataColumns(boolean isDiscrete) throws IOException {
        return readInDataColumns(Collections.EMPTY_SET, isDiscrete);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Reads in the data columns.
     */
    @Override
    public DataColumn[] readInDataColumns(Set<String> namesOfColumnsToExclude, boolean isDiscrete) throws IOException {
        if (namesOfColumnsToExclude == null || namesOfColumnsToExclude.isEmpty()) {
            return getColumns(new int[0], isDiscrete);
        } else {
            Set<String> cleanedColumnNames = new HashSet<>();
            if (Character.isDefined(this.quoteCharacter)) {
                namesOfColumnsToExclude.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(e -> !e.isEmpty())
                        .map(e -> stripCharacter(e, this.quoteCharacter))
                        .forEach(cleanedColumnNames::add);
            } else {
                namesOfColumnsToExclude.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(e -> !e.isEmpty())
                        .forEach(cleanedColumnNames::add);
            }

            int[] columnsToExclude = toColumnNumbers(cleanedColumnNames);

            return getColumns(columnsToExclude, isDiscrete);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Reads in the data columns.
     */
    @Override
    public DataColumn[] readInDataColumns(int[] columnsToExclude, boolean isDiscrete) throws IOException {
        int numOfCols = countNumberOfColumns();
        int[] sortedColsToExclude = Columns.sortNew(columnsToExclude);
        int[] validColsToExclude = Columns.extractValidColumnNumbers(numOfCols, sortedColsToExclude);

        return getColumns(validColsToExclude, isDiscrete);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Generates the data columns.
     */
    @Override
    public DataColumn[] generateColumns(int[] columnsToExclude, boolean isDiscrete) throws IOException {
        List<DataColumn> columns = new LinkedList<>();

        int[] sortedColsToExclude = Columns.sortNew(columnsToExclude);
        int numOfCols = countNumberOfColumns();
        final String prefix = "C";
        int index = 0;
        for (int col = 1; col <= numOfCols && !Thread.currentThread().isInterrupted(); col++) {
            if (index < sortedColsToExclude.length && col == sortedColsToExclude[index]) {
                index++;
            } else {
                columns.add(new TabularDataColumn(prefix + col, col, false, isDiscrete));
            }
        }

        return columns.toArray(new DataColumn[0]);
    }

    private DataColumn[] getColumns(int[] columnsToExclude, boolean isDiscrete) throws IOException {
        List<DataColumn> columns = new LinkedList<>();

        try (InputStream in = Files.newInputStream(this.dataFile, StandardOpenOption.READ)) {
            boolean skip = false;
            boolean hasSeenNonblankChar = false;
            boolean hasQuoteChar = false;
            boolean finished = false;

            byte delimChar = this.delimiter.getByteValue();
            byte prevChar = -1;

            // comment marker check
            byte[] comment = this.commentMarker.getBytes();
            int cmntIndex = 0;
            boolean checkForComment = comment.length > 0;

            // excluded columns check
            int numOfExCols = columnsToExclude.length;
            int exColsIndex = 0;

            int colNum = 0;
            int lineNum = 1;
            StringBuilder dataBuilder = new StringBuilder();

            byte[] buffer = new byte[DataFileReader.BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !finished && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < len && !finished && !Thread.currentThread().isInterrupted(); i++) {
                    byte currChar = buffer[i];

                    if (currChar == DataFileReader.CARRIAGE_RETURN || currChar == DataFileReader.LINE_FEED) {
                        finished = hasSeenNonblankChar && !skip;
                        if (finished) {
                            String value = dataBuilder.toString().trim();
                            dataBuilder.delete(0, dataBuilder.length());

                            colNum++;
                            if (numOfExCols == 0 || exColsIndex >= numOfExCols || colNum != columnsToExclude[exColsIndex]) {
                                if (value.isEmpty()) {
                                    String errMsg = String.format("Missing variable name on line %d at column %d.", lineNum, colNum);
//                                    TabularColumnFileReader.LOGGER.error(errMsg);
                                    throw new DataReaderException(errMsg);
                                } else {
                                    columns.add(new TabularDataColumn(value, colNum, false, isDiscrete));
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
                        } else {
                            if (hasQuoteChar) {
                                dataBuilder.append((char) currChar);
                            } else {
                                boolean isDelimiter;
                                if (this.delimiter == Delimiter.WHITESPACE) {
                                    isDelimiter = (currChar <= DataFileReader.SPACE_CHAR) && (prevChar > DataFileReader.SPACE_CHAR);
                                } else {
                                    isDelimiter = (currChar == delimChar);
                                }

                                if (isDelimiter) {
                                    String value = dataBuilder.toString().trim();
                                    dataBuilder.delete(0, dataBuilder.length());

                                    colNum++;
                                    if (numOfExCols > 0 && (exColsIndex < numOfExCols && colNum == columnsToExclude[exColsIndex])) {
                                        exColsIndex++;
                                    } else {
                                        if (value.isEmpty()) {
                                            String errMsg = String.format("Missing variable name on line %d at column %d.", lineNum, colNum);
//                                            TabularColumnFileReader.LOGGER.error(errMsg);
                                            throw new DataReaderException(errMsg);
                                        } else {
                                            columns.add(new TabularDataColumn(value, colNum, false, isDiscrete));
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
                String value = dataBuilder.toString().trim();
                dataBuilder.delete(0, dataBuilder.length());

                colNum++;
                if (numOfExCols == 0 || exColsIndex >= numOfExCols || colNum != columnsToExclude[exColsIndex]) {
                    if (value.isEmpty()) {
                        String errMsg = String.format("Missing variable name on line %d at column %d.", lineNum, colNum);
//                        TabularColumnFileReader.LOGGER.error(errMsg);
                        throw new DataReaderException(errMsg);
                    } else {
                        columns.add(new TabularDataColumn(value, colNum, false, isDiscrete));
                    }
                }
            }
        }

        return columns.toArray(new DataColumn[0]);
    }

}
