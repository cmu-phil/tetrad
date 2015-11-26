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

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.ObjectStreamException;
import java.util.regex.Pattern;

/**
 * Type-safe enum of delimiter types for parsing data. DISCRETE is used in data
 * where every line starts a new field and every occurrence of a tab starts a
 * new field. SECTION_MARKER is replaces tabs by " *\t *". LAUNCH_TIME replaces tabs
 * by ",". Custom replaces tabs by a specified regular expression.
 *
 * @author Joseph Ramsey
 */
public final class DelimiterType implements TetradSerializable {
    static final long serialVersionUID = 23L;

    public static final DelimiterType WHITESPACE =
            new DelimiterType("Whitespace", "\\s+");
    public static final DelimiterType TAB =
            new DelimiterType("Tab", "\t");
    public static final DelimiterType COMMA =
            new DelimiterType("Comma", ",");
    public static final DelimiterType COLON =
            new DelimiterType("Comma", ":");

    /**
     * The name of this type.
     */
    private transient final String name;

    /**
     * The regular expression representing the delimiter.
     */
    private transient final Pattern pattern;

    /**
     * Protected constructor for the types; this allows for extension in case
     * anyone wants to add formula types.
     */
    private DelimiterType(String name, String regex) {
        this.name = name;
        this.pattern = Pattern.compile(regex);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static DelimiterType serializableInstance() {
        return DelimiterType.TAB;
    }

    /**
     * @return the pattern representing this delimiter type. This pattern can be
     * used to parse, using a matcher.
     */
    public final Pattern getPattern() {
        return this.pattern;
    }

    /**
     * Prints out the name of the type.
     */
    public final String toString() {
        return name;
    }

    // Declarations required for serialization.
    private static int nextOrdinal = 0;
    private final int ordinal = nextOrdinal++;
    private static final DelimiterType[] TYPES = {WHITESPACE, TAB, COMMA};

    final Object readResolve() throws ObjectStreamException {
        return TYPES[ordinal]; // Canonicalize.
    }
}





