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

package edu.cmu.tetrad.calculator.expression;

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Represents the signature of the expression, for example sqrt(number).
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public interface ExpressionSignature extends TetradSerializable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;


    /**
     * <p>getSignature.</p>
     *
     * @return the sigature as a string.
     */
    String getSignature();

    /**
     * <p>getNumberOfArguments.</p>
     *
     * @return the number o f arguments.
     */
    int getNumberOfArguments();


    /**
     * <p>getArgument.</p>
     *
     * @param index a int
     * @return the argument type at the given index.
     */
    String getArgument(int index);

}





