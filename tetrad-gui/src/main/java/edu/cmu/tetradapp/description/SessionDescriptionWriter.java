/// ////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetradapp.description;

/**
 * Writes a {@link SessionDescription} as legible text. The text is meant to be read by people, pasted into documents
 * and email, and read back by {@link SessionDescriptionParser}; it is the same file for all three.
 * <p>
 * The layout is line oriented. The first line names the format. Header fields follow, one per line, as a key, a
 * colon and a value. Each box starts with its name in square brackets, followed by its fields. Text that needs
 * several lines (knowledge, a graph, a note) sits between a "Begin" line and an "End" line and is indented by four
 * spaces. The last line marks the end, so that a description cut short in copying can be recognized. Indentation
 * carries no meaning, so a mail program that strips it does no harm.
 *
 * @author josephramsey
 * @author Claude
 */
public final class SessionDescriptionWriter {

    /**
     * The start of the first line of a description; the format number follows it.
     */
    static final String MAGIC = "Tetrad session description, format ";

    /**
     * The last line of a description.
     */
    static final String END = "End of session description";

    /**
     * The start of the line that opens a box.
     */
    static final String BOX_OPEN = "[Box ";

    /**
     * The start of the line that opens a block.
     */
    static final String BLOCK_BEGIN = "Begin ";

    /**
     * The start of the line that closes a block.
     */
    static final String BLOCK_END = "End ";

    /**
     * The indentation of the lines of a block.
     */
    static final String BLOCK_INDENT = "    ";

    private SessionDescriptionWriter() {
    }

    /**
     * Returns the given description as text.
     *
     * @param description the description.
     * @return the text, with lines ended by the newline character.
     */
    public static String toText(SessionDescription description) {
        StringBuilder out = new StringBuilder();
        out.append(MAGIC).append(SessionDescription.FORMAT).append('\n');

        for (BoxDescription.Field field : description.getHeader()) {
            appendField(out, field);
        }

        for (BoxDescription box : description.getBoxes()) {
            out.append('\n');
            out.append(BOX_OPEN).append(box.getName()).append("]\n");

            for (BoxDescription.Item item : box.getItems()) {
                if (item instanceof BoxDescription.Field field) {
                    appendField(out, field);
                } else if (item instanceof BoxDescription.Block block) {
                    appendBlock(out, block);
                }
            }
        }

        out.append('\n').append(END).append('\n');
        return out.toString();
    }

    private static void appendField(StringBuilder out, BoxDescription.Field field) {
        out.append(field.key()).append(':');

        if (!field.value().isEmpty()) {
            out.append(' ').append(field.value());
        }

        out.append('\n');
    }

    private static void appendBlock(StringBuilder out, BoxDescription.Block block) {
        String endLine = BLOCK_END + block.name();
        out.append(BLOCK_BEGIN).append(block.name()).append('\n');

        for (String line : block.lines()) {
            if (line.isEmpty()) {
                out.append('\n');
            } else {
                out.append(BLOCK_INDENT).append(escape(line, endLine)).append('\n');
            }
        }

        out.append(endLine).append('\n');
    }

    /**
     * Guards a line of a block that would otherwise be taken for the line that closes the block. Such a line, and
     * any line that starts with a backslash, is given a backslash in front of its first character that is not a
     * space. The parser removes it.
     */
    private static String escape(String line, String endLine) {
        String stripped = line.stripLeading();

        if (stripped.equals(endLine) || stripped.startsWith("\\")) {
            int indent = line.length() - stripped.length();
            return line.substring(0, indent) + "\\" + stripped;
        }

        return line;
    }
}
