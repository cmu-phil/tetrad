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

package edu.pitt.dbmi.data.reader.tabular;

import edu.pitt.dbmi.data.reader.DataFileReader;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Feb 25, 2017 1:36:46 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public abstract class AbstractTabularColumnFileReader extends DataFileReader {

    /**
     * Constructor.
     *
     * @param dataFile  The data file.
     * @param delimiter The delimiter.
     */
    public AbstractTabularColumnFileReader(Path dataFile, Delimiter delimiter) {
        super(dataFile, delimiter);
    }

    /**
     * Get the column numbers for the specified column names.
     *
     * @param columnNames the column names
     * @return the column numbers
     * @throws java.io.IOException if an I/O error occurs
     */
    protected int[] toColumnNumbers(Set<String> columnNames) throws IOException {
        List<Integer> colNums = new LinkedList<>();

        try (InputStream in = Files.newInputStream(this.dataFile, StandardOpenOption.READ)) {
            boolean skip = false;
            boolean hasSeenNonblankChar = false;
            boolean hasQuoteChar = false;
            boolean finished = false;

            byte delimChar = this.delimiter.getByteValue();
            byte prevChar = -1;

            // comment marker check
            byte[] comment = this.commentMarker.getBytes();
            int cmntIndex = 0;
            boolean checkForComment = comment.length > 0;

            int colNum = 0;
            StringBuilder dataBuilder = new StringBuilder();

            byte[] buffer = new byte[DataFileReader.BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !finished && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < len && !finished && !Thread.currentThread().isInterrupted(); i++) {
                    byte currChar = buffer[i];

                    if (currChar == DataFileReader.CARRIAGE_RETURN || currChar == DataFileReader.LINE_FEED) {
                        finished = hasSeenNonblankChar && !skip;
                        if (finished) {
                            String value = dataBuilder.toString().trim();
                            dataBuilder.delete(0, dataBuilder.length());

                            colNum++;
                            if (columnNames.contains(value)) {
                                colNums.add(colNum);
                            }
                        } else {
                            dataBuilder.delete(0, dataBuilder.length());
                        }

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
                                    if (columnNames.contains(value)) {
                                        colNums.add(colNum);
                                    }
                                } else {
                                    dataBuilder.append((char) currChar);
                                }
                            }
                        }
                    }

                    prevChar = currChar;
                }
            }

            finished = hasSeenNonblankChar && !skip;
            if (finished) {
                String value = dataBuilder.toString().trim();
                dataBuilder.delete(0, dataBuilder.length());

                colNum++;
                if (columnNames.contains(value)) {
                    colNums.add(colNum);
                }
            }
        }

        return colNums.stream().mapToInt(e -> e).toArray();
    }

    /**
     * Strip the specified character from the word.
     *
     * @param word      the word
     * @param character the character to strip
     * @return the word with the specified character stripped
     */
    protected String stripCharacter(String word, byte character) {
        StringBuilder dataBuilder = new StringBuilder();
        for (byte currChar : word.getBytes()) {
            if (currChar != character) {
                dataBuilder.append((char) currChar);
            }
        }

        return dataBuilder.toString();
    }

}

