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

import org.apache.commons.math3.distribution.IntegerDistribution;
import org.apache.commons.math3.distribution.RealDistribution;

import java.io.Serial;
import java.util.Collections;
import java.util.List;

/**
 * An equation expression.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class EvaluationExpression implements Expression {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The variable part of the expression.
     */
    private final VariableExpression variable;


    /**
     * The string you are testing the variable against.
     */
    private final String string;


    /**
     * <p>Constructor for EvaluationExpression.</p>
     *
     * @param exp a {@link edu.cmu.tetrad.calculator.expression.VariableExpression} object
     * @param s   a {@link java.lang.String} object
     */
    public EvaluationExpression(VariableExpression exp, String s) {
        if (exp == null) {
            throw new NullPointerException("Variable must not be null.");
        }
        if (s == null) {
            throw new NullPointerException("String must not be null.");
        }
        this.variable = exp;
        this.string = s;
    }

    /**
     * <p>serializableInstance.</p>
     *
     * @return a {@link edu.cmu.tetrad.calculator.expression.EvaluationExpression} object
     */
    public static EvaluationExpression serializableInstance() {
        return new EvaluationExpression(VariableExpression.serializableInstance(), "a");
    }

    /**
     * {@inheritDoc}
     */
    public double evaluate(Context context) {
        Object o = this.variable.evaluateGeneric(context);
        if (o != null && this.string.equals(o.toString())) {
            return 1.0;
        }
        return 0.0;
    }

    /**
     * <p>getToken.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getToken() {
        return "Eval";
    }

    /**
     * <p>getPosition.</p>
     *
     * @return a {@link edu.cmu.tetrad.calculator.expression.ExpressionDescriptor.Position} object
     */
    public ExpressionDescriptor.Position getPosition() {
        return ExpressionDescriptor.Position.NEITHER;
    }

    /**
     * <p>getExpressions.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Expression> getExpressions() {
        return Collections.singletonList(this.variable);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RealDistribution getRealDistribution(Context context) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public IntegerDistribution getIntegerDistribution(Context context) {
        return null;
    }
}




