 ///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.pitt.dbmi.data.reader.covariance;

import edu.pitt.dbmi.data.reader.DataFileReader;
import edu.pitt.dbmi.data.reader.DataReaderException;
import edu.pitt.dbmi.data.reader.Delimiter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 *
 * Nov 17, 2025 10:53:31 PM
 *
 * @author Kevin V. Bui (kvb2univpitt@gmail.com)
 */
public class FullCovarianceDataFileReader extends AbstractCovarianceDataFileReader implements CovarianceDataReader {

    /**
     * Constructs a FullCovarianceDataFileReader with the specified data file path and delimiter.
     *
     * @param dataFile  the path to the data file to be read.
     * @param delimiter the delimiter used to parse the data file.
     */
    public FullCovarianceDataFileReader(Path dataFile, Delimiter delimiter) {
        super(dataFile, delimiter);
    }

    /**
     * Reads and processes the covariance data file, constructing a {@link CovarianceData} object
     * containing the number of cases, variables, and the covariance matrix data from the file.
     *
     * @return a {@link CovarianceData} object that encapsulates the number of cases, list of variables,
     *         and covariance data matrix.
     * @throws IOException if an error occurs while reading the data file.
     */
    @Override
    public CovarianceData readInData() throws IOException {
        int numOfCases = getNumberOfCases();
        List<String> variables = getVariables();
        double[][] data = getCovarianceData(variables.size());

        return new FullCovarianceData(numOfCases, variables, data);
    }

