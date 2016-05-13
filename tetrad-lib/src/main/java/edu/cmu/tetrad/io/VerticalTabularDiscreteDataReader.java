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
import edu.cmu.tetrad.data.VerticalIntDataBox;
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
        return readInData(Collections.EMPTY_SET);
    }

    @Override
    public DataSet readInData(Set<String> excludedVariables) throws IOException {
        if (excludedVariables == null) {
            excludedVariables = Collections.EMPTY_SET;
        }

        DiscreteVariableAnalysis variableAnalysis = analyzeVariables(excludedVariables);
        variableAnalysis.recategorize();

        List<Node> nodes = createDiscreteVariableList(variableAnalysis);
        int[][] data = encodeDiscreteData(variableAnalysis);

        return new BoxDataSet(new VerticalIntDataBox(data), nodes);
    }

    protected int[][] encodeDiscreteData(DiscreteVariableAnalysis variableAnalysis) throws IOException {
        DiscreteVarInfo[] variables = variableAnalysis.getDiscreteVarInfos();

        int maxNumOfCols = variables.length;
        int numOfCols = variableAnalysis.getNumOfCols();
        int numOfRows = countNumberOfLines() - 1;  // minus the header

        int[][] data = new int[numOfCols][numOfRows];
        try (FileChannel fc = new RandomAccessFile(dataFile.toFile(), "r").getChannel()) {
            MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());

            skipToNextLine(buffer);  // skip the header

            int colCount = 0;
            int col = 0;
            int row = 0;  // data start on the second row
            byte currentChar = -1;
            byte prevChar = NEW_LINE;
            StringBuilder dataBuilder = new StringBuilder();
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delimiter || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    String value = dataBuilder.toString().trim();
                    dataBuilder.delete(0, dataBuilder.length());
                    if (colCount < maxNumOfCols) {
                        DiscreteVarInfo variable = variables[colCount];
                        if (variable != null) {
                            if (value.length() > 0) {
                                data[col++][row] = variable.getEncodeValue(value);
                            } else {
                                String errMsg = String.format("Missing data at line %d column %d.", row + 2, col + 1);
                                LOGGER.error(errMsg);
                                throw new IOException(errMsg);
                            }
                        }
                    } else {
                        String errMsg = String.format("Number of columns exceeded at line %d.  Expect %d column(s) but found %d.", row + 2, maxNumOfCols, colCount + 1);
                        LOGGER.error(errMsg);
                        throw new IOException(errMsg);
                    }

                    colCount++;
                    if (currentChar == NEW_LINE) {
                        if (col < numOfCols) {
                            String errMsg = String.format("Insufficient number of columns at line %d.  Expect %d column(s) but found %d.", row + 2, numOfCols, col);
                            LOGGER.error(errMsg);
                            throw new IOException(errMsg);
                        }
                        colCount = 0;
                        col = 0;
                        row++;
                    }
                } else if (currentChar != SINGLE_QUOTE && currentChar != DOUBLE_QUOTE) {
                    dataBuilder.append((char) currentChar);
                }

                prevChar = currentChar;
            }
            if (currentChar > -1 && currentChar != NEW_LINE) {
                if (colCount < maxNumOfCols) {
                    DiscreteVarInfo variable = variables[colCount];
                    if (variable != null) {
                        if (currentChar == delimiter) {
                            String errMsg = String.format("Missing data at line %d column %d.", row + 2, colCount + 1);
                            LOGGER.error(errMsg);
                            throw new IOException(errMsg);
                        } else {
                            String value = dataBuilder.toString().trim();
                            dataBuilder.delete(0, dataBuilder.length());
                            if (value.length() > 0) {
                                data[col++][row] = variable.getEncodeValue(value);
                            } else {
                                String errMsg = String.format("Missing data at line %d column %d.", row + 2, colCount + 1);
                                LOGGER.error(errMsg);
                                throw new IOException(errMsg);
                            }
                        }
                    }
                } else {
                    String errMsg = String.format("Number of columns exceeded at line %d.  Expect %d column(s) but found %d.", row + 2, maxNumOfCols, colCount + 1);
                    LOGGER.error(errMsg);
                    throw new IOException(errMsg);
                }
            }
        }

        return data;
    }

}
