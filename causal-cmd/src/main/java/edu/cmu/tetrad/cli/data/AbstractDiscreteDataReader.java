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

import static edu.cmu.tetrad.cli.data.AbstractDataReader.NEW_LINE;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    protected VariableAnalysis analyzeVariables(Set<String> excludeVariables) throws IOException {
        VariableAnalysis variableAnalysis = new VariableAnalysis();

        try (FileChannel fc = new RandomAccessFile(dataFile.toFile(), "r").getChannel()) {
            MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            StringBuilder dataBuilder = new StringBuilder();
            byte currentChar = -1;
            byte prevChar = NEW_LINE;

            // read in variables
            int numOfCols = 0;
            int col = 0;
            VarInfo[] varInfos = new VarInfo[countNumberOfColumns()];
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delimiter || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    String value = dataBuilder.toString();
                    dataBuilder.delete(0, dataBuilder.length());
                    if (value.length() > 0) {
                        if (excludeVariables.contains(value)) {
                            varInfos[col] = new VarInfo(value, true);
                        } else {
                            varInfos[col] = new VarInfo(value);
                            numOfCols++;
                        }
                    } else {
                        String errMsg = String.format("Missing variable name at column %d.", col + 1);
                        LOGGER.error(errMsg);
                        throw new IOException(errMsg);
                    }

                    col++;
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
                if (currentChar == delimiter) {
                    String errMsg = String.format("Missing variable name at column %d.", col + 1);
                    LOGGER.error(errMsg);
                    throw new IOException(errMsg);
                } else {
                    String value = dataBuilder.toString();
                    dataBuilder.delete(0, dataBuilder.length());
                    if (value.length() > 0) {
                        if (excludeVariables.contains(value)) {
                            varInfos[col] = new VarInfo(value, true);
                        } else {
                            varInfos[col] = new VarInfo(value);
                            numOfCols++;
                        }
                    } else {
                        String errMsg = String.format("Missing variable name at column %d.", col + 1);
                        LOGGER.error(errMsg);
                        throw new IOException(errMsg);
                    }
                }
            }

            int numOfRows = 0;
            col = 0;
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delimiter || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    String value = dataBuilder.toString();
                    dataBuilder.delete(0, dataBuilder.length());
                    if (!varInfos[col].excluded) {
                        if (value.length() > 0) {
                            varInfos[col].setValue(value);
                            try {
                                Integer.parseInt(value);
                            } catch (NumberFormatException exception) {
                                String errMsg = String.format("Unable to parse data at line %d column %d.", numOfRows + 2, col + 1);
                                LOGGER.error(errMsg, exception);
                                throw new IOException(errMsg);
                            }
                        } else {
                            String errMsg = String.format("Missing value at line %d column %d.", numOfRows + 2, col + 1);
                            LOGGER.error(errMsg);
                            throw new IOException(errMsg);
                        }
                    }

                    col++;
                    if (currentChar == NEW_LINE) {
                        col = 0;
                        numOfRows++;
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
                if (!varInfos[col].excluded) {
                    if (currentChar == delimiter) {
                        String errMsg = String.format("Missing value at line %d column %d.", numOfRows + 2, col + 1);
                        LOGGER.error(errMsg);
                        throw new IOException(errMsg);
                    } else {
                        String value = dataBuilder.toString();
                        dataBuilder.delete(0, dataBuilder.length());
                        if (value.length() > 0) {
                            varInfos[col].setValue(value);
                        } else {
                            String errMsg = String.format("Unable to parse data at line %d column %d.", numOfRows + 2, col + 1);
                            LOGGER.error(errMsg);
                            throw new IOException(errMsg);
                        }
                    }
                }
            }

            variableAnalysis.setNumOfCols(numOfCols);
            variableAnalysis.setNumOfRows(numOfRows);
            variableAnalysis.setVarInfos(varInfos);
        }

        return variableAnalysis;
    }

    protected VariableAnalysis analyzeVariables() throws IOException {
        VariableAnalysis variableAnalysis = new VariableAnalysis();

        try (FileChannel fc = new RandomAccessFile(dataFile.toFile(), "r").getChannel()) {
            MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            StringBuilder dataBuilder = new StringBuilder();
            byte currentChar = -1;
            byte prevChar = NEW_LINE;

            // read in variables
            int numOfCols = 0;
            VarInfo[] varInfos = new VarInfo[countNumberOfColumns()];
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delimiter || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    String value = dataBuilder.toString();
                    dataBuilder.delete(0, dataBuilder.length());
                    if (value.length() > 0) {
                        varInfos[numOfCols] = new VarInfo(value);
                    } else {
                        String errMsg = String.format("Missing variable name at column %d.", numOfCols + 1);
                        LOGGER.error(errMsg);
                        throw new IOException(errMsg);
                    }

                    numOfCols++;
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
                if (currentChar == delimiter) {
                    String errMsg = String.format("Missing variable name at column %d.", numOfCols + 1);
                    LOGGER.error(errMsg);
                    throw new IOException(errMsg);
                } else {
                    String value = dataBuilder.toString();
                    dataBuilder.delete(0, dataBuilder.length());
                    if (value.length() > 0) {
                        varInfos[numOfCols] = new VarInfo(value);
                    } else {
                        String errMsg = String.format("Missing variable name at column %d.", numOfCols + 1);
                        LOGGER.error(errMsg);
                        throw new IOException(errMsg);
                    }
                    numOfCols++;
                }
            }

            int numOfRows = 0;
            int col = 0;
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delimiter || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    String value = dataBuilder.toString();
                    dataBuilder.delete(0, dataBuilder.length());
                    if (value.length() > 0) {
                        varInfos[col].setValue(value);
                        try {
                            Integer.parseInt(value);
                        } catch (NumberFormatException exception) {
                            String errMsg = String.format("Unable to parse data at line %d column %d.", numOfRows + 2, col + 1);
                            LOGGER.error(errMsg, exception);
                            throw new IOException(errMsg);
                        }
                    } else {
                        String errMsg = String.format("Missing value at line %d column %d.", numOfRows + 2, col + 1);
                        LOGGER.error(errMsg);
                        throw new IOException(errMsg);
                    }

                    col++;
                    if (currentChar == NEW_LINE) {
                        col = 0;
                        numOfRows++;
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
                if (currentChar == delimiter) {
                    String errMsg = String.format("Missing value at line %d column %d.", numOfRows + 2, col + 1);
                    LOGGER.error(errMsg);
                    throw new IOException(errMsg);
                } else {
                    String value = dataBuilder.toString();
                    dataBuilder.delete(0, dataBuilder.length());
                    if (value.length() > 0) {
                        varInfos[col].setValue(value);
                    } else {
                        String errMsg = String.format("Unable to parse data at line %d column %d.", numOfRows + 2, col + 1);
                        LOGGER.error(errMsg);
                        throw new IOException(errMsg);
                    }
                }
            }

            variableAnalysis.setNumOfCols(numOfCols);
            variableAnalysis.setNumOfRows(numOfRows);
            variableAnalysis.setVarInfos(varInfos);
        }

        return variableAnalysis;
    }

    public static class VariableAnalysis {

        private VarInfo[] varInfos;
        private int numOfRows;
        private int numOfCols;

        public VariableAnalysis() {
        }

        public VariableAnalysis(VarInfo[] varInfos, int numOfRows, int numOfCols) {
            this.varInfos = varInfos;
            this.numOfRows = numOfRows;
            this.numOfCols = numOfCols;
        }

        public void recategorizeDiscreteVariables() {
            for (VarInfo varInfo : varInfos) {
                varInfo.recategorize();
            }
        }

        public VarInfo[] getVarInfos() {
            return varInfos;
        }

        public void setVarInfos(VarInfo[] varInfos) {
            this.varInfos = varInfos;
        }

        public int getNumOfRows() {
            return numOfRows;
        }

        public void setNumOfRows(int numOfRows) {
            this.numOfRows = numOfRows;
        }

        public int getNumOfCols() {
            return numOfCols;
        }

        public void setNumOfCols(int numOfCols) {
            this.numOfCols = numOfCols;
        }

    }

    public static class VarInfo {

        private final String name;
        private final Map<String, Integer> values;
        private boolean excluded;

        private final List<String> categories;

        public VarInfo(String name, boolean excluded) {
            this.name = name;
            this.values = new HashMap<>();
            this.excluded = excluded;
            this.categories = new ArrayList<>();
        }

        public VarInfo(String name) {
            this(name, false);
        }

        @Override
        public String toString() {
            return "VarInfo{" + "name=" + name + ", values=" + values + ", excluded=" + excluded + ", categories=" + categories + '}';
        }

        public void recategorize() {
            if (!excluded) {
                Set<String> keyset = values.keySet();
                int[] val = new int[keyset.size()];
                int index = 0;
                for (String key : keyset) {
                    val[index++] = Integer.parseInt(key);
                }
                Arrays.sort(val);
                values.clear();
                for (int i = 0; i < val.length; i++) {
                    String data = Integer.toString(val[i]);
                    values.put(data, i);
                    categories.add(data);
                }
            }
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

        public boolean isExcluded() {
            return excluded;
        }

        public void setExcluded(boolean excluded) {
            this.excluded = excluded;
        }

        public List<String> getCategories() {
            return categories;
        }

    }

}
