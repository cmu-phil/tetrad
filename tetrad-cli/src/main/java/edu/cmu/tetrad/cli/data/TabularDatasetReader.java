/*
 * Copyright (C) 2016 University of Pittsburgh.
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
package edu.cmu.tetrad.cli.data;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 *
 * Feb 3, 2016 11:46:22 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TabularDatasetReader extends AbstractDatasetReader {

    public TabularDatasetReader(Path dataFile, char delimiter) {
        super(dataFile, delimiter);
    }

    @Override
    public DataSet readInContinuousData() throws IOException {
        int numOfRows = countNumberOfLines() - 1;  // exclude the header

        double[][] data;
        List<Node> nodes = new LinkedList<>();
        try (FileChannel fc = new RandomAccessFile(dataFile.toFile(), "r").getChannel()) {
            MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            StringBuilder dataBuilder = new StringBuilder();

            int numOfCols = 0;
            byte currentChar;
            byte prevChar = NEW_LINE;
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delimiter || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    nodes.add(new ContinuousVariable(dataBuilder.toString().trim()));

                    numOfCols++;
                    dataBuilder.delete(0, dataBuilder.length());
                    if (currentChar == NEW_LINE) {
                        prevChar = currentChar;
                        break;
                    }
                } else {
                    if (currentChar == SINGLE_QUOTE || currentChar == DOUBLE_QUOTE) {
                        continue;
                    }
                    dataBuilder.append((char) currentChar);
                }

                prevChar = currentChar;
            }
            // cases where the last column is empty
            if (prevChar == delimiter) {
                nodes.add(new ContinuousVariable(dataBuilder.toString().trim()));
                numOfCols++;
            }
            // cases where there's no newline at the end of the file
            String leftover = dataBuilder.toString().trim();
            if (leftover.length() > 0) {
                nodes.add(new ContinuousVariable(leftover));
                numOfCols++;
            }

            // read in data
            data = new double[numOfRows][numOfCols];
            int row = 0;
            int col = 0;
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delimiter || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    String value = dataBuilder.toString().trim();
                    if (value.length() > 0) {
                        try {
                            data[row][col] = Double.parseDouble(value);
                        } catch (NumberFormatException exception) {
                            throw new IOException(
                                    String.format("Unable to parse data at line %d column %d\n", row + 2, col),
                                    exception);
                        }
                    }
                    col++;

                    dataBuilder.delete(0, dataBuilder.length());
                    if (currentChar == NEW_LINE) {
                        col = 0;
                        row++;
                    }
                } else {
                    if (currentChar == SINGLE_QUOTE || currentChar == DOUBLE_QUOTE) {
                        continue;
                    }
                    dataBuilder.append((char) currentChar);
                }

                prevChar = currentChar;
            }

            // cases where there's no newline at the end of the file
            String value = dataBuilder.toString().trim();
            if (value.length() > 0) {
                try {
                    data[row][col++] = Double.parseDouble(value);
                } catch (NumberFormatException exception) {
                    throw new IOException(
                            String.format("Unable to parse data at line %d column %d\n", row + 2, col),
                            exception);
                }
            }
        }

        return new BoxDataSet(new DoubleDataBox(data), nodes);
    }

    @Override
    public DataSet readInContinuousData(Set<String> excludedVariables) throws IOException {
        if (excludedVariables == null || excludedVariables.isEmpty()) {
            return readInContinuousData();
        }

        int numOfRows = countNumberOfLines() - 1;  // exclude the header

        double[][] data;
        List<Node> nodes = new LinkedList<>();
        try (FileChannel fc = new RandomAccessFile(dataFile.toFile(), "r").getChannel()) {
            MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            StringBuilder dataBuilder = new StringBuilder();

            List<Integer> variablesToExclude = new LinkedList<>();

            int numOfCols = 0;
            byte currentChar;
            byte prevChar = NEW_LINE;
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delimiter || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    String value = dataBuilder.toString().trim();
                    if (excludedVariables.contains(value)) {
                        variablesToExclude.add(numOfCols);
                    } else {
                        nodes.add(new ContinuousVariable(value));
                    }
                    numOfCols++;

                    dataBuilder.delete(0, dataBuilder.length());
                    if (currentChar == NEW_LINE) {
                        prevChar = currentChar;
                        break;
                    }
                } else {
                    if (currentChar == SINGLE_QUOTE || currentChar == DOUBLE_QUOTE) {
                        continue;
                    }
                    dataBuilder.append((char) currentChar);
                }

                prevChar = currentChar;
            }

            // cases where the last column is empty
            if (prevChar == delimiter) {
                String value = dataBuilder.toString().trim();
                if (excludedVariables.contains(value)) {
                    variablesToExclude.add(numOfCols);
                } else {
                    nodes.add(new ContinuousVariable(value));
                }
                numOfCols++;
            }
            // cases where there's no newline at the end of the file
            String leftover = dataBuilder.toString().trim();
            if (leftover.length() > 0) {
                if (excludedVariables.contains(leftover)) {
                    variablesToExclude.add(numOfCols);
                } else {
                    nodes.add(new ContinuousVariable(leftover));
                }
                numOfCols++;
            }

            if (variablesToExclude.isEmpty()) {
                data = new double[numOfRows][numOfCols];
                int row = 0;
                int col = 0;
                while (buffer.hasRemaining()) {
                    currentChar = buffer.get();
                    if (currentChar == CARRIAGE_RETURN) {
                        currentChar = NEW_LINE;
                    }

                    if (currentChar == delimiter || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                        String value = dataBuilder.toString().trim();
                        if (value.length() > 0) {
                            try {
                                data[row][col] = Double.parseDouble(value);
                            } catch (NumberFormatException exception) {
                                throw new IOException(
                                        String.format("Unable to parse data at line %d column %d\n", row + 2, col),
                                        exception);
                            }
                        }
                        col++;

                        dataBuilder.delete(0, dataBuilder.length());
                        if (currentChar == NEW_LINE) {
                            col = 0;
                            row++;
                        }
                    } else {
                        if (currentChar == SINGLE_QUOTE || currentChar == DOUBLE_QUOTE) {
                            continue;
                        }
                        dataBuilder.append((char) currentChar);
                    }

                    prevChar = currentChar;
                }

                // cases where there's no newline at the end of the file
                String value = dataBuilder.toString().trim();
                if (value.length() > 0) {
                    try {
                        data[row][col++] = Double.parseDouble(value);
                    } catch (NumberFormatException exception) {
                        throw new IOException(
                                String.format("Unable to parse data at line %d column %d\n", row + 2, col),
                                exception);
                    }
                }
            } else {
                numOfCols = nodes.size();

                int[] exclusions = new int[variablesToExclude.size()];
                int excludeIndex = 0;
                for (Integer varIndex : variablesToExclude) {
                    exclusions[excludeIndex++] = varIndex;
                }
                Arrays.sort(exclusions);
                excludeIndex = 0;

                // read in data
                data = new double[numOfRows][numOfCols];
                int row = 0;
                int col = 0;
                int dataCol = 0;
                int excludeColumn = exclusions[excludeIndex];
                while (buffer.hasRemaining()) {
                    currentChar = buffer.get();
                    if (currentChar == CARRIAGE_RETURN) {
                        currentChar = NEW_LINE;
                    }

                    if (currentChar == delimiter || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                        if (col == excludeColumn) {
                            excludeIndex++;
                            if (excludeIndex < exclusions.length) {
                                excludeColumn = exclusions[excludeIndex];
                            }
                        } else {
                            String value = dataBuilder.toString().trim();
                            if (value.length() > 0) {
                                try {
                                    data[row][dataCol] = Double.parseDouble(value);
                                } catch (NumberFormatException exception) {
                                    throw new IOException(
                                            String.format("Unable to parse data at line %d column %d\n", row + 2, col),
                                            exception);
                                }
                            }
                            dataCol++;
                        }
                        col++;

                        dataBuilder.delete(0, dataBuilder.length());
                        if (currentChar == NEW_LINE) {
                            dataCol = 0;
                            col = 0;
                            row++;

                            excludeIndex = 0;
                            excludeColumn = exclusions[excludeIndex];
                        }
                    } else {
                        if (currentChar == SINGLE_QUOTE || currentChar == DOUBLE_QUOTE) {
                            continue;
                        }
                        dataBuilder.append((char) currentChar);
                    }

                    prevChar = currentChar;
                }

                // cases where there's no newline at the end of the file
                String value = dataBuilder.toString().trim();
                if (value.length() > 0) {
                    if (col != excludeColumn) {
                        try {
                            data[row][dataCol] = Double.parseDouble(value);
                        } catch (NumberFormatException exception) {
                            throw new IOException(
                                    String.format("Unable to parse data at line %d column %d\n", row + 2, col),
                                    exception);
                        }
                    }
                }
            }

        }

        return new BoxDataSet(new DoubleDataBox(data), nodes);
    }

}
