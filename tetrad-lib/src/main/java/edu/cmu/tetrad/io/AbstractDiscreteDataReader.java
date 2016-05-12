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

import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Apr 1, 2016 11:40:18 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public abstract class AbstractDiscreteDataReader extends AbstractDataReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDiscreteDataReader.class);

    public AbstractDiscreteDataReader(Path dataFile, char delimiter) {
        super(dataFile, delimiter);
    }

    protected List<Node> createDiscreteVariableList(DiscreteVariableAnalysis variableAnalysis) {
        List<Node> nodes = new ArrayList<>(variableAnalysis.getNumOfCols());
        DiscreteVarInfo[] variables = variableAnalysis.getDiscreteVarInfos();
        for (DiscreteVarInfo variable : variables) {
            if (variable != null) {
                nodes.add(new DiscreteVariable(variable.getName(), variable.getCategories()));
            }
        }

        return nodes;
    }

    protected DiscreteVariableAnalysis analyzeVariables(Set<String> excludedVariables) throws IOException {
        DiscreteVariableAnalysis variableAnalysis = new DiscreteVariableAnalysis();
        extractVariables(excludedVariables, variableAnalysis);
        extractVariableValues(excludedVariables, variableAnalysis);

        return variableAnalysis;
    }

    protected void extractVariableValues(Set<String> excludedVariables, DiscreteVariableAnalysis variableAnalysis) throws IOException {
        DiscreteVarInfo[] discreteVarInfos = variableAnalysis.getDiscreteVarInfos();
        int numOfCols = variableAnalysis.numOfCols;
        int maxNumOfVariables = discreteVarInfos.length;
        try (FileChannel fc = new RandomAccessFile(dataFile.toFile(), "r").getChannel()) {
            MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());

            skipToNextLine(buffer);  // skip header

            int columnIndex = 0;
            int rowCount = 2;  // data start on the second row
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
                    if (columnIndex < maxNumOfVariables) {
                        DiscreteVarInfo variable = discreteVarInfos[columnIndex];
                        if (variable != null) {
                            if (value.length() > 0) {
                                variable.setValue(value);
                            } else {
                                String errMsg = String.format("Missing data at line %d column %d.", rowCount, columnIndex + 1);
                                LOGGER.error(errMsg);
                                throw new IOException(errMsg);
                            }
                        }
                    } else {
                        String errMsg = String.format("Number of columns exceeded at line %d.  Expect %d column(s) but found %d.", rowCount, maxNumOfVariables, columnIndex + 1);
                        LOGGER.error(errMsg);
                        throw new IOException(errMsg);
                    }

                    columnIndex++;
                    if (currentChar == NEW_LINE) {
                        if (columnIndex < numOfCols) {
                            String errMsg = String.format("Insufficient number of columns at line %d.  Expect %d column(s) but found %d.", rowCount, numOfCols, columnIndex);
                            LOGGER.error(errMsg);
                            throw new IOException(errMsg);
                        }
                        columnIndex = 0;
                        rowCount++;
                    }
                } else if (currentChar != SINGLE_QUOTE && currentChar != DOUBLE_QUOTE) {
                    dataBuilder.append((char) currentChar);
                }

                prevChar = currentChar;
            }
            if (currentChar > -1 && currentChar != NEW_LINE) {
                if (columnIndex < maxNumOfVariables) {
                    DiscreteVarInfo variable = discreteVarInfos[columnIndex];
                    if (variable != null) {
                        if (currentChar == delimiter) {
                            String errMsg = String.format("Missing data at line %d column %d.", rowCount, columnIndex + 1);
                            LOGGER.error(errMsg);
                            throw new IOException(errMsg);
                        } else {
                            String value = dataBuilder.toString().trim();
                            dataBuilder.delete(0, dataBuilder.length());
                            if (value.length() > 0) {
                                variable.setValue(value);
                            } else {
                                String errMsg = String.format("Missing data at line %d column %d.", rowCount, columnIndex + 1);
                                LOGGER.error(errMsg);
                                throw new IOException(errMsg);
                            }
                        }
                    }
                } else {
                    String errMsg = String.format("Number of columns exceeded at line %d.  Expect %d column(s) but found %d.", rowCount, numOfCols, columnIndex + 1);
                    LOGGER.error(errMsg);
                    throw new IOException(errMsg);
                }
            }
        }
    }

    protected void extractVariables(Set<String> excludedVariables, DiscreteVariableAnalysis variableAnalysis) throws IOException {
        int numOfCols = 0;
        DiscreteVarInfo[] discreteVarInfos = new DiscreteVarInfo[countNumberOfColumns()];
        try (FileChannel fc = new RandomAccessFile(dataFile.toFile(), "r").getChannel()) {
            MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());

            int index = 0;
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
                    if (value.length() > 0) {
                        if (!excludedVariables.contains(value)) {
                            discreteVarInfos[index] = new DiscreteVarInfo(value);
                            numOfCols++;
                        }
                    } else {
                        String errMsg = String.format("Missing variable name at column %d.", index + 1);
                        LOGGER.error(errMsg);
                        throw new IOException(errMsg);
                    }

                    index++;
                    if (currentChar == NEW_LINE) {
                        break;
                    }
                } else if (currentChar != SINGLE_QUOTE && currentChar != DOUBLE_QUOTE) {
                    dataBuilder.append((char) currentChar);
                }

                prevChar = currentChar;
            }
            if (currentChar > -1 && currentChar != NEW_LINE) {
                if (currentChar == delimiter) {
                    String errMsg = String.format("Missing variable name at column %d.", index + 1);
                    LOGGER.error(errMsg);
                    throw new IOException(errMsg);
                } else {
                    String value = dataBuilder.toString().trim();
                    dataBuilder.delete(0, dataBuilder.length());
                    if (value.length() > 0) {
                        if (!excludedVariables.contains(value)) {
                            discreteVarInfos[index] = new DiscreteVarInfo(value);
                            numOfCols++;
                        }
                    } else {
                        String errMsg = String.format("Missing variable name at column %d.", index + 1);
                        LOGGER.error(errMsg);
                        throw new IOException(errMsg);
                    }
                }
            }
        }

        variableAnalysis.setDiscreteVarInfos(discreteVarInfos);
        variableAnalysis.setNumOfCols(numOfCols);
    }

    public static class DiscreteVariableAnalysis {

        private DiscreteVarInfo[] discreteVarInfos;
        private int numOfCols;

        public DiscreteVariableAnalysis() {
        }

        public void recategorize() {
            for (DiscreteVarInfo var : discreteVarInfos) {
                if (var != null) {
                    var.recategorize();
                }
            }
        }

        public DiscreteVarInfo[] getDiscreteVarInfos() {
            return discreteVarInfos;
        }

        public void setDiscreteVarInfos(DiscreteVarInfo[] discreteVarInfos) {
            this.discreteVarInfos = discreteVarInfos;
        }

        public int getNumOfCols() {
            return numOfCols;
        }

        public void setNumOfCols(int numOfCols) {
            this.numOfCols = numOfCols;
        }

    }

    public static class DiscreteVarInfo {

        private final String name;
        private final Map<String, Integer> values;

        private final List<String> categories;

        public DiscreteVarInfo(String name) {
            this.name = name;
            this.values = new TreeMap<>();
            this.categories = new ArrayList<>();
        }

        public void recategorize() {
            Set<String> keyset = values.keySet();
            int count = 0;
            for (String key : keyset) {
                values.put(key, count++);
                categories.add(key);
            }
        }

        @Override
        public String toString() {
            return "DiscreteVarInfo{" + "name=" + name + ", values=" + values + ", categories=" + categories + '}';
        }

        public String getName() {
            return name;
        }

        public Integer getEncodeValue(String value) {
            return values.get(value);
        }

        public void setValue(String value) {
            this.values.put(value, null);
        }

        public List<String> getCategories() {
            return categories;
        }

    }

}
