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

package edu.cmu.tetradapp.util;

import edu.cmu.tetrad.util.TetradLogger;

import javax.swing.*;
import java.io.OutputStream;

/**
 * An output stream to pipe stuff written to it directly to a JTextArea.
 *
 * @author Joseph Ramsey
 */
public class TextAreaOutputStream extends OutputStream implements TetradLogger.LogDisplayOutputStream {

    /**
     * The text area written to.
     */
    private final JTextArea textArea;

    /**
     * A string bugger used to buffer lines.
     */
    private final StringBuilder buf = new StringBuilder();

    /**
     * The length of string written to the text area.
     */
    private int lengthWritten;

    /**
     * Creates a text area output stream, for writing text to the given
     * text area. It is assumed that the text area is blank to b egin with
     * and that nothing else writes to it. The reason is that the length
     * of the text in the text area is tracked separately so that the text
     * area can quickly be scrolled to the end of the text stored. (The
     * internal mechanism for keeping track of text area length is slow.)
     *
     * @param textArea The text area written to.
     */
    public TextAreaOutputStream(JTextArea textArea) {
        this.textArea = textArea;
        this.lengthWritten = textArea.getText().length();
    }

    /**
     * Writes the specified byte to this byte array output stream.
     *
     * @param b the byte to be written.
     */
    public synchronized void write(int b) {
        if (this.buf.length() > 5000) return;
        this.buf.append((char) b);

        if ((char) b == '\n') {

            this.textArea.append(this.buf.toString());
            this.lengthWritten = this.lengthWritten + this.buf.length();
            this.buf.setLength(0);
            moveToEnd();
        }
    }

    /**
     * Converts the buffer's contents into a string, translating bytes into
     * characters according to the platform's default character encoding.
     *
     * @return String translated from the buffer's contents.
     */
    public String toString() {
        return this.textArea.toString();
    }


    public void reset() {
        this.textArea.setText("");
        this.lengthWritten = 0;
    }


    /**
     * The total string length written to the text area.
     *
     * @return The total string length written to the text area.
     */
    public int getLengthWritten() {
        return this.lengthWritten;
    }

    public void moveToEnd() {
        this.textArea.setCaretPosition(0);
    }
}




