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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

/**
 *
 * Feb 29, 2016 1:32:34 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public abstract class AbstractDataReader {

    protected static final byte NEW_LINE = '\n';

    protected static final byte CARRIAGE_RETURN = '\r';

    protected static final byte DOUBLE_QUOTE = '"';

    protected static final byte SINGLE_QUOTE = '\'';

    protected static final byte SPACE = ' ';

    protected int lineCount;
    protected int columnCount;

    protected final Path dataFile;
    protected final char delimiter;

    public AbstractDataReader(Path dataFile, char delimiter) {
        this.dataFile = dataFile;
        this.delimiter = delimiter;
        this.lineCount = -1;
        this.columnCount = -1;
    }

    public int countNumberOfColumns() throws IOException {
        if (columnCount == -1) {
            int count = 0;
            try (FileChannel fc = new RandomAccessFile(dataFile.toFile(), "r").getChannel()) {
                MappedByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
                byte currentChar = -1;
                byte prevChar = NEW_LINE;
                while (buffer.hasRemaining()) {
                    currentChar = buffer.get();
                    if (currentChar == CARRIAGE_RETURN) {
                        currentChar = NEW_LINE;
                    }

                    if (currentChar == delimiter || (currentChar == NEW_LINE && prevChar != NEW_LINE)) {
                        count++;
                        if (currentChar == NEW_LINE) {
                            break;
                        }
                    }

                    prevChar = currentChar;
                }

                // cases where file has no newline at the end of the file
                if (!(currentChar == -1 || currentChar == NEW_LINE)) {
                    count++;
                }
            }
            columnCount = count;
        }

        return columnCount;
    }

    public int countNumberOfLines() throws IOException {
        if (lineCount == -1) {
            int count = 0;
            try (FileChannel fc = new RandomAccessFile(dataFile.toFile(), "r").getChannel()) {
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

                // cases where file has no newline at the end of the file
                if (prevChar != NEW_LINE) {
                    count++;
                }
            }
            lineCount = count;
        }

        return lineCount;
    }

}
