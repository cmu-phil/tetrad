///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.DataUtility;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A utility to read in large tabular dataset efficiently. This class will most
 * likely be replaced as the support for different dataset grows.
 * <p>
 * Apr 30, 2015 11:30:46 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class BigDataSetUtility {

    private static final byte NEW_LINE = '\n';

    private static final byte CARRIAGE_RETURN = '\r';

    private static final byte DOUBLE_QUOTE = '"';

    private static final byte SINGLE_QUOTE = '\'';

    private BigDataSetUtility() {
    }

    /**
     * Read in continuous dataset.
     *
     * @param file dataset
     * @param delimiter a single character used to separate the data
     * @throws IOException
     */
    public static DataSet readInContinuousData(File file, char delimiter) throws IOException {
        byte delim = (byte) delimiter;

        int numRow = DataUtility.countLine(file) - 1;  // exclude the header

        List<Node> nodes = new LinkedList<>();
        double[][] data;
        try (FileChannel fc = new RandomAccessFile(file, "r").getChannel()) {
            MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            StringBuilder dataBuilder = new StringBuilder();
            byte currentChar;
            byte prevChar = NEW_LINE;

            // read in variables
            int numCol = 0;
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delim || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    nodes.add(new ContinuousVariable(dataBuilder.toString().trim()));
                    numCol++;

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

            // take care of cases where there's no newline at the end of the file
            if (prevChar == delim) {
                nodes.add(new ContinuousVariable(dataBuilder.toString().trim()));
                numCol++;
            }
            String leftover = dataBuilder.toString().trim();
            if (leftover.length() > 0) {
                nodes.add(new ContinuousVariable(leftover));
                numCol++;
            }

            // read in data
            data = new double[numRow][numCol];

            int row = 0;
            int col = 0;
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delim || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    String value = dataBuilder.toString().trim();
                    if (value.length() > 0) {
                        try {
                            data[row][col++] = Double.parseDouble(value);
                        } catch (NumberFormatException exception) {
                            throw new IOException(
                                    String.format("Unable to parse data at line %d column %d\n", row + 2, col),
                                    exception);
                        }
                    } else {
                        col++;
                    }

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

            // take care of cases where there's no newline at the end of the file
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

    /**
     * Read in continuous dataset.
     *
     * @param file dataset
     * @param delimiter a single character used to separate the data
     * @param excludeVariables the names of the columns to be excluded
     * @throws IOException
     */
    public static DataSet readInContinuousData(File file, char delimiter, Set<String> excludeVariables) throws IOException {
        if (excludeVariables == null || excludeVariables.isEmpty()) {
            return readInContinuousData(file, delimiter);
        }

        byte delim = (byte) delimiter;
        int numRow = DataUtility.countLine(file) - 1;  // exclude the header
        List<Node> nodes = new LinkedList<>();
        double[][] data;
        try (FileChannel fc = new RandomAccessFile(file, "r").getChannel()) {
            MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            StringBuilder dataBuilder = new StringBuilder();
            byte currentChar;
            byte prevChar = NEW_LINE;

            int[] excludes = new int[excludeVariables.size()];
            for (int i = 0; i < excludes.length; i++) {
                excludes[i] = -1;
            }

            // read in variables
            int numCol = 0;
            int excludeIndex = 0;
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delim || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    String value = dataBuilder.toString().trim();
                    if (excludeVariables.contains(value)) {
                        excludes[excludeIndex++] = numCol;
                    } else {
                        nodes.add(new ContinuousVariable(value));
                    }
                    numCol++;

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

            // take care of cases where there's no newline at the end of the file
            if (prevChar == delim) {
                String leftover = dataBuilder.toString().trim();
                if (excludeVariables.contains(leftover)) {
                    excludes[excludeIndex++] = numCol;
                } else {
                    nodes.add(new ContinuousVariable(leftover));
                }
                numCol++;
            }
            String leftover = dataBuilder.toString().trim();
            if (leftover.length() > 0) {
                if (excludeVariables.contains(leftover)) {
                    excludes[excludeIndex++] = numCol;
                } else {
                    nodes.add(new ContinuousVariable(leftover));
                }
                numCol++;
            }

            // count the number of excluded variables that's actually found in the dataset
            int founded = 0;
            for (int var : excludes) {
                if (var != -1) {
                    founded++;
                }
            }

            if (founded == 0) {
                // read in data normally as if there's no column to exclude
                data = new double[numRow][numCol];
                int row = 0;
                int col = 0;
                while (buffer.hasRemaining()) {
                    currentChar = buffer.get();
                    if (currentChar == CARRIAGE_RETURN) {
                        currentChar = NEW_LINE;
                    }

                    if (currentChar == delim || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                        String value = dataBuilder.toString().trim();
                        if (value.length() > 0) {
                            try {
                                data[row][col++] = Double.parseDouble(value);
                            } catch (NumberFormatException exception) {
                                throw new IOException(
                                        String.format("Unable to parse data at line %d column %d\n", row + 2, col),
                                        exception);
                            }
                        } else {
                            col++;
                        }

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

                // take care of cases where there's no newline at the end of the file
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
                int[] excludedVars = new int[founded];
                int index = 0;
                for (int varIndex : excludes) {
                    if (varIndex != -1) {
                        excludedVars[index++] = varIndex;
                    }
                }

                // read in data
                data = new double[numRow][numCol];
                int row = 0;
                int col = 0;
                int dataCol = 0;
                while (buffer.hasRemaining()) {
                    currentChar = buffer.get();
                    if (currentChar == CARRIAGE_RETURN) {
                        currentChar = NEW_LINE;
                    }

                    if (currentChar == delim || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                        if (Arrays.binarySearch(excludedVars, col) < 0) {
                            String value = dataBuilder.toString().trim();
                            if (value.length() > 0) {
                                try {
                                    data[row][dataCol++] = Double.parseDouble(value);
                                } catch (NumberFormatException exception) {
                                    throw new IOException(
                                            String.format("Unable to parse data at line %d column %d\n", row + 2, col),
                                            exception);
                                }
                            }
                        }

                        dataBuilder.delete(0, dataBuilder.length());
                        col++;
                        if (currentChar == NEW_LINE) {
                            col = 0;
                            dataCol = 0;
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

                // take care of cases where there's no newline at the end of the file
                String value = dataBuilder.toString().trim();
                if (value.length() > 0) {
                    if (Arrays.binarySearch(excludedVars, col) < 0) {
                        try {
                            data[row][dataCol++] = Double.parseDouble(value);
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

    /**
     * Read in continuous dataset.
     *
     * @param file dataset
     * @param delimiter a single character used to separate the data
     * @throws IOException
     * @deprecated use readInContinuousData instead
     */
    public static DataSet readContinuous(File file, char delimiter) throws IOException {
        return readInContinuousData(file, delimiter, Collections.singleton("MULT"));
    }

    public static DataSet readInDiscreteData(File file, char delimiter, Set<String> excludeVariables) throws IOException {
        if (excludeVariables == null || excludeVariables.isEmpty()) {
            return readInDiscreteData(file, delimiter);
        }

        DiscreteDataAnalysis dataAnalysis = analyDiscreteData(file, delimiter, excludeVariables);
        dataAnalysis.recategorizeDiscreteVariables();

        int numOfRows = dataAnalysis.numOfRows;
        int numOfCols = dataAnalysis.numOfCols;

        List<Node> nodes = new ArrayList<>(numOfCols);
        DiscreteVar[] discreteVars = dataAnalysis.getDiscreteVars();
        for (DiscreteVar discreteVar : discreteVars) {
            if (!discreteVar.isExcluded()) {
                nodes.add(new DiscreteVariable(discreteVar.name, discreteVar.categories));
            }
        }

        int[][] data = new int[numOfRows][numOfCols];
        int row = 0;
        int col = 0;
        int colIndex = 0;
        byte delim = (byte) delimiter;
        try (FileChannel fc = new RandomAccessFile(file, "r").getChannel()) {
            MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            StringBuilder dataBuilder = new StringBuilder();
            byte currentChar;
            byte prevChar = NEW_LINE;

            // skip the header
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delim || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    if (currentChar == NEW_LINE) {
                        prevChar = currentChar;
                        break;
                    }
                }

                prevChar = currentChar;
            }

            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delim || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    if (!discreteVars[col].excluded) {
                        String value = dataBuilder.toString().trim();
                        if (value.length() > 0) {
                            data[row][colIndex++] = discreteVars[col].getEncodeValue(value);
                        } else {
                            colIndex++;
                        }
                    }

                    col++;
                    dataBuilder.delete(0, dataBuilder.length());
                    if (currentChar == NEW_LINE) {
                        col = 0;
                        colIndex = 0;
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

            // take care of cases where there's no newline at the end of the file
            String value = dataBuilder.toString().trim();
            if (value.length() > 0) {
                data[row][colIndex++] = discreteVars[col].getEncodeValue(value);
            }
        }

        return new BoxDataSet(new IntDataBox(data), nodes);
    }

    private static DataSet readInDiscreteData(File file, char delimiter) throws IOException {
        DiscreteDataAnalysis dataAnalysis = analyDiscreteData(file, delimiter);
        dataAnalysis.recategorizeDiscreteVariables();

        int numOfRows = dataAnalysis.numOfRows;
        int numOfCols = dataAnalysis.numOfCols;

        List<Node> nodes = new ArrayList<>(numOfCols);
        DiscreteVar[] discreteVars = dataAnalysis.getDiscreteVars();
        for (DiscreteVar discreteVar : discreteVars) {
            nodes.add(new DiscreteVariable(discreteVar.name, discreteVar.categories));
        }

        int[][] data = new int[numOfRows][numOfCols];
        int row = 0;
        int col = 0;
        byte delim = (byte) delimiter;
        try (FileChannel fc = new RandomAccessFile(file, "r").getChannel()) {
            MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            StringBuilder dataBuilder = new StringBuilder();
            byte currentChar;
            byte prevChar = NEW_LINE;

            // skip the header
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delim || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    if (currentChar == NEW_LINE) {
                        prevChar = currentChar;
                        break;
                    }
                }

                prevChar = currentChar;
            }

            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delim || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    String value = dataBuilder.toString().trim();
                    if (value.length() > 0) {
                        data[row][col] = discreteVars[col].getEncodeValue(value);
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

            // take care of cases where there's no newline at the end of the file
            String value = dataBuilder.toString().trim();
            if (value.length() > 0) {
                data[row][col] = discreteVars[col].getEncodeValue(value);
            }
        }

        return new BoxDataSet(new IntDataBox(data), nodes);
    }

    /**
     * Read in discrete dataset.
     *
     * @param file dataset
     * @param delimiter a single character used to separate the data
     * @throws IOException
     * @deprecated use method readInDiscreteData instead
     */
    public static DataSet readDiscrete(File file, char delimiter) throws IOException {
        return readInDiscreteData(file, delimiter, Collections.singleton("MULT"));
    }

    private static DiscreteDataAnalysis analyDiscreteData(File file, char delimiter, Set<String> excludeVariables) throws IOException {
        DiscreteDataAnalysis dataAnalysis = new DiscreteDataAnalysis();
        byte delim = (byte) delimiter;

        List<DiscreteVar> vars = new LinkedList<>();
        try (FileChannel fc = new RandomAccessFile(file, "r").getChannel()) {
            MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            StringBuilder dataBuilder = new StringBuilder();
            byte currentChar;
            byte prevChar = NEW_LINE;

            // read in variables
            int numOfCols = 0;
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delim || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    String value = dataBuilder.toString().trim();
                    if (excludeVariables.contains(value)) {
                        vars.add(new DiscreteVar(value, true));
                    } else {
                        vars.add(new DiscreteVar(value));
                        numOfCols++;
                    }

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

            // take care of cases where there's no newline at the end of the file
            if (prevChar == delim) {
                vars.add(new DiscreteVar(dataBuilder.toString().trim()));
                numOfCols++;
            }
            String leftover = dataBuilder.toString().trim();
            if (leftover.length() > 0) {
                vars.add(new DiscreteVar(leftover));
                numOfCols++;
            }

            DiscreteVar[] discreteVars = vars.toArray(new DiscreteVar[vars.size()]);

            int numOfRows = 0;
            int col = 0;
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delim || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    String value = dataBuilder.toString().trim();
                    if (value.length() > 0) {
                        discreteVars[col++].setValue(value);
                        try {
                            Integer.parseInt(value);
                        } catch (NumberFormatException exception) {
                            throw new IOException(
                                    String.format("Unable to parse data at line %d column %d\n", numOfRows + 2, col),
                                    exception);
                        }
                    } else {
                        col++;
                    }

                    dataBuilder.delete(0, dataBuilder.length());
                    if (currentChar == NEW_LINE) {
                        col = 0;
                        numOfRows++;
                    }
                } else {
                    if (currentChar == SINGLE_QUOTE || currentChar == DOUBLE_QUOTE) {
                        continue;
                    }
                    dataBuilder.append((char) currentChar);
                }

                prevChar = currentChar;
            }

            // take care of cases where there's no newline at the end of the file
            String value = dataBuilder.toString().trim();
            if (value.length() > 0) {
                discreteVars[col++].setValue(value);
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException exception) {
                    throw new IOException(
                            String.format("Unable to parse data at line %d column %d\n", numOfRows + 2, col),
                            exception);
                }

                numOfRows++;
            }

            dataAnalysis.setDiscreteVars(discreteVars);
            dataAnalysis.setNumOfCols(numOfCols);
            dataAnalysis.setNumOfRows(numOfRows);
        }

        return dataAnalysis;
    }

    private static DiscreteDataAnalysis analyDiscreteData(File file, char delimiter) throws IOException {
        DiscreteDataAnalysis dataAnalysis = new DiscreteDataAnalysis();
        byte delim = (byte) delimiter;

        List<DiscreteVar> vars = new LinkedList<>();
        try (FileChannel fc = new RandomAccessFile(file, "r").getChannel()) {
            MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            StringBuilder dataBuilder = new StringBuilder();
            byte currentChar;
            byte prevChar = NEW_LINE;

            // read in variables
            int numOfCols = 0;
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delim || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    vars.add(new DiscreteVar(dataBuilder.toString().trim()));
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

            // take care of cases where there's no newline at the end of the file
            if (prevChar == delim) {
                vars.add(new DiscreteVar(dataBuilder.toString().trim()));
                numOfCols++;
            }
            String leftover = dataBuilder.toString().trim();
            if (leftover.length() > 0) {
                vars.add(new DiscreteVar(leftover));
                numOfCols++;
            }

            DiscreteVar[] discreteVars = vars.toArray(new DiscreteVar[vars.size()]);

            int numOfRows = 0;
            int col = 0;
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delim || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    String value = dataBuilder.toString().trim();
                    if (value.length() > 0) {
                        discreteVars[col++].setValue(value);
                        try {
                            Integer.parseInt(value);
                        } catch (NumberFormatException exception) {
                            throw new IOException(
                                    String.format("Unable to parse data at line %d column %d\n", numOfRows + 2, col),
                                    exception);
                        }
                    } else {
                        col++;
                    }

                    dataBuilder.delete(0, dataBuilder.length());
                    if (currentChar == NEW_LINE) {
                        col = 0;
                        numOfRows++;
                    }
                } else {
                    if (currentChar == SINGLE_QUOTE || currentChar == DOUBLE_QUOTE) {
                        continue;
                    }
                    dataBuilder.append((char) currentChar);
                }

                prevChar = currentChar;
            }

            // take care of cases where there's no newline at the end of the file
            String value = dataBuilder.toString().trim();
            if (value.length() > 0) {
                discreteVars[col++].setValue(value);
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException exception) {
                    throw new IOException(
                            String.format("Unable to parse data at line %d column %d\n", numOfRows + 2, col),
                            exception);
                }

                numOfRows++;
            }

            dataAnalysis.setDiscreteVars(discreteVars);
            dataAnalysis.setNumOfCols(numOfCols);
            dataAnalysis.setNumOfRows(numOfRows);
        }

        return dataAnalysis;
    }

    public static class DiscreteDataAnalysis {

        private DiscreteVar[] discreteVars;
        private int numOfRows;
        private int numOfCols;

        public DiscreteDataAnalysis() {
        }

        public DiscreteDataAnalysis(DiscreteVar[] discreteVars, int numOfRows, int numOfCols) {
            this.discreteVars = discreteVars;
            this.numOfRows = numOfRows;
            this.numOfCols = numOfCols;
        }

        @Override
        public String toString() {
            return "DiscreteDataAnalysis{" + "numOfRows=" + numOfRows
                    + ", numOfCols=" + numOfCols + '}';
        }

        public void recategorizeDiscreteVariables() {
            for (DiscreteVar discreteVar : discreteVars) {
                discreteVar.recategorize();
            }
        }

        public int getNumOfCols() {
            return numOfCols;
        }

        public void setNumOfCols(int numOfCols) {
            this.numOfCols = numOfCols;
        }

        public DiscreteVar[] getDiscreteVars() {
            return discreteVars;
        }

        public void setDiscreteVars(DiscreteVar[] discreteVars) {
            this.discreteVars = discreteVars;
        }

        public int getNumOfRows() {
            return numOfRows;
        }

        public void setNumOfRows(int numOfRows) {
            this.numOfRows = numOfRows;
        }

    }

    /**
     * This class is used to store information on a discrete variable.
     */
    public static class DiscreteVar {

        private final String name;
        private final Map<String, Integer> values;
        private boolean excluded;

        private final List<String> categories;

        public DiscreteVar(String name, boolean excluded) {
            this.name = name;
            this.values = new HashMap<>();
            this.excluded = excluded;
            this.categories = new ArrayList<>();
        }

        public DiscreteVar(String name) {
            this(name, false);
        }

        @Override
        public String toString() {
            return "DiscreteVar{" + "name=" + name + ", values=" + values + ", excluded=" + excluded + ", categories=" + categories + '}';
        }

        public void recategorize() {
            Set<String> keyset = values.keySet();
            int[] val = new int[keyset.size()];
            int index = 0;
            for (String key : keyset) {
                val[index++] = Integer.parseInt(key);
            }
            Arrays.sort(val);
            values.clear();
            for (int i = 0; i < val.length; i++) {
                String data = String.valueOf(val[i]);
                values.put(data, i);
                categories.add(data);
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

    /**
     * Counts the number of column of the first line in the file.
     *
     * @param file dataset
     * @param delimiter a single character used to separate the data
     * @throws IOException
     */
    public static int countColumn(File file, char delimiter) throws IOException {
        int count = 0;

        byte delim = (byte) delimiter;
        try (FileChannel fc = new RandomAccessFile(file, "r").getChannel()) {
            MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            byte currentChar = -1;
            byte prevChar = NEW_LINE;
            while (buffer.hasRemaining()) {
                currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == delim || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                    count++;
                    if (currentChar == NEW_LINE) {
                        break;
                    }
                }

                prevChar = currentChar;
            }

            // take care of cases where there's no newline at the end of the file
            if (!(currentChar == -1 || currentChar == NEW_LINE)) {
                count++;
            }
        }

        return count;
    }

    /**
     * Counts the number of lines that contain data.
     *
     * @param file dataset
     * @throws IOException
     */
    public static int countLine(File file) throws IOException {
        int count = 0;

        try (FileChannel fc = new RandomAccessFile(file, "r").getChannel()) {
            MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            byte prevChar = NEW_LINE;
            while (buffer.hasRemaining()) {
                byte currentChar = buffer.get();
                if (currentChar == CARRIAGE_RETURN) {
                    currentChar = NEW_LINE;
                }

                if (currentChar == NEW_LINE && prevChar != NEW_LINE) {
                    count++;
                }

                prevChar = currentChar;
            }

            if (prevChar != NEW_LINE) {
                count++;
            }
        }

        return count;
    }

}
