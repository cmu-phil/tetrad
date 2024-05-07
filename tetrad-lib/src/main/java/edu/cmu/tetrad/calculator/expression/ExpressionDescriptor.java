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

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Represents a definition for some expression.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public interface ExpressionDescriptor extends TetradSerializable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * <p>getName.</p>
     *
     * @return the name that the expressions is known under.
     */
    String getName();

    /**
     * <p>getToken.</p>
     *
     * @return the token that represents the expression, such as "+".
     */
    String getToken();

    /**
     * <p>getSignature.</p>
     *
     * @return the signature that should be used.
     */
    ExpressionSignature getSignature();

    /**
     * <p>getPosition.</p>
     *
     * @return the position that the expression can occur in.
     */
    Position getPosition();

    /**
     * Creates the actual expression that can be used to evaluate matters from the given expressions.
     *
     * @param expressions a {@link edu.cmu.tetrad.calculator.expression.Expression} object
     * @return a {@link edu.cmu.tetrad.calculator.expression.Expression} object
     * @throws edu.cmu.tetrad.calculator.expression.ExpressionInitializationException if any.
     */
    Expression createExpression(Expression... expressions) throws ExpressionInitializationException;


    /**
     * An enum of positions that an expression can occur in.
     */
    enum Position implements TetradSerializable {
        /**
         * The expression can occur in neither the prefix nor infix position.
         */
        NEITHER,

        /**
         * The expression can occur in the infix position.
         */
        INFIX,

        /**
         * The expression can occur in the prefix position.
         */
        PREFIX,

        /**
         * The expression can occur in both the prefix and infix position.
         */
        BOTH;

        private static final long serialVersionUID = 23L;

        /**
         * <p>serializableInstance.</p>
         *
         * @return a {@link edu.cmu.tetrad.calculator.expression.ExpressionDescriptor.Position} object
         */
        public static Position serializableInstance() {
            return Position.NEITHER;
        }
    }


}




