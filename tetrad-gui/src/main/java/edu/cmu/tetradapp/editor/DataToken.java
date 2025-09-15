/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

package edu.cmu.tetradapp.editor;


/**
 * Represents the tokens used for reading in data in Tetrad.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public enum DataToken implements Token {

    /**
     * Whitespace.
     */
    WHITESPACE("WHITESPACE"),

    /**
     * A blank line.
     */
    BLANK_LINE("BLANK_LINE"),

    /**
     * Rest of the line.
     */
    REST_OF_LINE("REST_OF_LINE"),

    /**
     * Comment line.
     */
    COMMENT_LINE("COMMENT_LINE"),

    /**
     * Variable marker.
     */
    VARIABLES_MARKER("VARIABLES_MARKER"),

    /**
     * Variable type.
     */
    VAR_TYPE("VAR_TYPE"),

    /**
     * Colon.
     */
    COLON("COLON"),

    /**
     * Left parenthesis.
     */
    LPAREN("LPAREN"),

    /**
     * Discrete state.
     */
    DISCRETE_STATE("DISCRETE_STATE"),

    /**
     * Comma.
     */
    COMMA("COMMA"),

    /**
     * Right parenthesis.
     */
    RPAREN("RPAREN"),

    /**
     * Data marker.
     */
    DATA_MARKER("DATA_MARKER"),

    /**
     * Continuous token.
     */
    CONTINUOUS_TOKEN("CONTINUOUS_TOKEN"),

    /**
     * Discrete token.
     */
    DISCRETE_TOKEN("DISCRETE_TOKEN"),

    /**
     * String token.
     */
    STRING_TOKEN("STRING_TOKEN"),

    /**
     * Missing value.
     */
    MISSING_VALUE("MISSING_VALUE"),

    /**
     * Knowledge marker.
     */
    KNOWLEDGE_MARKER("KNOWLEDGE_MARKER"),

    /**
     * Add temporal header.
     */
    ADD_TEMPORAL_HEADER("ADD_TEMPORAL"),

    /**
     * Forbid direct header.
     */
    FORBID_DIRECT_HEADER("FORBID_DIRECT"),

    /**
     * Require direct header.
     */
    REQUIRE_DIRECT_HEADER("REQUIRE_DIRECT"),

    /**
     * The end of file.
     */
    EOF("EOF");

    /**
     * The name of the token
     */
    private final String name;

    /**
     * Constructs the enum
     */
    DataToken(String name) {
        if (name == null) {
            throw new NullPointerException();
        }

        this.name = name;
    }


    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return the name of the token
     */
    public String getName() {
        return this.name;
    }
}



