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

import edu.pitt.dbmi.data.reader.*;
import edu.pitt.dbmi.data.reader.metadata.ColumnMetadata;
import edu.pitt.dbmi.data.reader.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Nov 15, 2018 5:22:50 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public final class TabularDataFileReader extends DatasetFileReader implements TabularDataReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(TabularDataFileReader.class);

    public TabularDataFileReader(final Path dataFile, final Delimiter delimiter) {
        super(dataFile, delimiter);
    }

    @Override
    public void determineDiscreteDataColumns(final DataColumn[] dataColumns, final int numberOfCategories, final boolean hasHeader) throws IOException {
        int numOfColsInDataFile = 0;
        for (final DataColumn dataColumn : dataColumns) {
            if (!dataColumn.isGenerated()) {
                numOfColsInDataFile++;
            }
        }

        final Set<String>[] columnCategories = new Set[numOfColsInDataFile];
        for (int i = 0; i < numOfColsInDataFile; i++) {
            columnCategories[i] = new HashSet<>();
        }

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

            final int maxCategoryToAdd = numberOfCategories + 1;

            final StringBuilder dataBuilder = new StringBuilder();
            byte prevChar = -1;
            final byte[] buffer = new byte[DataFileReader.BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !Thread.currentThread().isInterrupted()) {
                int i = 0; // buffer array index

                if (skipHeader) {
                    boolean finished = false;
                    for (; i < len && !finished && !Thread.currentThread().isInterrupted(); i++) {
                        final byte currChar = buffer[i];

                        if (currChar == DataFileReader.CARRIAGE_RETURN || currChar == DataFileReader.LINE_FEED) {
                            if (currChar == DataFileReader.LINE_FEED && prevChar == DataFileReader.CARRIAGE_RETURN) {
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
                        }

                        prevChar = currChar;
                    }
                }

                for (; i < len && !Thread.currentThread().isInterrupted(); i++) {
                    final byte currChar = buffer[i];

                    if (currChar == DataFileReader.CARRIAGE_RETURN || currChar == DataFileReader.LINE_FEED) {
                        if (currChar == DataFileReader.LINE_FEED && prevChar == DataFileReader.CARRIAGE_RETURN) {
                            prevChar = currChar;
                            continue;
                        }

                        if (hasSeenNonblankChar && !skip) {
                            colNum++;

                            final DataColumn dataColumn = dataColumns[columnIndex];
                            if (dataColumn.getColumnNumber() == colNum) {
                                final String value = dataBuilder.toString().trim();
                                if (!(value.isEmpty() || value.equals(this.missingDataMarker))) {
                                    final Set<String> categories = columnCategories[columnIndex];
                                    if (categories.size() < maxCategoryToAdd) {
                                        categories.add(value);
                                    }
                                }

                                columnIndex++;
                            }

                            // ensure we have enough data
                            if (columnIndex < numOfColsInDataFile) {
                                final String errMsg = String.format("Insufficient data on line %d.  Extracted %d value(s) but expected %d.", lineNum, columnIndex, numOfColsInDataFile);
                                TabularDataFileReader.LOGGER.error(errMsg);
                                throw new DataReaderException(errMsg);
                            }
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

                                    final DataColumn dataColumn = dataColumns[columnIndex];
                                    if (dataColumn.getColumnNumber() == colNum) {
                                        final String value = dataBuilder.toString().trim();
                                        if (!(value.isEmpty() || value.equals(this.missingDataMarker))) {
                                            final Set<String> categories = columnCategories[columnIndex];
                                            if (categories.size() < maxCategoryToAdd) {
                                                categories.add(value);
                                            }
                                        }

                                        columnIndex++;
                                        if (columnIndex == numOfColsInDataFile) {
                                            skip = true;
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
                    if (!(value.isEmpty() || value.equals(this.missingDataMarker))) {
                        final Set<String> categories = columnCategories[columnIndex];
                        if (categories.size() < maxCategoryToAdd) {
                            categories.add(value);
                        }
                    }

                    columnIndex++;
                }

                // ensure we have enough data
                if (columnIndex < numOfColsInDataFile) {
                    final String errMsg = String.format("Insufficient data on line %d.  Extracted %d value(s) but expected %d.", lineNum, columnIndex, numOfColsInDataFile);
                    TabularDataFileReader.LOGGER.error(errMsg);
                    throw new DataReaderException(errMsg);
                }
            }
        }

        for (int i = 0; i < numOfColsInDataFile; i++) {
            dataColumns[i].setDiscrete(columnCategories[i].size() <= numberOfCategories);
        }
    }

    @Override
    public Data read(final DataColumn[] dataColumns, final boolean hasHeader) throws IOException {
        if (dataColumns == null) {
            return null;
        }

        int numOfColsInDataFile = 0;
        boolean isDiscrete = false;
        boolean isContinuous = false;
        for (final DataColumn dataColumn : dataColumns) {
            if (dataColumn.isDiscrete()) {
                isDiscrete = true;
            } else {
                isContinuous = true;
            }

            if (!dataColumn.isGenerated()) {
                numOfColsInDataFile++;
            }
        }

        if (isDiscrete && isContinuous) {
            return readInMixedData(dataColumns, hasHeader, numOfColsInDataFile);
        } else if (isContinuous) {
            return readInContinuousData(dataColumns, hasHeader, numOfColsInDataFile);
        } else if (isDiscrete) {
            return readInDiscreteData(dataColumns, hasHeader, numOfColsInDataFile);
        } else {
            return null;
        }
    }

    @Override
    public Data read(final DataColumn[] dataColumns, final boolean hasHeader, final Metadata metadata) throws IOException {
        final Data data = read(dataColumns, hasHeader);

        if (metadata != null) {
            if (data instanceof ContinuousData) {
                final ContinuousData continuousData = (ContinuousData) data;
                final double[][] contData = continuousData.getData();
                metadata.getInterventionalColumns().forEach(column -> {
                    final ColumnMetadata valCol = column.getValueColumn();
                    final ColumnMetadata statCol = column.getStatusColumn();
                    final int valColNum = valCol.getColumnNumber() - 1;
                    final int statColNum = statCol.getColumnNumber() - 1;
                    final double[] val = contData[valColNum];
                    final double[] stat = contData[statColNum];
                    for (int i = 0; i < val.length; i++) {
                        if (Double.isNaN(val[i])) {
                            val[i] = 0.0;
                            stat[i] = 0.0;
                        } else if (dataColumns[statColNum].isGenerated()) {
                            stat[i] = 1.0;
                        }
                    }
                });
            } else if (data instanceof DiscreteData) {
                final DiscreteData verticalDiscreteData = (DiscreteData) data;
                final int[][] discreteData = verticalDiscreteData.getData();
                metadata.getInterventionalColumns().forEach(column -> {
                    final ColumnMetadata valCol = column.getValueColumn();
                    final ColumnMetadata statCol = column.getStatusColumn();
                    final int valColNum = valCol.getColumnNumber() - 1;
                    final int statColNum = statCol.getColumnNumber() - 1;
                    final int[] val = discreteData[valColNum];
                    final int[] stat = discreteData[statColNum];
                    for (int i = 0; i < val.length; i++) {
                        if (val[i] == DatasetReader.DISCRETE_MISSING_VALUE) {
                            val[i] = 0;
                            stat[i] = 0;
                        } else if (dataColumns[statColNum].isGenerated()) {
                            stat[i] = 1;
                        }
                    }
                });
            } else if (data instanceof MixedTabularData) {
                final MixedTabularData mixedTabularData = (MixedTabularData) data;
                final double[][] continuousData = mixedTabularData.getContinuousData();
                final int[][] discreteData = mixedTabularData.getDiscreteData();
                metadata.getInterventionalColumns().forEach(column -> {
                    final ColumnMetadata valCol = column.getValueColumn();
                    final ColumnMetadata statCol = column.getStatusColumn();
                    final int valColNum = valCol.getColumnNumber() - 1;
                    final int statColNum = statCol.getColumnNumber() - 1;
                    if (valCol.isDiscrete()) {
                        final int[] val = discreteData[valColNum];
                        if (statCol.isDiscrete()) {
                            final int[] stat = discreteData[statColNum];
                            for (int i = 0; i < val.length; i++) {
                                if (val[i] == DatasetReader.DISCRETE_MISSING_VALUE) {
                                    val[i] = 0;
                                    stat[i] = 0;
                                } else if (dataColumns[statColNum].isGenerated()) {
                                    stat[i] = 1;
                                }
                            }
                        } else {
                            final double[] stat = continuousData[statColNum];
                            for (int i = 0; i < val.length; i++) {
                                if (val[i] == DatasetReader.DISCRETE_MISSING_VALUE) {
                                    val[i] = 0;
                                    stat[i] = 0.0;
                                } else if (dataColumns[statColNum].isGenerated()) {
                                    stat[i] = 1.0;
                                }
                            }
                        }
                    } else {
                        final double[] val = continuousData[valColNum];
                        if (statCol.isDiscrete()) {
                            final int[] stat = discreteData[statColNum];
                            for (int i = 0; i < val.length; i++) {
                                if (Double.isNaN(val[i])) {
                                    val[i] = 0.0;
                                    stat[i] = 0;
                                } else if (dataColumns[statColNum].isGenerated()) {
                                    stat[i] = 1;
                                }
                            }
                        } else {
                            final double[] stat = continuousData[statColNum];
                            for (int i = 0; i < val.length; i++) {
                                if (Double.isNaN(val[i])) {
                                    val[i] = 0.0;
                                    stat[i] = 0.0;
                                } else if (dataColumns[statColNum].isGenerated()) {
                                    stat[i] = 1.0;
                                }
                            }
                        }
                    }
                });
            }
        }

        return data;
    }

    private Data readInMixedData(final DataColumn[] dataColumns, final boolean hasHeader, final int numOfColsInDataFile) throws IOException {
        final int numOfCols = dataColumns.length;
        final int numOfRows = hasHeader ? countNumberOfLines() - 1 : countNumberOfLines();

        final DiscreteDataColumn[] discreteDataColumns = new DiscreteDataColumn[numOfCols];
        final double[][] continuousData = new double[numOfCols][];
        final int[][] discreteData = new int[numOfCols][];
        for (int i = 0; i < numOfCols; i++) {
            final DataColumn dataColumn = dataColumns[i];

            // initialize data
            if (dataColumn.isDiscrete()) {
                discreteData[i] = new int[numOfRows];
            } else {
                continuousData[i] = new double[numOfRows];
            }

            // initialize columns
            discreteDataColumns[i] = new MixedTabularDataColumn(dataColumn);
        }

        readInDiscreteCategorizes(discreteDataColumns, hasHeader, numOfColsInDataFile);
        readInMixedData(discreteDataColumns, hasHeader, continuousData, discreteData, numOfColsInDataFile);

        return new MixedTabularData(numOfRows, discreteDataColumns, continuousData, discreteData);
    }

    private void readInMixedData(final DiscreteDataColumn[] dataColumns, final boolean hasHeader, final double[][] continuousData, final int[][] discreteData, final int numOfColsInDataFile) throws IOException {
        final int numOfCols = dataColumns.length;
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

            int row = 0;  // array row number
            int col = 0;  // array column number

            final StringBuilder dataBuilder = new StringBuilder();
            byte prevChar = -1;
            final byte[] buffer = new byte[DataFileReader.BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !Thread.currentThread().isInterrupted()) {
                int i = 0; // buffer array index

                if (skipHeader) {
                    boolean finished = false;
                    for (; i < len && !finished && !Thread.currentThread().isInterrupted(); i++) {
                        final byte currChar = buffer[i];

                        if (currChar == DataFileReader.CARRIAGE_RETURN || currChar == DataFileReader.LINE_FEED) {
                            if (currChar == DataFileReader.LINE_FEED && prevChar == DataFileReader.CARRIAGE_RETURN) {
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
                        }

                        prevChar = currChar;
                    }
                }

                for (; i < len && !Thread.currentThread().isInterrupted(); i++) {
                    final byte currChar = buffer[i];

                    if (currChar == DataFileReader.CARRIAGE_RETURN || currChar == DataFileReader.LINE_FEED) {
                        if (currChar == DataFileReader.LINE_FEED && prevChar == DataFileReader.CARRIAGE_RETURN) {
                            prevChar = currChar;
                            continue;
                        }

                        if (hasSeenNonblankChar && !skip) {
                            colNum++;

                            final DiscreteDataColumn discreteDataColumn = dataColumns[columnIndex];
                            final DataColumn dataColumn = discreteDataColumn.getDataColumn();
                            if (dataColumn.getColumnNumber() == colNum) {
                                final String value = dataBuilder.toString().trim();
                                if (dataColumn.isDiscrete()) {
                                    if (value.isEmpty() || value.equals(this.missingDataMarker)) {
                                        discreteData[col++][row] = DatasetReader.DISCRETE_MISSING_VALUE;
                                    } else {
                                        discreteData[col++][row] = discreteDataColumn.getEncodeValue(value);
                                    }
                                } else {
                                    if (value.isEmpty() || value.equals(this.missingDataMarker)) {
                                        continuousData[col++][row] = DatasetReader.CONTINUOUS_MISSING_VALUE;
                                    } else {
                                        try {
                                            continuousData[col++][row] = Double.parseDouble(value);
                                        } catch (final NumberFormatException exception) {
                                            final String errMsg = String.format("Invalid number %s on line %d at column %d.", value, lineNum, colNum);
                                            TabularDataFileReader.LOGGER.error(errMsg, exception);
                                            throw new DataReaderException(errMsg);
                                        }
                                    }
                                }

                                columnIndex++;
                            }

                            // ensure we have enough data
                            if (columnIndex < numOfColsInDataFile) {
                                final String errMsg = String.format("Insufficient data on line %d.  Extracted %d value(s) but expected %d.", lineNum, columnIndex, numOfColsInDataFile);
                                TabularDataFileReader.LOGGER.error(errMsg);
                                throw new DataReaderException(errMsg);
                            }

                            row++;
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
                        col = 0;
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

                                    final DiscreteDataColumn discreteDataColumn = dataColumns[columnIndex];
                                    final DataColumn dataColumn = discreteDataColumn.getDataColumn();
                                    if (dataColumn.getColumnNumber() == colNum) {
                                        final String value = dataBuilder.toString().trim();
                                        if (dataColumn.isDiscrete()) {
                                            if (value.isEmpty() || value.equals(this.missingDataMarker)) {
                                                discreteData[col++][row] = DatasetReader.DISCRETE_MISSING_VALUE;
                                            } else {
                                                discreteData[col++][row] = discreteDataColumn.getEncodeValue(value);
                                            }
                                        } else {
                                            if (value.isEmpty() || value.equals(this.missingDataMarker)) {
                                                continuousData[col++][row] = DatasetReader.CONTINUOUS_MISSING_VALUE;
                                            } else {
                                                try {
                                                    continuousData[col++][row] = Double.parseDouble(value);
                                                } catch (final NumberFormatException exception) {
                                                    final String errMsg = String.format("Invalid number %s on line %d at column %d.", value, lineNum, colNum);
                                                    TabularDataFileReader.LOGGER.error(errMsg, exception);
                                                    throw new DataReaderException(errMsg);
                                                }
                                            }
                                        }

                                        columnIndex++;
                                        if (columnIndex == numOfCols) {
                                            row++;
                                            skip = true;
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

                final DiscreteDataColumn discreteDataColumn = dataColumns[columnIndex];
                final DataColumn dataColumn = discreteDataColumn.getDataColumn();
                if (dataColumn.getColumnNumber() == colNum) {
                    final String value = dataBuilder.toString().trim();
                    if (dataColumn.isDiscrete()) {
                        if (value.isEmpty() || value.equals(this.missingDataMarker)) {
                            discreteData[col++][row] = DatasetReader.DISCRETE_MISSING_VALUE;
                        } else {
                            discreteData[col++][row] = discreteDataColumn.getEncodeValue(value);
                        }
                    } else {
                        if (value.isEmpty() || value.equals(this.missingDataMarker)) {
                            continuousData[col++][row] = DatasetReader.CONTINUOUS_MISSING_VALUE;
                        } else {
                            try {
                                continuousData[col++][row] = Double.parseDouble(value);
                            } catch (final NumberFormatException exception) {
                                final String errMsg = String.format("Invalid number %s on line %d at column %d.", value, lineNum, colNum);
                                TabularDataFileReader.LOGGER.error(errMsg, exception);
                                throw new DataReaderException(errMsg);
                            }
                        }
                    }

                    columnIndex++;
                }

                // ensure we have enough data
                if (columnIndex < numOfColsInDataFile) {
                    final String errMsg = String.format("Insufficient data on line %d.  Extracted %d value(s) but expected %d.", lineNum, columnIndex, numOfColsInDataFile);
                    TabularDataFileReader.LOGGER.error(errMsg);
                    throw new DataReaderException(errMsg);
                }
            }
        }
    }

    private Data readInContinuousData(final DataColumn[] dataColumns, final boolean hasHeader, final int numOfColsInDataFile) throws IOException {
        final int numOfCols = dataColumns.length;
        final int numOfRows = hasHeader ? countNumberOfLines() - 1 : countNumberOfLines();
        final double[][] data = new double[numOfRows][numOfCols];

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

            int row = 0;  // array row number
            int col = 0;  // array column number

            final StringBuilder dataBuilder = new StringBuilder();
            byte prevChar = -1;
            final byte[] buffer = new byte[DataFileReader.BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !Thread.currentThread().isInterrupted()) {
                int i = 0; // buffer array index

                if (skipHeader) {
                    boolean finished = false;
                    for (; i < len && !finished && !Thread.currentThread().isInterrupted(); i++) {
                        final byte currChar = buffer[i];

                        if (currChar == DataFileReader.CARRIAGE_RETURN || currChar == DataFileReader.LINE_FEED) {
                            if (currChar == DataFileReader.LINE_FEED && prevChar == DataFileReader.CARRIAGE_RETURN) {
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
                        }

                        prevChar = currChar;
                    }
                }

                for (; i < len && !Thread.currentThread().isInterrupted(); i++) {
                    final byte currChar = buffer[i];

                    if (currChar == DataFileReader.CARRIAGE_RETURN || currChar == DataFileReader.LINE_FEED) {
                        if (currChar == DataFileReader.LINE_FEED && prevChar == DataFileReader.CARRIAGE_RETURN) {
                            prevChar = currChar;
                            continue;
                        }

                        if (hasSeenNonblankChar && !skip) {
                            colNum++;

                            final DataColumn dataColumn = dataColumns[columnIndex];
                            if (dataColumn.getColumnNumber() == colNum) {
                                final String value = dataBuilder.toString().trim();
                                if (value.isEmpty() || value.equals(this.missingDataMarker)) {
                                    data[row][col++] = DatasetReader.CONTINUOUS_MISSING_VALUE;
                                } else {
                                    try {
                                        data[row][col++] = Double.parseDouble(value);
                                    } catch (final NumberFormatException exception) {
                                        final String errMsg = String.format("Non-continuous number %s on line %d at column %d.", value, lineNum, colNum);
                                        TabularDataFileReader.LOGGER.error(errMsg, exception);
                                        throw new DataReaderException(errMsg);
                                    }
                                }

                                columnIndex++;
                            }

                            // ensure we have enough data
                            if (columnIndex < numOfColsInDataFile) {
                                final String errMsg = String.format("Insufficient data on line %d.  Extracted %d value(s) but expected %d.", lineNum, columnIndex, numOfColsInDataFile);
                                TabularDataFileReader.LOGGER.error(errMsg);
                                throw new DataReaderException(errMsg);
                            }

                            row++;
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
                        col = 0;
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

                                    final DataColumn dataColumn = dataColumns[columnIndex];
                                    if (dataColumn.getColumnNumber() == colNum) {
                                        final String value = dataBuilder.toString().trim();
                                        if (value.isEmpty() || value.equals(this.missingDataMarker)) {
                                            data[row][col++] = DatasetReader.CONTINUOUS_MISSING_VALUE;
                                        } else {
                                            try {
                                                data[row][col++] = Double.parseDouble(value);
                                            } catch (final NumberFormatException exception) {
                                                final String errMsg = String.format("Non-continuous number %s on line %d at column %d.", value, lineNum, colNum);
                                                TabularDataFileReader.LOGGER.error(errMsg, exception);
                                                throw new DataReaderException(errMsg);
                                            }
                                        }

                                        columnIndex++;
                                        if (columnIndex == numOfCols) {
                                            row++;
                                            skip = true;
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
                    if (value.isEmpty() || value.equals(this.missingDataMarker)) {
                        data[row][col++] = DatasetReader.CONTINUOUS_MISSING_VALUE;
                    } else {
                        try {
                            data[row][col++] = Double.parseDouble(value);
                        } catch (final NumberFormatException exception) {
                            final String errMsg = String.format("Non-continuous number %s on line %d at column %d.", value, lineNum, colNum);
                            TabularDataFileReader.LOGGER.error(errMsg, exception);
                            throw new DataReaderException(errMsg);
                        }
                    }

                    columnIndex++;
                }

                // ensure we have enough data
                if (columnIndex < numOfColsInDataFile) {
                    final String errMsg = String.format("Insufficient data on line %d.  Extracted %d value(s) but expected %d.", lineNum, columnIndex, numOfColsInDataFile);
                    TabularDataFileReader.LOGGER.error(errMsg);
                    throw new DataReaderException(errMsg);
                }
            }
        }

        return new ContinuousTabularData(dataColumns, data);
    }

    private Data readInDiscreteData(final DataColumn[] dataColumns, final boolean hasHeader, final int numOfColsInDataFile) throws IOException {
        final DiscreteDataColumn[] discreteDataColumns = Arrays.stream(dataColumns)
                .map(DiscreteTabularDataColumn::new)
                .toArray(DiscreteDataColumn[]::new);
        readInDiscreteCategorizes(discreteDataColumns, hasHeader, numOfColsInDataFile);

        final int[][] data = readInDiscreteData(discreteDataColumns, hasHeader, numOfColsInDataFile);

        return new VerticalDiscreteTabularData(discreteDataColumns, data);
    }

    private int[][] readInDiscreteData(final DiscreteDataColumn[] dataColumns, final boolean hasHeader, final int numOfColsInDataFile) throws IOException {
        final int numOfCols = dataColumns.length;
        final int numOfRows = hasHeader ? countNumberOfLines() - 1 : countNumberOfLines();
        final int[][] data = new int[numOfCols][numOfRows];

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

            int row = 0;  // array row number
            int col = 0;  // array column number

            final StringBuilder dataBuilder = new StringBuilder();
            byte prevChar = -1;
            final byte[] buffer = new byte[DataFileReader.BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !Thread.currentThread().isInterrupted()) {
                int i = 0; // buffer array index

                if (skipHeader) {
                    boolean finished = false;
                    for (; i < len && !finished && !Thread.currentThread().isInterrupted(); i++) {
                        final byte currChar = buffer[i];

                        if (currChar == DataFileReader.CARRIAGE_RETURN || currChar == DataFileReader.LINE_FEED) {
                            if (currChar == DataFileReader.LINE_FEED && prevChar == DataFileReader.CARRIAGE_RETURN) {
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
                        }

                        prevChar = currChar;
                    }
                }

                for (; i < len && !Thread.currentThread().isInterrupted(); i++) {
                    final byte currChar = buffer[i];

                    if (currChar == DataFileReader.CARRIAGE_RETURN || currChar == DataFileReader.LINE_FEED) {
                        if (currChar == DataFileReader.LINE_FEED && prevChar == DataFileReader.CARRIAGE_RETURN) {
                            prevChar = currChar;
                            continue;
                        }

                        if (hasSeenNonblankChar && !skip) {
                            colNum++;

                            final DiscreteDataColumn discreteDataColumn = dataColumns[columnIndex];
                            final DataColumn dataColumn = discreteDataColumn.getDataColumn();
                            if (dataColumn.getColumnNumber() == colNum) {
                                final String value = dataBuilder.toString().trim();
                                if (value.isEmpty() || value.equals(this.missingDataMarker)) {
                                    data[col++][row] = DatasetReader.DISCRETE_MISSING_VALUE;
                                } else {
                                    data[col++][row] = discreteDataColumn.getEncodeValue(value);
                                }

                                columnIndex++;
                            }

                            // ensure we have enough data
                            if (columnIndex < numOfColsInDataFile) {
                                final String errMsg = String.format("Insufficient data on line %d.  Extracted %d value(s) but expected %d.", lineNum, columnIndex, numOfColsInDataFile);
                                TabularDataFileReader.LOGGER.error(errMsg);
                                throw new DataReaderException(errMsg);
                            }

                            row++;
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
                        col = 0;
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

                                    final DiscreteDataColumn discreteDataColumn = dataColumns[columnIndex];
                                    final DataColumn dataColumn = discreteDataColumn.getDataColumn();
                                    if (dataColumn.getColumnNumber() == colNum) {
                                        final String value = dataBuilder.toString().trim();
                                        if (value.isEmpty() || value.equals(this.missingDataMarker)) {
                                            data[col++][row] = DatasetReader.DISCRETE_MISSING_VALUE;
                                        } else {
                                            data[col++][row] = discreteDataColumn.getEncodeValue(value);
                                        }

                                        columnIndex++;
                                        if (columnIndex == numOfCols) {
                                            row++;
                                            skip = true;
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

                final DiscreteDataColumn discreteDataColumn = dataColumns[columnIndex];
                final DataColumn dataColumn = discreteDataColumn.getDataColumn();
                if (dataColumn.getColumnNumber() == colNum) {
                    final String value = dataBuilder.toString().trim();
                    if (value.isEmpty() || value.equals(this.missingDataMarker)) {
                        data[col++][row] = DatasetReader.DISCRETE_MISSING_VALUE;
                    } else {
                        data[col++][row] = discreteDataColumn.getEncodeValue(value);
                    }

                    columnIndex++;
                }

                // ensure we have enough data
                if (columnIndex < numOfColsInDataFile) {
                    final String errMsg = String.format("Insufficient data on line %d.  Extracted %d value(s) but expected %d.", lineNum, columnIndex, numOfColsInDataFile);
                    TabularDataFileReader.LOGGER.error(errMsg);
                    throw new DataReaderException(errMsg);
                }
            }
        }

        return data;
    }

    private void readInDiscreteCategorizes(final DiscreteDataColumn[] dataColumns, final boolean hasHeader, final int numOfColsInDataFile) throws IOException {
        final int numOfCols = dataColumns.length;
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
            final byte[] buffer = new byte[DataFileReader.BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !Thread.currentThread().isInterrupted()) {
                int i = 0; // buffer array index

                if (skipHeader) {
                    boolean finished = false;
                    for (; i < len && !finished && !Thread.currentThread().isInterrupted(); i++) {
                        final byte currChar = buffer[i];

                        if (currChar == DataFileReader.CARRIAGE_RETURN || currChar == DataFileReader.LINE_FEED) {
                            if (currChar == DataFileReader.LINE_FEED && prevChar == DataFileReader.CARRIAGE_RETURN) {
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
                        }

                        prevChar = currChar;
                    }
                }

                for (; i < len && !Thread.currentThread().isInterrupted(); i++) {
                    final byte currChar = buffer[i];

                    if (currChar == DataFileReader.CARRIAGE_RETURN || currChar == DataFileReader.LINE_FEED) {
                        if (currChar == DataFileReader.LINE_FEED && prevChar == DataFileReader.CARRIAGE_RETURN) {
                            prevChar = currChar;
                            continue;
                        }

                        if (hasSeenNonblankChar && !skip) {
                            colNum++;

                            final DiscreteDataColumn discreteDataColumn = dataColumns[columnIndex];
                            final DataColumn dataColumn = discreteDataColumn.getDataColumn();
                            if (dataColumn.getColumnNumber() == colNum) {
                                if (dataColumn.isDiscrete()) {
                                    final String value = dataBuilder.toString().trim();
                                    if (value.length() > 0 && !value.equals(this.missingDataMarker)) {
                                        discreteDataColumn.setValue(value);
                                    }
                                }

                                columnIndex++;
                            }

                            // ensure we have enough data
                            if (columnIndex < numOfColsInDataFile) {
                                final String errMsg = String.format("Insufficient data on line %d.  Extracted %d value(s) but expected %d.", lineNum, columnIndex, numOfColsInDataFile);
                                TabularDataFileReader.LOGGER.error(errMsg);
                                throw new DataReaderException(errMsg);
                            }
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

                                    final DiscreteDataColumn discreteDataColumn = dataColumns[columnIndex];
                                    final DataColumn dataColumn = discreteDataColumn.getDataColumn();
                                    if (dataColumn.getColumnNumber() == colNum) {
                                        if (dataColumn.isDiscrete()) {
                                            final String value = dataBuilder.toString().trim();
                                            if (value.length() > 0 && !value.equals(this.missingDataMarker)) {
                                                discreteDataColumn.setValue(value);
                                            }
                                        }

                                        columnIndex++;
                                        if (columnIndex == numOfCols) {
                                            skip = true;
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

                final DiscreteDataColumn discreteDataColumn = dataColumns[columnIndex];
                final DataColumn dataColumn = discreteDataColumn.getDataColumn();
                if (dataColumn.getColumnNumber() == colNum) {
                    if (dataColumn.isDiscrete()) {
                        final String value = dataBuilder.toString().trim();
                        if (value.length() > 0 && !value.equals(this.missingDataMarker)) {
                            discreteDataColumn.setValue(value);
                        }
                    }

                    columnIndex++;
                }

                // ensure we have enough data
                if (columnIndex < numOfColsInDataFile) {
                    final String errMsg = String.format("Insufficient data on line %d.  Extracted %d value(s) but expected %d.", lineNum, columnIndex, numOfColsInDataFile);
                    TabularDataFileReader.LOGGER.error(errMsg);
                    throw new DataReaderException(errMsg);
                }
            }
        }

        // recategorize values
        for (final DiscreteDataColumn discreteDataColumn : dataColumns) {
            if (discreteDataColumn.getDataColumn().isGenerated()) {
                discreteDataColumn.setValue("0");
                discreteDataColumn.setValue("1");
            }

            discreteDataColumn.recategorize();
        }
    }

}
