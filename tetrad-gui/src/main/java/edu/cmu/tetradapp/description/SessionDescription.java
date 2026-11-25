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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A description of a session as text: what boxes it has, how they are connected, where they sit, and which choices
 * and non-default parameter settings were made in each. It holds no data sets and no results. It is written as a
 * legible text file by {@link SessionDescriptionWriter} and read back by {@link SessionDescriptionParser}.
 * <p>
 * This class holds text only, so a description can be written, parsed and compared without a session.
 *
 * @author josephramsey
 * @author Claude
 */
public final class SessionDescription {

    /**
     * The version of the text format.
     */
    public static final int FORMAT = 1;

    /**
     * The key of the header field giving the version of Tetrad that wrote the description.
     */
    public static final String TETRAD_VERSION = "Tetrad version";

    /**
     * The key of the header field giving the name of the session.
     */
    public static final String SESSION = "Session";

    /**
     * The key of the header field giving the date the description was written.
     */
    public static final String WRITTEN = "Written";

    /**
     * The header fields, in order.
     */
    private final List<BoxDescription.Field> header;

    /**
     * The boxes, in order.
     */
    private final List<BoxDescription> boxes;

    /**
     * Constructs a session description.
     *
     * @param header the header fields, in order.
     * @param boxes  the boxes, in order.
     */
    public SessionDescription(List<BoxDescription.Field> header, List<BoxDescription> boxes) {
        this.header = List.copyOf(header);
        this.boxes = List.copyOf(boxes);
    }

    /**
     * Returns the header fields, in order.
     *
     * @return the header fields; the list cannot be modified.
     */
    public List<BoxDescription.Field> getHeader() {
        return this.header;
    }

    /**
     * Returns the value of the first header field with the given key.
     *
     * @param key the key.
     * @return the value, or null if there is no such field.
     */
    public String getHeader(String key) {
        for (BoxDescription.Field field : this.header) {
            if (field.key().equals(key)) {
                return field.value();
            }
        }

        return null;
    }

    /**
     * Returns the boxes, in order.
     *
     * @return the boxes; the list cannot be modified.
     */
    public List<BoxDescription> getBoxes() {
        return this.boxes;
    }

    /**
     * Returns the box with the given name.
     *
     * @param name the name of the box.
     * @return the box, or null if there is none by that name.
     */
    public BoxDescription getBox(String name) {
        for (BoxDescription box : this.boxes) {
            if (box.getName().equals(name)) {
                return box;
            }
        }

        return null;
    }

    /**
     * Returns the names of the parents of the given box. The "Parents" field separates names with commas, and a box
     * name may itself contain a comma, so the names are matched against the names of the boxes in this description:
     * pieces between commas are joined until they spell the name of a box. A piece that matches no box is returned
     * as it stands.
     *
     * @param box a box of this description.
     * @return the names of its parents, in the order written.
     */
    public List<String> getParentNames(BoxDescription box) {
        List<String> parents = new ArrayList<>();
        String value = box.get(BoxDescription.PARENTS);

        if (value == null || value.isBlank()) {
            return parents;
        }

        Set<String> names = new HashSet<>();

        for (BoxDescription b : this.boxes) {
            names.add(b.getName());
        }

        String[] pieces = value.split(",", -1);
        int i = 0;

        while (i < pieces.length) {
            StringBuilder candidate = new StringBuilder(pieces[i]);
            int end = i;
            int matchedEnd = -1;
            String matched = null;

            while (true) {
                String name = candidate.toString().strip();

                if (names.contains(name)) {
                    matched = name;
                    matchedEnd = end;
                }

                if (++end >= pieces.length) {
                    break;
                }

                candidate.append(',').append(pieces[end]);
            }

            if (matched != null) {
                parents.add(matched);
                i = matchedEnd + 1;
            } else {
                parents.add(pieces[i].strip());
                i++;
            }
        }

        return parents;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof SessionDescription other && this.header.equals(other.header)
               && this.boxes.equals(other.boxes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return 31 * this.header.hashCode() + this.boxes.hashCode();
    }

    /**
     * Returns the description as text, as {@link SessionDescriptionWriter} writes it.
     *
     * @return the text.
     */
    @Override
    public String toString() {
        return SessionDescriptionWriter.toText(this);
    }
}
