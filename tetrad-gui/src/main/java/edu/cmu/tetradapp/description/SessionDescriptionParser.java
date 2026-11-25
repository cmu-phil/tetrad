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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the text written by {@link SessionDescriptionWriter} back into a {@link SessionDescription}.
 * <p>
 * The text may have been pasted into a document or sent by email and copied back, so the parser is forgiving. Text
 * before the first line of the description and after its last line is ignored. Indentation is ignored. Line endings
 * of any kind are accepted. If the description was quoted in an email reply, so that every line starts with a
 * greater-than sign, the quoting is removed. Lines that cannot be read are skipped and reported as warnings; only
 * text with no description in it at all is an error.
 *
 * @author josephramsey
 * @author Claude
 */
public final class SessionDescriptionParser {

    private static final Pattern MAGIC_LINE
            = Pattern.compile("^([>\\s]*)" + Pattern.quote(SessionDescriptionWriter.MAGIC) + "(\\d+)\\s*$");

    private static final Pattern QUOTING = Pattern.compile("^\\s*(>\\s?)+");

    private static final Pattern FIELD_LINE = Pattern.compile("^([^:\\[\\]]+):\\s*(.*)$");

    private SessionDescriptionParser() {
    }

    /**
     * Reads a session description from text.
     *
     * @param text the text.
     * @return the description, together with warnings about anything that could not be read.
     * @throws IllegalArgumentException if the text holds no session description.
     */
    public static Result parse(String text) {
        if (text == null) {
            throw new NullPointerException("The text must not be null.");
        }

        String[] lines = text.replace("﻿", "").replace(' ', ' ').split("\\R", -1);
        List<String> warnings = new ArrayList<>();

        // Find the first line of the description, and see whether the description was quoted in a reply.
        int start = -1;
        boolean quoted = false;

        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = MAGIC_LINE.matcher(lines[i]);

            if (matcher.matches()) {
                start = i;
                quoted = matcher.group(1).contains(">");

                if (Integer.parseInt(matcher.group(2)) > SessionDescription.FORMAT) {
                    warnings.add("Line " + (i + 1) + ": this description is in format " + matcher.group(2)
                                 + ", which is newer than the format this version of Tetrad knows (format "
                                 + SessionDescription.FORMAT + "); some of it may not be read.");
                }

                break;
            }
        }

        if (start == -1) {
            throw new IllegalArgumentException("This text does not hold a Tetrad session description: no line "
                                               + "reading \"" + SessionDescriptionWriter.MAGIC + "1\" was found.");
        }

        List<BoxDescription.Field> header = new ArrayList<>();
        List<BoxDescription> boxes = new ArrayList<>();

        String boxName = null;
        List<BoxDescription.Item> items = null;
        String blockName = null;
        List<String> blockLines = null;
        int blockIndent = 0;
        boolean ended = false;

        for (int i = start + 1; i < lines.length; i++) {
            String line = quoted ? QUOTING.matcher(lines[i]).replaceFirst("") : lines[i];
            String stripped = line.strip();

            // Inside a block, every line is content until the line that closes the block.
            if (blockName != null) {
                if (stripped.equals(SessionDescriptionWriter.BLOCK_END + blockName)) {
                    addBlock(blockName, blockLines, blockIndent, boxName, items, warnings, i);
                    blockName = null;
                    blockLines = null;
                } else {
                    blockLines.add(line);
                }

                continue;
            }

            if (stripped.isEmpty() || stripped.startsWith("#")) {
                continue;
            }

            if (stripped.equals(SessionDescriptionWriter.END)) {
                ended = true;
                break;
            }

            if (stripped.startsWith(SessionDescriptionWriter.BOX_OPEN) && stripped.endsWith("]")) {
                if (boxName != null) {
                    boxes.add(new BoxDescription(boxName, items));
                }

                boxName = stripped.substring(SessionDescriptionWriter.BOX_OPEN.length(), stripped.length() - 1).strip();
                items = new ArrayList<>();

                if (boxName.isEmpty()) {
                    warnings.add("Line " + (i + 1) + ": a box has no name.");
                }

                continue;
            }

            Matcher field = FIELD_LINE.matcher(stripped);

            if (field.matches()) {
                BoxDescription.Field f = new BoxDescription.Field(field.group(1), field.group(2));

                if (boxName == null) {
                    header.add(f);
                } else {
                    items.add(f);
                }

                continue;
            }

            if (stripped.startsWith(SessionDescriptionWriter.BLOCK_BEGIN)) {
                blockName = stripped.substring(SessionDescriptionWriter.BLOCK_BEGIN.length()).strip();
                blockLines = new ArrayList<>();
                blockIndent = line.length() - line.stripLeading().length();
                continue;
            }

            warnings.add("Line " + (i + 1) + ": not understood and skipped: " + stripped);
        }

        if (blockName != null) {
            warnings.add("The \"" + blockName + "\" block was not closed; the description may have been cut short.");
            addBlock(blockName, blockLines, blockIndent, boxName, items, warnings, lines.length);
        }

        if (boxName != null) {
            boxes.add(new BoxDescription(boxName, items));
        }

        if (!ended) {
            warnings.add("The line \"" + SessionDescriptionWriter.END + "\" was not found; the description may "
                         + "have been cut short.");
        }

        return new Result(new SessionDescription(header, boxes), List.copyOf(warnings));
    }

    /**
     * Adds a finished block to the box being read, after removing the indentation the writer gave its lines and the
     * backslashes it used to guard them.
     */
    private static void addBlock(String blockName, List<String> blockLines, int blockIndent, String boxName,
                                 List<BoxDescription.Item> items, List<String> warnings, int lineIndex) {
        if (boxName == null) {
            warnings.add("Line " + (lineIndex + 1) + ": the \"" + blockName + "\" block is not inside a box and "
                         + "was skipped.");
            return;
        }

        // The writer indents the lines of a block by a fixed amount beyond the line that opens it. Remove that
        // much, or less if a mail program has already removed some, as told by the least indented line.
        int indent = blockIndent + SessionDescriptionWriter.BLOCK_INDENT.length();

        for (String line : blockLines) {
            if (!line.isBlank()) {
                indent = Math.min(indent, line.length() - line.stripLeading().length());
            }
        }

        List<String> cleaned = new ArrayList<>();

        for (String line : blockLines) {
            String content = line.isBlank() ? "" : line.substring(indent);
            String contentStripped = content.stripLeading();

            if (contentStripped.startsWith("\\")) {
                int leading = content.length() - contentStripped.length();
                content = content.substring(0, leading) + contentStripped.substring(1);
            }

            cleaned.add(content);
        }

        items.add(new BoxDescription.Block(blockName, cleaned));
    }

    /**
     * What the parser read.
     *
     * @param description the description.
     * @param warnings    one message for each thing that could not be read; empty if all was read.
     */
    public record Result(SessionDescription description, List<String> warnings) {
    }
}
