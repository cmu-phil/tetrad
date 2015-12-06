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

package edu.cmu.tetradapp.editor;


/**
 * Represents the tokens used for reading in data in Tetrad.
 *
 * @author Joseph Ramsey
 */
public enum DataToken implements Token {
    WHITESPACE("WHITESPACE"),

    BLANK_LINE("BLANK_LINE"),
    REST_OF_LINE("REST_OF_LINE"),
    COMMENT_LINE("COMMENT_LINE"),

    VARIABLES_MARKER("VARIABLES_MARKER"),
    VAR_TYPE("VAR_TYPE"),
    COLON("COLON"),
    LPAREN("LPAREN"),
    DISCRETE_STATE("DISCRETE_STATE"),
    COMMA("COMMA"),
    RPAREN("RPAREN"),

    DATA_MARKER("DATA_MARKER"),
    CONTINUOUS_TOKEN("CONTINUOUS_TOKEN"),
    DISCRETE_TOKEN("DISCRETE_TOKEN"),
    STRING_TOKEN("STRING_TOKEN"),
    MISSING_VALUE("MISSING_VALUE"),

    KNOWLEDGE_MARKER("KNOWLEEDGE_MARKER"),
    ADD_TEMPORAL_HEADER("ADD_TEMPORAL"),
    FORBID_DIRECT_HEADER("FORBID_DIRECT"),
    REQUIRE_DIRECT_HEADER("REQUIRE_DIRECT"),

    EOF("EOF");

    /**
     * The name of the token
     */
    private final String name;

    /**
     * Constructs the enum
     */
    private DataToken(String name){
        if (name == null) {
            throw new NullPointerException();
        }

        this.name = name;
    }


    /**
     * @return the name of the token
     */
    public String getName(){
       return this.name;
    }
}



