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
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.VerticalIntDataBox;
import edu.cmu.tetrad.graph.Node;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This data reader reads in discrete data in a transposed (vertical) format.
 *
 * Mar 30, 2016 2:40:14 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class VerticalTabularDiscreteDataReader extends AbstractDiscreteDataReader implements DataReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(VerticalTabularDiscreteDataReader.class);

    public VerticalTabularDiscreteDataReader(Path dataFile, char delimiter) {
        super(dataFile, delimiter);
    }

    @Override
    public DataSet readInData() throws IOException {
        VariableAnalysis variableAnalysis = analyzeVariables();
        variableAnalysis.recategorizeDiscreteVariables();

        int numOfRows = variableAnalysis.getNumOfRows();
        int numOfCols = variableAnalysis.getNumOfCols();

        List<Node> nodes = new ArrayList<>(numOfCols);
        VarInfo[] varInfos = variableAnalysis.getVarInfos();
        for (VarInfo varInfo : varInfos) {
            nodes.add(new DiscreteVariable(varInfo.getName(), varInfo.getCategories()));
        }

        int[][] data = new int[numOfCols][numOfRows];
        int row = 0;
        int col = -1;
        try (FileChannel fc = new RandomAccessFile(dataFile.toFile(), "r").getChannel()) {
            MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            StringBuilder dataBuilder = new StringBuilder();
            byte currentChar = -1;
            byte prevChar = NEW_LINE;

            // skip the header
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == NEW_LINE) {
                    prevChar = currentChar;
                    break;
                }

                prevChar = currentChar;
            }

            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delimiter || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    col++;
                    String value = dataBuilder.toString();
                    dataBuilder.delete(0, dataBuilder.length());
                    if (value.length() > 0) {
                        data[col][row] = varInfos[col].getEncodeValue(value);
                    } else {
                        String errMsg = String.format("Missing data at column %d.", col + 1);
                        LOGGER.error(errMsg);
                        throw new IOException(errMsg);
                    }

                    if (currentChar == NEW_LINE) {
                        col = -1;
                        row++;
                    }
                } else {
                    if (currentChar <= SPACE || currentChar == SINGLE_QUOTE || currentChar == DOUBLE_QUOTE) {
                        continue;
                    }
                    dataBuilder.append((char) currentChar);
                }

                prevChar = currentChar;
            }
            if (currentChar != NEW_LINE) {
                col++;
                if (currentChar == delimiter) {
                    String errMsg = String.format("Missing data at column %d.", col + 1);
                    LOGGER.error(errMsg);
                    throw new IOException(errMsg);
                } else {
                    String value = dataBuilder.toString();
                    dataBuilder.delete(0, dataBuilder.length());
                    if (value.length() > 0) {
                        data[col][row] = varInfos[col].getEncodeValue(value);
                    } else {
                        String errMsg = String.format("Missing data at column %d.", col + 1);
                        LOGGER.error(errMsg);
                        throw new IOException(errMsg);
                    }
                }
            }
        }

        return new BoxDataSet(new VerticalIntDataBox(data), nodes);
    }

    @Override
    public DataSet readInData(Set<String> excludedVariables) throws IOException {
        if (excludedVariables == null || excludedVariables.isEmpty()) {
            return readInData();
        }

        VariableAnalysis variableAnalysis = analyzeVariables(excludedVariables);
        variableAnalysis.recategorizeDiscreteVariables();

        int numOfRows = variableAnalysis.getNumOfRows();
        int numOfCols = variableAnalysis.getNumOfCols();

        List<Node> nodes = new ArrayList<>(numOfCols);
        VarInfo[] varInfos = variableAnalysis.getVarInfos();
        for (VarInfo varInfo : varInfos) {
            if (!varInfo.isExcluded()) {
                nodes.add(new DiscreteVariable(varInfo.getName(), varInfo.getCategories()));
            }
        }

        int[][] data = new int[numOfCols][numOfRows];
        int row = 0;
        int col = -1;
        int colIndex = 0;
        try (FileChannel fc = new RandomAccessFile(dataFile.toFile(), "r").getChannel()) {
            MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            StringBuilder dataBuilder = new StringBuilder();
            byte currentChar = -1;
            byte prevChar = NEW_LINE;

            // skip the header
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == NEW_LINE) {
                    prevChar = currentChar;
                    break;
                }

                prevChar = currentChar;
            }

            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delimiter || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    col++;
                    String value = dataBuilder.toString();
                    dataBuilder.delete(0, dataBuilder.length());
                    if (!varInfos[col].isExcluded()) {
                        if (value.length() > 0) {
                            data[colIndex++][row] = varInfos[col].getEncodeValue(value);
                        } else {
                            String errMsg = String.format("Missing data at column %d.", col + 1);
                            LOGGER.error(errMsg);
                            throw new IOException(errMsg);
                        }
                    }

                    if (currentChar == NEW_LINE) {
                        col = -1;
                        colIndex = 0;
                        row++;
                    }
                } else {
                    if (currentChar <= SPACE || currentChar == SINGLE_QUOTE || currentChar == DOUBLE_QUOTE) {
                        continue;
                    }
                    dataBuilder.append((char) currentChar);
                }

                prevChar = currentChar;
            }
            if (currentChar != NEW_LINE) {
                col++;
                if (!varInfos[col].isExcluded()) {
                    if (currentChar == delimiter) {
                        String errMsg = String.format("Missing data at column %d.", col + 1);
                        LOGGER.error(errMsg);
                        throw new IOException(errMsg);
                    } else {
                        String value = dataBuilder.toString();
                        dataBuilder.delete(0, dataBuilder.length());
                        if (value.length() > 0) {
                            data[colIndex++][row] = varInfos[col].getEncodeValue(value);
                        } else {
                            String errMsg = String.format("Missing data at column %d.", col + 1);
                            LOGGER.error(errMsg);
                            throw new IOException(errMsg);
                        }
                    }
                }
            }
        }

        return new BoxDataSet(new VerticalIntDataBox(data), nodes);
    }

}
