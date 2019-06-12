/*
 * Copyright (C) 2018 University of Pittsburgh.
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
package edu.pitt.dbmi.data.reader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 *
 * Dec 12, 2018 11:16:14 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public abstract class DataFileReader implements DataReader {

    protected static final int BUFFER_SIZE = 1024 * 1024;

    protected static final byte LINE_FEED = '\n';
    protected static final byte CARRIAGE_RETURN = '\r';
    protected static final byte SPACE_CHAR = Delimiter.SPACE.getByteValue();

    protected byte quoteCharacter;
    protected String commentMarker;

    protected final Path dataFile;
    protected final Delimiter delimiter;

    public DataFileReader(Path dataFile, Delimiter delimiter) {
        this.dataFile = dataFile;
        this.delimiter = delimiter;
        this.quoteCharacter = -1;
        this.commentMarker = "";
    }

    /**
     * Counts number of column from the first non-blank line.
     *
     * @return the number of column from the first non-blank line
     * @throws IOException
     */
    protected int countNumberOfColumns() throws IOException {
        int count = 0;

        try (InputStream in = Files.newInputStream(dataFile, StandardOpenOption.READ)) {
            boolean skip = false;
            boolean hasSeenNonblankChar = false;
            boolean hasQuoteChar = false;
            boolean finished = false;

            byte delimChar = delimiter.getByteValue();
            byte prevChar = -1;

            // comment marker check
            byte[] comment = commentMarker.getBytes();
            int cmntIndex = 0;
            boolean checkForComment = comment.length > 0;

            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !finished && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < len && !finished && !Thread.currentThread().isInterrupted(); i++) {
                    byte currChar = buffer[i];

                    if (currChar == CARRIAGE_RETURN || currChar == LINE_FEED) {
                        finished = hasSeenNonblankChar && !skip;
                        if (finished) {
                            count++;
                        }

                        // reset states
                        skip = false;
                        hasSeenNonblankChar = false;
                        cmntIndex = 0;
                        checkForComment = comment.length > 0;
                    } else if (!skip) {
                        if (currChar > SPACE_CHAR) {
                            hasSeenNonblankChar = true;
                        }

                        // skip blank chars at the begining of the line
                        if (currChar <= SPACE_CHAR && !hasSeenNonblankChar) {
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

                        if (currChar == quoteCharacter) {
                            hasQuoteChar = !hasQuoteChar;
                        } else {
                            if (!hasQuoteChar) {
                                switch (delimiter) {
                                    case WHITESPACE:
                                        if (currChar <= SPACE_CHAR && prevChar > SPACE_CHAR) {
                                            count++;
                                        }
                                        break;
                                    default:
                                        if (currChar == delimChar) {
                                            count++;
                                        }
                                }
                            }
                        }
                    }

                    prevChar = currChar;
                }
            }

            // case when no newline char at end of file
            finished = hasSeenNonblankChar && !skip;
            if (finished) {
                count++;
            }
        }

        return count;
    }

    /**
     * Counts number of non-blank lines.
     *
     * @return the number of non-blank and non-commented lines
     * @throws IOException
     */
    protected int countNumberOfLines() throws IOException {
        int count = 0;

        try (InputStream in = Files.newInputStream(dataFile, StandardOpenOption.READ)) {
            boolean skip = false;
            boolean hasSeenNonblankChar = false;

            // comment marker check
            byte[] comment = commentMarker.getBytes();
            int cmntIndex = 0;
            boolean checkForComment = comment.length > 0;

            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1 && !Thread.currentThread().isInterrupted()) {
                for (int i = 0; i < len; i++) {
                    byte currChar = buffer[i];
                    if (currChar == CARRIAGE_RETURN || currChar == LINE_FEED) {
                        if (!skip && cmntIndex > 0) {
                            count++;
                        }

                        // reset states
                        skip = false;
                        hasSeenNonblankChar = false;
                        cmntIndex = 0;
                    } else {
                        if (!skip) {
                            if (currChar > SPACE_CHAR) {
                                hasSeenNonblankChar = true;
                            }

                            // skip blank chars at the begining of the line
                            if (currChar <= SPACE_CHAR && !hasSeenNonblankChar) {
                                continue;
                            }

                            if (checkForComment) {
                                if (currChar == comment[cmntIndex]) {
                                    cmntIndex++;
                                    if (cmntIndex == comment.length) {
                                        skip = true;
                                    }

                                    continue;
                                }
                            }

                            count++;
                            skip = true;
                        }
                    }
                }
            }

            // case when no newline char at end of file
            if (!skip && cmntIndex > 0) {
                count++;
            }
        }

        return count;
    }

    @Override
    public void setQuoteCharacter(char quoteCharacter) {
        this.quoteCharacter = Character.isDefined(quoteCharacter)
                ? (byte) quoteCharacter
                : (byte) -1;
    }

    @Override
    public void setCommentMarker(String commentMarker) {
        this.commentMarker = (commentMarker == null)
                ? ""
                : commentMarker.trim();
    }

}
