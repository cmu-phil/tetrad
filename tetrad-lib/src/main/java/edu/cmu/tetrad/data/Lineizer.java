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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 * @return one line at a time, with a method to determine whether another
 * line is available. Blank lines and lines beginning with the given comment
 * marker are skipped.
 *
 * @author Joseph Ramsey
 */
public final class Lineizer {

    /**
     * The character sequence being tokenized.
     */
    private final BufferedReader reader;

    /**
     * Stores the line read by hasMoreLines, until it is retrieved by nextLine,
     * at which point it is null.
     */
    private String tempLine = null;

    /**
     * The comment marker.
     */
    private String commentMarker;

    /**
     * The line number of the line most recently read.
     */
    private int lineNumber = 0;

    /**
     * Constructs a tokenizer for the given input line, using the given Pattern
     * as delimiter.
     */
    public Lineizer(Reader reader, String commentMarker) {
        if (reader == null) {
            throw new NullPointerException();
        }

        if (commentMarker == null) {
            throw new NullPointerException();
        }

        this.reader = new BufferedReader(reader);
        this.commentMarker = commentMarker;
    }

    /**
     * @return true iff more tokens exist in the line.
     */
    public final boolean hasMoreLines() {
        if (tempLine == null) {
            try {
                tempLine = readLine();
                return tempLine != null;
            }
            catch (IOException e) {
                return false;
            }
        }
        else {
            return true;
        }
    }

    /**
     * Return the next token in the line.
     */
    public final String nextLine() {
        lineNumber++;

        if (tempLine == null) {
            try {
                return readLine();
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        else {
            String line = tempLine;
            tempLine = null;
            return line;
        }
    }

    private String readLine() throws IOException {
        String line;

        while ((line = reader.readLine()) != null) {
            if ("".equals(line)) {
                continue;
            }

            if (line.startsWith(commentMarker)) {
                continue;
            }

            return line;
        }

        return null;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}



