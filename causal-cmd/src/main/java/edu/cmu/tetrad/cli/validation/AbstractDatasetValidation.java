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
package edu.cmu.tetrad.cli.validation;

import edu.cmu.tetrad.io.AbstractDataReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * May 12, 2016 3:29:06 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public abstract class AbstractDatasetValidation extends AbstractDataReader {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDatasetValidation.class);

    protected final Set<String> excludedVariables;

    public AbstractDatasetValidation(Set<String> excludedVariables, Path dataFile, char delimiter) {
        super(dataFile, delimiter);
        this.excludedVariables = (excludedVariables == null) ? Collections.EMPTY_SET : excludedVariables;;
    }

    protected VariableAnalysis analyzeVariables(PrintStream stderr) throws IOException {
        VariableAnalysis variableAnalysis = new VariableAnalysis();

        boolean valid = true;
        int numOfCols = 0;
        String[] variables = new String[countNumberOfColumns()];
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
                            variables[index] = value;
                            numOfCols++;
                        }
                    } else {
                        variables[index] = "unknown";
                        String errMsg = String.format("Missing variable name at column %d.", index + 1);
                        stderr.println(errMsg);
                        LOGGER.error(errMsg);
                        valid = false;
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
                    stderr.println(errMsg);
                    LOGGER.error(errMsg);
                    valid = false;
                } else {
                    String value = dataBuilder.toString().trim();
                    dataBuilder.delete(0, dataBuilder.length());
                    if (value.length() > 0) {
                        if (!excludedVariables.contains(value)) {
                            variables[index] = value;
                            numOfCols++;
                        }
                    } else {
                        variables[index] = "unknown";
                        String errMsg = String.format("Missing variable name at column %d.", index + 1);
                        stderr.println(errMsg);
                        LOGGER.error(errMsg);
                        valid = false;
                    }
                }
            }
        }

        variableAnalysis.setNumOfCols(numOfCols);
        variableAnalysis.setVariables(variables);
        variableAnalysis.setValid(valid);

        return variableAnalysis;
    }

    protected class VariableAnalysis {

        private String[] variables;
        private int numOfCols;
        private boolean valid;

        public VariableAnalysis() {
        }

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public String[] getVariables() {
            return variables;
        }

        public void setVariables(String[] variables) {
            this.variables = variables;
        }

        public int getNumOfCols() {
            return numOfCols;
        }

        public void setNumOfCols(int numOfCols) {
            this.numOfCols = numOfCols;
        }

    }

}
