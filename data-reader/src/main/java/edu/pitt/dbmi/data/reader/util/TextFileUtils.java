/*
 * Copyright (C) 2019 University of Pittsburgh.
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
package edu.pitt.dbmi.data.reader.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 *
 * Mar 8, 2017 10:51:43 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TextFileUtils {

    protected static final byte LINE_FEED = '\n';
    protected static final byte CARRIAGE_RETURN = '\r';

    protected static final byte SPACE_CHAR = ' ';

    private TextFileUtils() {
    }

    /**
     * Determine the delimiter for a text data file.
     *
     * Reads the first n lines of data in a text file and attempts to infer what
     * delimiter is used.
     *
     * Idea expanded from <a>https://rdrr.io/cran/reader/man/get.delim.html</a>.
     *
     * @param file the file to examine
     * @param n the number of lines to read to make the inference
     * @param skip number of lines to skip at top of file before processing
     * @param comment a comment symbol to ignore lines in files
     * @param quoteCharacter used for grouping characters
     * @param delims the set of delimiters to test for
     * @return
     * @throws IOException
     */
    public static char inferDelimiter(File file, int n, int skip, String comment, char quoteCharacter, char[] delims) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("Parameter file cannot be null.");
        }
        if (n < 0) {
            throw new IllegalArgumentException("Parameter n must be positive integer.");
        }
        if (skip < 0) {
            throw new IllegalArgumentException("Parameter skip must be positive integer.");
        }
        comment = (comment == null) ? "" : comment.trim();

        int[] characters = new int[256];
        try (FileChannel fc = new RandomAccessFile(file, "r").getChannel()) {
            long fileSize = fc.size();
            long position = 0;
            long size = (fileSize > Integer.MAX_VALUE) ? Integer.MAX_VALUE : fileSize;

            ByteBuffer byteBuffer = ByteBuffer.allocate(comment.length());
            byte[] prefix = comment.getBytes();
            int index = 0;
            boolean hasQuoteChar = false;
            boolean reqCheck = prefix.length > 0;
            boolean skipLine = false;
            int lineCount = 0;
            byte quoteChar = (byte) quoteCharacter;
            byte prevNonBlankChar = SPACE_CHAR;
            byte prevChar = -1;
            do {
                MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, position, size);

                while (buffer.hasRemaining() && lineCount < n && !Thread.currentThread().isInterrupted()) {
                    byte currChar = buffer.get();

                    if (skipLine) {
                        if (currChar == CARRIAGE_RETURN || currChar == LINE_FEED) {
                            skipLine = false;
                        }
                    } else {
                        if (currChar == CARRIAGE_RETURN || currChar == LINE_FEED) {
                            byteBuffer.clear();
                            reqCheck = prefix.length > 0;

                            if (!(currChar == LINE_FEED && prevChar == CARRIAGE_RETURN)) {
                                lineCount++;
                            }
                        } else {
                            if (currChar > SPACE_CHAR) {
                                prevNonBlankChar = currChar;
                            }

                            if (reqCheck && prevNonBlankChar > SPACE_CHAR) {
                                if (currChar == prefix[index]) {
                                    index++;
                                    if (index == prefix.length) {
                                        index = 0;
                                        skipLine = true;
                                        prevNonBlankChar = SPACE_CHAR;
                                        byteBuffer.clear();

                                        prevChar = currChar;
                                        continue;
                                    }
                                } else {
                                    index = 0;
                                    reqCheck = false;
                                }
                            }

                            if (reqCheck) {
                                byteBuffer.put(currChar);
                            } else {
                                if (skip > 0) {
                                    skip--;
                                    skipLine = true;
                                    byteBuffer.clear();
                                } else {
                                    if (byteBuffer.position() > 0) {
                                        byteBuffer.flip();
                                        while (byteBuffer.hasRemaining() && !Thread.currentThread().isInterrupted()) {
                                            byte c = byteBuffer.get();
                                            if (c == quoteChar) {
                                                hasQuoteChar = !hasQuoteChar;
                                            } else if (!hasQuoteChar) {
                                                if (c >= 0 && c < characters.length) {
                                                    characters[c]++;
                                                }
                                            }
                                        }
                                        byteBuffer.clear();
                                    }

                                    if (currChar == quoteChar) {
                                        hasQuoteChar = !hasQuoteChar;
                                    } else if (!hasQuoteChar) {
                                        if (currChar >= 0 && currChar < characters.length) {
                                            characters[currChar]++;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    prevChar = currChar;
                }

                position += size;
                if ((position + size) > fileSize) {
                    size = fileSize - position;
                }
            } while (position < fileSize && lineCount < n && !Thread.currentThread().isInterrupted());

            int maxIndex = 0;
            for (int i = 1; i < delims.length && !Thread.currentThread().isInterrupted(); i++) {
                if (characters[delims[maxIndex]] < characters[delims[i]]) {
                    maxIndex = i;
                }
            }

            return delims[maxIndex];
        }

    }
}
