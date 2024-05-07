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
package edu.pitt.dbmi.data.reader.preview;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Feb 20, 2017 2:13:13 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class BasicDataPreviewer extends AbstractDataPreviewer implements DataPreviewer {

    /**
     * Constructor.
     *
     * @param dataFile The data file.
     */
    public BasicDataPreviewer(Path dataFile) {
        super(dataFile);
    }

    /**
     * Get the previews of the data file.
     *
     * @param fromLine        the starting line number
     * @param toLine          the ending line number
     * @param numOfCharacters the number of characters to preview
     * @return the previews
     * @throws java.io.IOException if an I/O error occurs
     */
    @Override
    public List getPreviews(int fromLine, int toLine, int numOfCharacters) throws IOException {
        // parameter validations
        checkLineNumberParameter(fromLine, toLine);
        checkCharacterNumberParameter(numOfCharacters);
        if (toLine == 0 || numOfCharacters == 0) {
            return Collections.EMPTY_LIST;
        }

        List<String> linePreviews = new LinkedList<>();
        try {
            getPreviews(fromLine, toLine, numOfCharacters, linePreviews);
        } catch (ClosedByInterruptException exception) {
//            BasicDataPreviewer.LOGGER.error("", exception);
        }

        return linePreviews;
    }

    /**
     * Get the previews of the data file.
     *
     * @param fromLine        the starting line number
     * @param toLine          the ending line number
     * @param numOfCharacters the number of characters to preview
     * @param list            the list to store the previews
     * @throws java.io.IOException if an I/O error occurs
     */
    protected void getPreviews(int fromLine, int toLine, int numOfCharacters, List<String> list) throws IOException {
        try (FileChannel fc = new RandomAccessFile(this.dataFile.toFile(), "r").getChannel()) {
            long fileSize = fc.size();
            long position = 0;
            long size = (fileSize > Integer.MAX_VALUE) ? Integer.MAX_VALUE : fileSize;

            StringBuilder lineBuilder = new StringBuilder();
            boolean isDone = false;
            boolean skipLine = false;
            int lineNumber = 1;
            int charCount = 0;
            byte previousChar = -1;
            do {
                MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, position, size);

                while (buffer.hasRemaining() && !isDone && !Thread.currentThread().isInterrupted()) {
                    byte currentChar = buffer.get();
                    if (skipLine) {
                        if (currentChar == AbstractDataPreviewer.CARRIAGE_RETURN || currentChar == AbstractDataPreviewer.LINE_FEED) {
                            skipLine = false;

                            if (charCount > 0) {
                                charCount = 0;
                                list.add(lineBuilder.toString());
                                lineBuilder.delete(0, lineBuilder.length());
                            }

                            lineNumber++;
                            if (currentChar == AbstractDataPreviewer.LINE_FEED && previousChar == AbstractDataPreviewer.CARRIAGE_RETURN) {
                                lineNumber--;
                            }
                        }
                    } else if (lineNumber > toLine) {
                        isDone = true;
                    } else if (lineNumber < fromLine) {
                        skipLine = true;
                    } else {
                        if (currentChar == AbstractDataPreviewer.CARRIAGE_RETURN || currentChar == AbstractDataPreviewer.LINE_FEED) {
                            if (charCount > 0) {
                                charCount = 0;
                                list.add(lineBuilder.toString());
                                lineBuilder.delete(0, lineBuilder.length());
                            }

                            lineNumber++;
                            if (currentChar == AbstractDataPreviewer.LINE_FEED && previousChar == AbstractDataPreviewer.CARRIAGE_RETURN) {
                                lineNumber--;
                            }
                        } else {
                            charCount++;
                            if (charCount > numOfCharacters) {
                                lineBuilder.append(AbstractDataPreviewer.ELLIPSIS);
                                skipLine = true;
                            } else {
                                lineBuilder.append((char) currentChar);
                            }
                        }
                    }

                    previousChar = currentChar;
                }

                position += size;
                if ((position + size) > fileSize) {
                    size = fileSize - position;
                }
            } while (position < fileSize && !Thread.currentThread().isInterrupted());

        }
    }

}
