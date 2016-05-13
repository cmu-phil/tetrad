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

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.Node;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * May 2, 2016 11:32:07 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public abstract class AbstractContinuousDataReader extends AbstractDataReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractContinuousDataReader.class);

    public AbstractContinuousDataReader(Path dataFile, char delimiter) {
        super(dataFile, delimiter);
    }

    protected ContinuousVariableAnalysis analyzeData(Set<String> excludedVariables) throws IOException {
        ContinuousVariableAnalysis variableAnalysis = new ContinuousVariableAnalysis();
        extractVariables(excludedVariables, variableAnalysis);

        return variableAnalysis;
    }

    protected void extractVariables(Set<String> excludedVariables, ContinuousVariableAnalysis variableAnalysis) throws IOException {
        List<Integer> excludedVarIndices = new LinkedList<>();
        List<Node> nodes = new LinkedList<>();
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
                        if (excludedVariables.contains(value)) {
                            excludedVarIndices.add(index);
                        } else {
                            nodes.add(new ContinuousVariable(value));
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
                        if (excludedVariables.contains(value)) {
                            excludedVarIndices.add(index);
                        } else {
                            nodes.add(new ContinuousVariable(value));
                        }
                    } else {
                        String errMsg = String.format("Missing variable name at column %d.", index + 1);
                        LOGGER.error(errMsg);
                        throw new IOException(errMsg);
                    }
                }
            }
        }

        // add a dummy index if there are no variables to exclude
        if (excludedVarIndices.isEmpty()) {
            excludedVarIndices.add(-1);
        }

        int[] excludedIndices = new int[excludedVarIndices.size()];
        int index = 0;
        for (Integer excludedIndex : excludedVarIndices) {
            excludedIndices[index++] = excludedIndex;
        }
        variableAnalysis.setExcludedIndices(excludedIndices);

        variableAnalysis.setVariables(nodes);
    }

    public static class ContinuousVariableAnalysis {

        private int[] excludedIndices;
        private List<Node> variables;

        public ContinuousVariableAnalysis() {
        }

        public int[] getExcludedIndices() {
            return excludedIndices;
        }

        public void setExcludedIndices(int[] excludedIndices) {
            this.excludedIndices = excludedIndices;
        }

        public List<Node> getVariables() {
            return variables;
        }

        public void setVariables(List<Node> variables) {
            this.variables = variables;
        }

    }

}
