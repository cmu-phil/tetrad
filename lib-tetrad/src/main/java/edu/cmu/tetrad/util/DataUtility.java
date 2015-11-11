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

package edu.cmu.tetrad.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Fast data loader for continuous or discrete data.
 *
 * Jul 13, 2015 10:44:10 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class DataUtility {

    private static final byte NEW_LINE = '\n';

    private static final byte CARRIAGE_RETURN = '\r';

    private DataUtility() {
    }

    /**
     * Counts the number of column of the first line in the file.
     *
     * @param file dataset
     * @param delimiter a single character used to separate the data
     * @return
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
     * @return
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

