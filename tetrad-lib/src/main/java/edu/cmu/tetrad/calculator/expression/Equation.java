///////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetrad.calculator.expression;

/**
 * Represents an equation of the form Variable = Expression, where the Variable represents a column in some dataset.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class Equation {

    /**
     * The string value of the variable.
     */
    private final String variable;


    /**
     * The expression that should be used to evaluate the variables new value.
     */
    private final Expression expression;


    /**
     * The unparsed expression.
     */
    private final String unparsedExpression;


    /**
     * <p>Constructor for Equation.</p>
     *
     * @param variable   a {@link java.lang.String} object
     * @param expression a {@link edu.cmu.tetrad.calculator.expression.Expression} object
     * @param unparsed   a {@link java.lang.String} object
     */
    public Equation(String variable, Expression expression, String unparsed) {
        if (variable == null) {
            throw new NullPointerException("variable was null.");
        }
        if (expression == null) {
            throw new NullPointerException("expression was null.");
        }
        if (unparsed == null) {
            throw new NullPointerException("unparsed was null.");
        }
        this.unparsedExpression = unparsed;
        this.variable = variable;
        this.expression = expression;
    }

    //========================== Public Methods ======================//


    /**
     * <p>Getter for the field <code>unparsedExpression</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getUnparsedExpression() {
        return this.unparsedExpression;
    }


    /**
     * <p>Getter for the field <code>variable</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getVariable() {
        return this.variable;
    }


    /**
     * <p>Getter for the field <code>expression</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.calculator.expression.Expression} object
     */
    public Expression getExpression() {
        return this.expression;
    }

}




