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

package edu.cmu.tetrad.calculator.expression;

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Represents a definition for some expression.
 *
 * @author Tyler Gibson
 */
public interface ExpressionDescriptor extends  TetradSerializable {
    static final long serialVersionUID = 23L;


    public static enum Position implements TetradSerializable {
        NEITHER,
        INFIX,
        PREFIX,
        BOTH;

        static final long serialVersionUID = 23L;
        public static Position serializableInstance() {
            return NEITHER;
        }
    }

    /**
     * @return the name that the expressions is known under.
     */
    public String getName();

    /**
     * @return the token that represents the expression, such as "+".
     */
    public String getToken();

    /**
     * @return the signature that should be used.
     */
    public ExpressionSignature getSignature();

    /**
     * @return the position that the expression can occur in.
     */
    Position getPosition();


    /**
     * Creates the actual expression that can be used to evaluate matters from the given
     * expressions.
     */
    public Expression createExpression(Expression ... expressions) throws ExpressionInitializationException;


}




