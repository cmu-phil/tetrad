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
import org.apache.commons.math3.distribution.IntegerDistribution;
import org.apache.commons.math3.distribution.RealDistribution;

import java.util.List;

/**
 * Represents a mathematical expression. Used in the Calculator and
 * the Generalized Sem model.
 * </p>
 * Note that expressions form trees. Each expression has a (possibly
 * empty) list of children.
 *
 * @author Tyler Gibson
 */
public interface Expression extends TetradSerializable {
    long serialVersionUID = 23L;

    /**
     * Evaluates the expression using the given context
     */
    double evaluate(Context context);

    /**
     * @return the token for this expression=="+".
     */
    String getToken();

    /**
     * @return the position, infix or not.
     */
    ExpressionDescriptor.Position getPosition();

    /**
     * @return the sub expressions of this expression.
     */
    List<Expression> getExpressions();

    /**
     */
    RealDistribution getRealDistribution(Context context);

    /**
     */
    IntegerDistribution getIntegerDistribution(Context context);

}



