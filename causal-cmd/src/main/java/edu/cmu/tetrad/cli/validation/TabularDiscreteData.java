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
 * May 3, 2016 4:12:51 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TabularDiscreteData extends AbstractDatasetValidation implements DataValidation {

    private static final Logger LOGGER = LoggerFactory.getLogger(TabularDiscreteData.class);

    public TabularDiscreteData(Set<String> excludedVariables, Path dataFile, char delimiter) {
        super(excludedVariables, dataFile, delimiter);
    }

    public TabularDiscreteData(Path dataFile, char delimiter) {
        super(Collections.EMPTY_SET, dataFile, delimiter);
    }

    @Override
    public boolean validate(PrintStream stderr, boolean verbose) {
        boolean valid = true;

        try {
            VariableAnalysis variableAnalysis = analyzeVariables(stderr);
            valid = variableAnalysis.isValid() && valid;
            valid = analyzeData(stderr, variableAnalysis) && valid;
        } catch (IOException exception) {
            String errMsg = String.format("Error during reading in file '%s'.", dataFile.getFileName().toString());
            System.err.println(errMsg);
            LOGGER.error(errMsg, exception);
            valid = false;
        }

        return valid;
    }

    private boolean analyzeData(PrintStream stderr, VariableAnalysis variableAnalysis) throws IOException {
        boolean valid = true;

        String[] variables = variableAnalysis.getVariables();
        int numOfCols = variableAnalysis.getNumOfCols();
        int maxNumOfCols = variables.length;
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
                    String value = dataBuilder.toString();
                    dataBuilder.delete(0, dataBuilder.length());
                    if (colCount < maxNumOfCols) {
                        String variable = variables[colCount];
                        if (variable != null) {
                            col++;
                            if (value.length() == 0) {
                                String errMsg = String.format("Missing data for variable '%s' at line %d column %d.", variable, row + 2, colCount + 1);
                                stderr.println(errMsg);
                                LOGGER.error(errMsg);
                                valid = false;
                            }
                        }
                    } else {
                        String errMsg = String.format("Number of columns exceeded at line %d.  Expect %d column(s) but found %d.", row + 2, maxNumOfCols, colCount + 1);
                        stderr.println(errMsg);
                        LOGGER.error(errMsg);
                        valid = false;
                    }

                    colCount++;
                    if (currentChar == NEW_LINE) {
                        if (col < numOfCols) {
                            String errMsg = String.format("Insufficient number of columns at line %d.  Expect %d column(s) but found %d.", row + 2, numOfCols, col);
                            stderr.println(errMsg);
                            LOGGER.error(errMsg);
                            valid = false;
                        }
                        colCount = 0;
                        col = 0;
                        row++;
                    }
                } else if (currentChar > SPACE && (currentChar != SINGLE_QUOTE && currentChar != DOUBLE_QUOTE)) {
                    dataBuilder.append((char) currentChar);
                }

                prevChar = currentChar;
            }
            if (currentChar > -1 && currentChar != NEW_LINE) {
                if (colCount < maxNumOfCols) {
                    String value = dataBuilder.toString();
                    dataBuilder.delete(0, dataBuilder.length());

                    String variable = variables[colCount];
                    if (variable != null) {
                        col++;
                        if (value.length() == 0) {
                            String errMsg = String.format("Missing data for variable '%s' at line %d column %d.", variable, row + 2, colCount + 1);
                            stderr.println(errMsg);
                            LOGGER.error(errMsg);
                            valid = false;
                        }
                    }
                } else {
                    String errMsg = String.format("Number of columns exceeded at line %d.  Expect %d column(s) but found %d.", row + 2, maxNumOfCols, colCount + 1);
                    LOGGER.error(errMsg);
                    throw new IOException(errMsg);
                }
            }
        }

        return valid;
    }

}
