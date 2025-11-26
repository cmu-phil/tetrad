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
import java.util.LinkedList;
import java.util.List;

/**
 *
 * Nov 17, 2025 10:55:17 PM
 *
 * @author Kevin V. Bui (kvb2univpitt@gmail.com)
 */
public abstract class AbstractCovarianceDataFileReader extends DataFileReader {

    /**
     * Constructs an AbstractCovarianceDataFileReader with the specified data file and delimiter.
     *
     * @param dataFile  the path to the data file to be read.
     * @param delimiter the delimiter used to parse the data file.
     */
    public AbstractCovarianceDataFileReader(Path dataFile, Delimiter delimiter) {
        super(dataFile, delimiter);
    }

    protected List<String> getVariables() throws IOException {
        List<String> variables = new LinkedList<>();

        try (InputStream in = Files.newInputStream(this.dataFile, StandardOpenOption.READ)) {
            boolean skip = false;
            boolean hasSeenNonblankChar = false;
            boolean hasQuoteChar = false;
            boolean finished = false;

            byte delimChar = this.delimiter.getByteValue();

            // comment marker check
            byte[] comment = this.commentMarker.getBytes();
            int cmntIndex = 0;
            boolean checkForComment = comment.length > 0;

            int lineDataNum = 1;
            int colNum = 0;
            int lineNum = 1;

            StringBuilder dataBuilder = new StringBuilder();
            byte prevChar = -1;
            byte[] buffer = new byte[DataFileReader.BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !finished && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < len && !finished && !Thread.currentThread().isInterrupted(); i++) {
                    byte currChar = buffer[i];

                    if (currChar == DataFileReader.CARRIAGE_RETURN || currChar == DataFileReader.LINE_FEED) {
                        if (currChar == DataFileReader.LINE_FEED && prevChar == DataFileReader.CARRIAGE_RETURN) {
                            prevChar = DataFileReader.LINE_FEED;
                            continue;
                        }

                        if (hasSeenNonblankChar && !skip) {
                            if (lineDataNum == 2) {
                                String value = dataBuilder.toString().trim();
                                dataBuilder.delete(0, dataBuilder.length());

                                colNum++;
                                if (value.isEmpty()) {
                                    String errMsg = String.format("Missing variable name on line %d at column %d.", lineNum, colNum);
                                    throw new DataReaderException(errMsg);
                                } else {
                                    variables.add(value);
                                }
                            }

                            lineDataNum++;
                            finished = lineDataNum > 2;
                        }

                        lineNum++;

                        // clear data
                        dataBuilder.delete(0, dataBuilder.length());

                        // reset states
                        skip = false;
                        hasSeenNonblankChar = false;
                        cmntIndex = 0;
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

                        if (lineDataNum == 2) {
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
                                        String value = dataBuilder.toString().trim();
                                        dataBuilder.delete(0, dataBuilder.length());

                                        colNum++;
                                        if (value.isEmpty()) {
                                            String errMsg = String.format("Missing variable name on line %d at column %d.", lineNum, colNum);
                                            throw new DataReaderException(errMsg);
                                        } else {
                                            variables.add(value);
                                        }
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
                if (lineDataNum == 2) {
                    String value = dataBuilder.toString().trim();
                    dataBuilder.delete(0, dataBuilder.length());

                    colNum++;
                    if (value.isEmpty()) {
                        String errMsg = String.format("Missing variable name on line %d at column %d.", lineNum, colNum);
                        throw new DataReaderException(errMsg);
                    } else {
                        variables.add(value);
                    }
                }
            }
        }

        if (variables.isEmpty()) {
            final String errMsg = "Covariance file does not contain variable names.";
            throw new DataReaderException(errMsg);
        }

        return variables;
    }

    protected int getNumberOfCases() throws IOException {
        int numOfCases = 0;

        try (InputStream in = Files.newInputStream(this.dataFile, StandardOpenOption.READ)) {
            boolean skip = false;
            boolean hasSeenNonblankChar = false;
            boolean finished = false;

            // comment marker check
            byte[] comment = this.commentMarker.getBytes();
            int cmntIndex = 0;
            boolean checkForComment = comment.length > 0;

            int lineNum = 1;

            StringBuilder dataBuilder = new StringBuilder();
            byte prevChar = -1;
            byte[] buffer = new byte[DataFileReader.BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !finished && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < len && !finished && !Thread.currentThread().isInterrupted(); i++) {
                    byte currChar = buffer[i];

                    if (currChar == DataFileReader.CARRIAGE_RETURN || currChar == DataFileReader.LINE_FEED) {
                        if (currChar == DataFileReader.LINE_FEED && prevChar == DataFileReader.CARRIAGE_RETURN) {
                            prevChar = DataFileReader.LINE_FEED;
                            continue;
                        }

                        finished = hasSeenNonblankChar && !skip;
                        if (!finished) {
                            lineNum++;

                            // clear data
                            dataBuilder.delete(0, dataBuilder.length());

                            // reset states
                            skip = false;
                            hasSeenNonblankChar = false;
                            cmntIndex = 0;
                            checkForComment = comment.length > 0;
                        }
                    } else {
                        if (!skip) {
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

                            if (currChar != this.quoteCharacter) {
                                dataBuilder.append((char) currChar);
                            }
                        }
                    }

                    prevChar = currChar;
                }
            }

            String value = dataBuilder.toString().trim();
            if (value.isEmpty()) {
                String errMsg = String.format("Line %d: Missing number of cases.", lineNum);
                throw new DataReaderException(errMsg);
            } else {
                try {
                    numOfCases += Integer.parseInt(value);
                } catch (NumberFormatException exception) {
                    String errMsg = String.format("Invalid number %s on line %d.", value, lineNum);
                    throw new DataReaderException(errMsg);
                }
            }
        }

        return numOfCases;
    }

}
