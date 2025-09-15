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

package edu.cmu.tetrad.calculator.parser;

/**
 * Allowable tokens.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public enum Token {

    /**
     * Whitespace.
     */
    WHITESPACE,

    /**
     * Left parenthesis.
     */
    LPAREN,

    /**
     * Right parenthesis.
     */
    RPAREN,

    /**
     * Comma.
     */
    COMMA,

    /**
     * Number.
     */
    NUMBER,

    /**
     * Operator.
     */
    OPERATOR,

    /**
     * Parameter.
     */
    PARAMETER,

    /**
     * Equation.
     */
    EQUATION,

    /**
     * String.
     */
    STRING,

    /**
     * End of file.
     */
    EOF,

    /**
     * Unknown.
     */
    UNKNOWN,
}





