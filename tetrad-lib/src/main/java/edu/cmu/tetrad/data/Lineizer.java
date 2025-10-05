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

package edu.cmu.tetrad.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 * @author josephramsey Returns one line at a time, with a method to determine whether another line is available. Blank
 * lines and lines beginning with the given comment marker are skipped.
 */
final class Lineizer {

    /**
     * The character sequence being tokenized.
     */
    private final BufferedReader reader;
    /**
     * The comment marker.
     */
    private final String commentMarker;
    /**
     * Stores the line read by hasMoreLines, until it is retrieved by nextLine, at which point it is null.
     */
    private String tempLine;
    /**
     * The line number of the line most recently read.
     */
    private int lineNumber;

    /**
     * Constructs a tokenizer for the given input line, using the given Pattern as delimiter.
     *
     * @param reader        a {@link java.io.Reader} object
     * @param commentMarker a {@link java.lang.String} object
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
     * <p>hasMoreLines.</p>
     *
     * @return true iff more tokens exist in the line.
     */
    public boolean hasMoreLines() {
        if (this.tempLine == null) {
            try {
                this.tempLine = readLine();
                return this.tempLine != null;
            } catch (IOException e) {
                return false;
            }
        } else {
            return true;
        }
    }

    /**
     * Return the next token in the line.
     *
     * @return a {@link java.lang.String} object
     */
    public String nextLine() {
        this.lineNumber++;

        if (this.tempLine == null) {
            try {
                return readLine();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            String line = this.tempLine;
            this.tempLine = null;
            return line;
        }
    }

    private String readLine() throws IOException {
        String line;

        while ((line = this.reader.readLine()) != null) {
            if ("".equals(line)) {
                continue;
            }

            if (line.startsWith(this.commentMarker)) {
                continue;
            }

            return line;
        }

        return null;
    }

    /**
     * <p>Getter for the field <code>lineNumber</code>.</p>
     *
     * @return a int
     */
    public int getLineNumber() {
        return this.lineNumber;
    }
}




