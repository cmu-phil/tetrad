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
package edu.cmu.tetrad.io;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Feb 29, 2016 1:34:57 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TabularContinuousDataReader extends AbstractContinuousDataReader implements DataReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(TabularContinuousDataReader.class);

    public TabularContinuousDataReader(Path dataFile, char delimiter) {
        super(dataFile, delimiter);
    }

    @Override
    public DataSet readInData() throws IOException {
        return readInData(Collections.EMPTY_SET);
    }

    @Override
    public DataSet readInData(Set<String> excludedVariables) throws IOException {
        if (excludedVariables == null) {
            excludedVariables = Collections.EMPTY_SET;
        }

        ContinuousVariableAnalysis variableAnalysis = analyzeData(excludedVariables);
        List<Node> nodes = variableAnalysis.getVariables();
        double[][] data = extractContinuousData(variableAnalysis);

        return new BoxDataSet(new DoubleDataBox(data), nodes);
    }

    protected double[][] extractContinuousData(ContinuousVariableAnalysis variableAnalysis) throws IOException {
        int maxNumOfCols = countNumberOfColumns();
        int numOfCols = variableAnalysis.getVariables().size();
        int numOfRows = countNumberOfLines() - 1;  // minus the header

        double[][] data = new double[numOfRows][numOfCols];
        try (FileChannel fc = new RandomAccessFile(dataFile.toFile(), "r").getChannel()) {
            MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());

            skipToNextLine(buffer);  // skip header

            int[] excludedIndices = variableAnalysis.getExcludedIndices();
            int excludedIndex = 0;
            int excludedColumn = excludedIndices[excludedIndex];

            int row = 0;
            int col = 0;
            int colCount = 0;
            byte currentChar = -1;
            byte prevChar = NEW_LINE;
            StringBuilder dataBuilder = new StringBuilder();
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delimiter || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    String value = dataBuilder.toString();
                    dataBuilder.delete(0, dataBuilder.length());
                    if (colCount == excludedColumn) {
                        excludedIndex++;
                        if (excludedIndex < excludedIndices.length) {
                            excludedColumn = excludedIndices[excludedIndex];
                        }
                    } else {
                        if (colCount < maxNumOfCols) {
                            if (value.length() > 0) {
                                try {
                                    data[row][col++] = Double.parseDouble(value);
                                } catch (NumberFormatException exception) {
                                    throw new IOException(
                                            String.format("Unable to parse data at line %d column %d.", row + 2, colCount + 1),
                                            exception);
                                }
                            } else {
                                String errMsg = String.format("Missing data at line %d column %d.", row + 2, colCount + 1);
                                LOGGER.error(errMsg);
                                throw new IOException(errMsg);
                            }
                        } else {
                            String errMsg = String.format("Number of columns exceeded at line %d.  Expect %d column(s) but found %d.", row + 2, maxNumOfCols, colCount + 1);
                            LOGGER.error(errMsg);
                            throw new IOException(errMsg);
                        }
                    }

                    colCount++;
                    if (currentChar == NEW_LINE) {
                        if (col < numOfCols) {
                            String errMsg = String.format("Insufficient number of columns at line %d.  Expect %d column(s) but found %d.", row + 2, maxNumOfCols, colCount);
                            LOGGER.error(errMsg);
                            throw new IOException(errMsg);
                        }
                        colCount = 0;
                        col = 0;
                        row++;

                        excludedIndex = 0;
                        excludedColumn = excludedIndices[excludedIndex];
                    }
                } else if (currentChar > SPACE && (currentChar != SINGLE_QUOTE && currentChar != DOUBLE_QUOTE)) {
                    dataBuilder.append((char) currentChar);
                }

                prevChar = currentChar;
            }
            if (currentChar > -1 && currentChar != NEW_LINE) {
                if (currentChar == delimiter) {
                    String errMsg = String.format("Missing data at line %d column %d.", row + 2, col + 1);
                    LOGGER.error(errMsg);
                    throw new IOException(errMsg);
                } else {
                    String value = dataBuilder.toString();
                    dataBuilder.delete(0, dataBuilder.length());
                    if (colCount != excludedColumn) {
                        if (colCount < maxNumOfCols) {
                            if (value.length() > 0) {
                                try {
                                    data[row][col++] = Double.parseDouble(value);
                                } catch (NumberFormatException exception) {
                                    throw new IOException(
                                            String.format("Unable to parse data at line %d column %d.", row + 2, colCount + 1),
                                            exception);
                                }
                            } else {
                                String errMsg = String.format("Missing data at line %d column %d.", row + 2, colCount + 1);
                                LOGGER.error(errMsg);
                                throw new IOException(errMsg);
                            }
                        } else {
                            String errMsg = String.format("Number of columns exceeded at line %d.  Expect %d column(s) but found %d.", row + 2, maxNumOfCols, colCount + 1);
                            LOGGER.error(errMsg);
                            throw new IOException(errMsg);
                        }
                    }
                }
            }
        }

        return data;
    }

}
