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
import org.apache.commons.math3.distribution.IntegerDistribution;
import org.apache.commons.math3.distribution.RealDistribution;

import java.util.List;

/**
 * Represents a mathematical expression. Used in the Calculator and the Generalized Sem model.
 * <p>
 * Note that expressions form trees. Each expression has a (possibly empty) list of children.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public interface Expression extends TetradSerializable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    long serialVersionUID = 23L;

    /**
     * Evaluates the expression using the given context
     *
     * @param context a {@link edu.cmu.tetrad.calculator.expression.Context} object
     * @return a double
     */
    double evaluate(Context context);

    /**
     * <p>getToken.</p>
     *
     * @return the token for this expression=="+".
     */
    String getToken();

    /**
     * <p>getPosition.</p>
     *
     * @return the position, infix or not.
     */
    ExpressionDescriptor.Position getPosition();

    /**
     * <p>getExpressions.</p>
     *
     * @return the sub expressions of this expression.
     */
    List<Expression> getExpressions();

    /**
     * <p>getRealDistribution.</p>
     *
     * @param context a {@link edu.cmu.tetrad.calculator.expression.Context} object
     * @return a {@link org.apache.commons.math3.distribution.RealDistribution} object
     */
    RealDistribution getRealDistribution(Context context);

    /**
     * <p>getIntegerDistribution.</p>
     *
     * @param context a {@link edu.cmu.tetrad.calculator.expression.Context} object
     * @return a {@link org.apache.commons.math3.distribution.IntegerDistribution} object
     */
    IntegerDistribution getIntegerDistribution(Context context);

}




