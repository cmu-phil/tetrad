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

import org.apache.commons.math3.distribution.IntegerDistribution;
import org.apache.commons.math3.distribution.RealDistribution;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Contains some common methods for Expressions (see).
 *
 * @author Tyler Gibson
 */
abstract class AbstractExpression implements Expression {
    static final long serialVersionUID = 23L;

    /**
     * The sub expressionts
     */
    private final List<Expression> expressions;

    /**
     * The position of the token--infix or prefix or both.
     */
    private ExpressionDescriptor.Position position;

    /**
     * The token--for example, + or cos.
     */
    private String token;

    public AbstractExpression(String token, ExpressionDescriptor.Position position, Expression... expressions) {
        this.position = position;
        this.token = token;
        this.expressions = Collections.unmodifiableList(Arrays.asList(expressions));
    }


    public String getToken() {
        return this.token;
    }

    public ExpressionDescriptor.Position getPosition() {
        return this.position;
    }

    /**
     * @return the sub expressions (unmodifiable).f
     */
    public List<Expression> getExpressions() {
        return this.expressions;
    }

    @Override
    public RealDistribution getRealDistribution(Context context) {
        if (expressions.size() == 1) {
            return expressions.get(0).getRealDistribution(context);
        }

        return null;

    }

    public IntegerDistribution getIntegerDistribution(Context context) {
        if (expressions.size() == 1) {
            return expressions.get(0).getIntegerDistribution(context);
        }

        return null;

    }

    public String toString() {
        if (getPosition() != null && getToken() != null) {
            return ExpressionUtils.renderExpression(this, getPosition(), getToken());
        }
        return "No string representation available.";
    }
}