    private double[][] getCovarianceData(int matrixSize) throws IOException {
        double[][] data = new double[matrixSize][matrixSize];

        try (InputStream in = Files.newInputStream(this.dataFile, StandardOpenOption.READ)) {
            boolean skip = false;
            boolean hasSeenNonblankChar = false;
            boolean hasQuoteChar = false;

            byte delimChar = this.delimiter.getByteValue();

            // comment marker check
            byte[] comment = this.commentMarker.getBytes();
            int cmntIndex = 0;
            boolean checkForComment = comment.length > 0;

            int lineDataNum = 1;
            int lineNum = 1;
            int colNum = 0;
            int col = 0;
            int row = 0;
            final int dataLimit = matrixSize - 1;

            StringBuilder dataBuilder = new StringBuilder();
            byte prevChar = -1;
            byte[] buffer = new byte[DataFileReader.BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < len && !Thread.currentThread().isInterrupted(); i++) {
                    byte currChar = buffer[i];

                    if (currChar == DataFileReader.CARRIAGE_RETURN || currChar == DataFileReader.LINE_FEED) {
                        if (currChar == DataFileReader.LINE_FEED && prevChar == DataFileReader.CARRIAGE_RETURN) {
                            prevChar = DataFileReader.LINE_FEED;
                            continue;
                        }

                        if (hasSeenNonblankChar && !skip) {
                            if (lineDataNum >= 3) {
                                if (row >= matrixSize) {
                                    String errMsg = String.format("Excess data on line %d.  Extracted %d rows but expected %d.", lineNum, row + 1, matrixSize);
                                    throw new DataReaderException(errMsg);
                                }
                                if (col > dataLimit) {
                                    String errMsg = String.format("Excess data on line %d.  Extracted %d value(s) but expected %d.", lineNum, col + 1, matrixSize);
                                    throw new DataReaderException(errMsg);
                                } else if (col < dataLimit) {
                                    String errMsg = String.format("Insufficent data on line %d.  Extracted %d value(s) but expected %d.", lineNum, col + 1, matrixSize);
                                    throw new DataReaderException(errMsg);
                                } else {
                                    String value = dataBuilder.toString().trim();
                                    dataBuilder.delete(0, dataBuilder.length());

                                    colNum++;
                                    if (value.isEmpty()) {
                                        String errMsg = String.format("Missing value on line %d at column %d.", lineNum, colNum);
                                        throw new DataReaderException(errMsg);
                                    } else {
                                        try {
                                            double covariance = Double.parseDouble(value);
                                            data[row][col] = covariance;
                                        } catch (NumberFormatException exception) {
                                            String errMsg = String.format("Invalid number %s on line %d at column %d.", value, lineNum, colNum);
                                            throw new DataReaderException(errMsg);
                                        }
                                    }
                                }

                                row++;
                            }

                            lineDataNum++;
                        }

                        lineNum++;

                        // clear data
                        dataBuilder.delete(0, dataBuilder.length());

                        // reset states
                        skip = false;
                        hasSeenNonblankChar = false;
                        cmntIndex = 0;
                        col = 0;
                        colNum = 0;
                        checkForComment = comment.length > 0;
                    } else if (!skip) {
                        if (currChar > DataFileReader.SPACE_CHAR) {
                            hasSeenNonblankChar = true;
                        }

                        // skip blank chars at the begining of the line
                        if (currChar <= DataFileReader.SPACE_CHAR && !hasSeenNonblankChar) {
                            continue;
                        }

                        // check for comment marker to skip line
                        if (checkForComment) {
                            if (currChar == comment[cmntIndex]) {
                                cmntIndex++;
                                if (cmntIndex == comment.length) {
                                    skip = true;
                                    prevChar = currChar;
                                    continue;
                                }
                            } else {
                                checkForComment = false;
                            }
                        }

                        if (lineDataNum >= 3) {
                            if (currChar == this.quoteCharacter) {
                                hasQuoteChar = !hasQuoteChar;
                            } else {
                                if (hasQuoteChar) {
                                    dataBuilder.append((char) currChar);
                                } else {
                                    boolean isDelimiter;
                                    if (this.delimiter == Delimiter.WHITESPACE) {
                                        isDelimiter = (currChar <= DataFileReader.SPACE_CHAR) && (prevChar > DataFileReader.SPACE_CHAR);
                                    } else {
                                        isDelimiter = (currChar == delimChar);
                                    }

                                    if (isDelimiter) {
                                        if (row >= matrixSize) {
                                            String errMsg = String.format("Excess data on line %d.  Extracted %d rows but expected %d.", lineNum, row + 1, matrixSize);
                                            throw new DataReaderException(errMsg);
                                        }
                                        if (col > dataLimit) {
                                            String errMsg = String.format("Excess data on line %d.  Extracted %d value(s) but expected %d.", lineNum, col + 1, matrixSize);
                                            throw new DataReaderException(errMsg);
                                        }

                                        String value = dataBuilder.toString().trim();
                                        dataBuilder.delete(0, dataBuilder.length());

                                        colNum++;
                                        if (value.isEmpty()) {
                                            String errMsg = String.format("Missing value on line %d at column %d.", lineNum, colNum);
                                            throw new DataReaderException(errMsg);
                                        } else {
                                            try {
                                                double covariance = Double.parseDouble(value);
                                                data[row][col] = covariance;
                                            } catch (NumberFormatException exception) {
                                                String errMsg = String.format("Invalid number %s on line %d at column %d.", value, lineNum, colNum);
                                                throw new DataReaderException(errMsg);
                                            }
                                        }

                                        col++;
                                    } else {
                                        dataBuilder.append((char) currChar);
                                    }
                                }
                            }
                        }
                    }

                    prevChar = currChar;
                }
            }

            if (hasSeenNonblankChar && !skip) {
                if (lineDataNum >= 3) {
                    if (row >= matrixSize) {
                        String errMsg = String.format("Excess data on line %d.  Extracted %d rows but expected %d.", lineNum, row + 1, matrixSize);
                        throw new DataReaderException(errMsg);
                    }
                    if (col > dataLimit) {
                        String errMsg = String.format("Excess data on line %d.  Extracted %d value(s) but expected %d.", lineNum, col + 1, matrixSize);
                        throw new DataReaderException(errMsg);
                    } else if (col < dataLimit) {
                        String errMsg = String.format("Insufficent data on line %d.  Extracted %d value(s) but expected %d.", lineNum, col + 1, matrixSize);
                        throw new DataReaderException(errMsg);
                    } else {
                        String value = dataBuilder.toString().trim();
                        dataBuilder.delete(0, dataBuilder.length());

                        colNum++;
                        if (value.isEmpty()) {
                            String errMsg = String.format("Missing value on line %d at column %d.", lineNum, colNum);
                            throw new DataReaderException(errMsg);
                        } else {
                            try {
                                double covariance = Double.parseDouble(value);
                                data[row][col] = covariance;
                            } catch (NumberFormatException exception) {
                                String errMsg = String.format("Invalid number %s on line %d at column %d.", value, lineNum, colNum);
                                throw new DataReaderException(errMsg);
                            }
                        }

                        row++;
                    }
                }
            }

            if (row < matrixSize) {
                String errMsg = String.format("Insufficient data.  Expect %d rows but only read in %d.", matrixSize, row);
                throw new DataReaderException(errMsg);
            }
        }

        checkSymmetry(data);

        return data;
    }

    private void checkSymmetry(double[][] data) throws DataReaderException {
        for (int i = 0; i < data.length; i++) {
            for (int j = 0; j < data[i].length; j++) {
                if (i != j) {
                    if (Double.compare(data[i][j], data[j][i]) != 0) {
                        String errMsg = String.format("Non-symmetric matrix.  COV(%d,%d)=%f is not equal to COV(%d,%d)=%f.", i, j, data[i][j], j, i, data[j][i]);
                        throw new DataReaderException(errMsg);
                    }
                }
            }
        }
    }

    private static final class FullCovarianceData implements CovarianceData {

        private final int numberOfCases;
        private final List<String> variables;
        private final double[][] data;

        private FullCovarianceData(int numberOfCases, List<String> variables, double[][] data) {
            this.numberOfCases = numberOfCases;
            this.variables = variables;
            this.data = data;
        }

        /**
         * @return the number of cases in the data.
         */
        @Override
        public int getNumberOfCases() {
            return this.numberOfCases;
        }

        /**
         * @return the number of variables in the data.
         */
        @Override
        public List<String> getVariables() {
            return this.variables;
        }

        /**
         * @return the data in a 2D array.
         */
        @Override
        public double[][] getData() {
            return this.data;
        }
    }

}
