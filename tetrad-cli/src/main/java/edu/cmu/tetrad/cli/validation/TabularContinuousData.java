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

import edu.cmu.tetrad.cli.data.AbstractDataReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

/**
 *
 * Mar 4, 2016 3:13:20 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TabularContinuousData extends AbstractDataReader implements DataValidation {

    public TabularContinuousData(Path dataFile, char delimiter) {
        super(dataFile, delimiter);
    }

    @Override
    public boolean validate(PrintStream printStream) {
        boolean valid = true;

        try {
            int numOfCols = countNumberOfColumns();
            String[] variables = new String[numOfCols];
            try (FileChannel fc = new RandomAccessFile(dataFile.toFile(), "r").getChannel()) {
                MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
                StringBuilder dataBuilder = new StringBuilder();

                int col = 0;
                byte currentChar = -1;
                byte prevChar = NEW_LINE;
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
                            variables[col - 1] = value;
                        } else {
                            printStream.printf("Missing variable name at column %d.", col);
                            printStream.println();
                        }

                        if (currentChar == NEW_LINE) {
                            prevChar = currentChar;
                            break;
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
                        printStream.printf("Missing variable name at column %d.", col);
                        printStream.println();
                    } else {
                        String value = dataBuilder.toString();
                        dataBuilder.delete(0, dataBuilder.length());
                        if (value.length() > 0) {
                            variables[col - 1] = value;
                        } else {
                            printStream.printf("Missing variable name at column %d.", col);
                            printStream.println();
                        }
                    }
                }

                col = 0;
                int row = 1;
                while (buffer.hasRemaining()) {
                    currentChar = buffer.get();
                    if (currentChar == CARRIAGE_RETURN) {
                        currentChar = NEW_LINE;
                    }

                    if (currentChar == delimiter || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                        col++;
                        String value = dataBuilder.toString();
                        dataBuilder.delete(0, dataBuilder.length());

                        if (col > numOfCols) {
                            printStream.printf("Column limit exceeded at row %d. Expect %d column(s) but found %d.", row + 1, numOfCols, col);
                            printStream.println();
                        } else {
                            if (value.length() > 0) {
                                try {
                                    Double.parseDouble(value);
                                } catch (NumberFormatException exception) {
                                    String var = variables[col - 1];
                                    printStream.println((var == null)
                                            ? String.format("Unable to parse data '%s' for unknown variable at row %d column %d.", value, row + 1, col)
                                            : String.format("Unable to parse data '%s' for variable '%s' at row %d column %d.", value, var, row + 1, col));
                                }
                            } else {
                                String var = variables[col - 1];
                                printStream.println((var == null)
                                        ? String.format("Missing data for unknown variable at row %d column %d.", row + 1, col)
                                        : String.format("Missing data for variable '%s' at row %d column %d.", var, row + 1, col));
                            }
                        }

                        if (currentChar == NEW_LINE) {
                            if (col < numOfCols) {
                                printStream.printf("Insufficient data at row %d. Expect %d column(s) but found %d.", row + 1, numOfCols, col);
                                printStream.println();
                            }
                            col = 0;
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
                    if (col > numOfCols) {
                        printStream.printf("Column limit exceeded at row %d. Expect %d column(s) but found %d.", row + 1, numOfCols, col);
                        printStream.println();
                    } else if (col < numOfCols) {
                        printStream.printf("Insufficient data at row %d. Expect %d column(s) but found %d.", row + 1, numOfCols, col);
                        printStream.println();
                    } else {
                        if (currentChar == delimiter) {
                            String var = variables[col - 1];
                            printStream.println((var == null)
                                    ? String.format("Missing data for unknown variable at row %d column %d.", row + 1, col)
                                    : String.format("Missing data for variable '%s' at row %d column %d.", var, row + 1, col));
                        } else {
                            String value = dataBuilder.toString();
                            dataBuilder.delete(0, dataBuilder.length());
                            if (value.length() > 0) {
                                try {
                                    Double.parseDouble(value);
                                } catch (NumberFormatException exception) {
                                    String var = variables[col - 1];
                                    printStream.println((var == null)
                                            ? String.format("Unable to parse data '%s' for unknown variable at row %d column %d.", value, row + 1, col)
                                            : String.format("Unable to parse data '%s' for variable '%s' at row %d column %d.", value, var, row + 1, col));
                                }
                            } else {
                                String var = variables[col - 1];
                                printStream.println((var == null)
                                        ? String.format("Missing data for unknown variable at row %d column %d.", row + 1, col)
                                        : String.format("Missing data for variable '%s' at row %d column %d.", var, row + 1, col));
                            }
                        }
                    }
                }
            }
        } catch (IOException exception) {
            printStream.println(exception.getMessage());
            valid = false;
        }

        return valid;
    }

}
