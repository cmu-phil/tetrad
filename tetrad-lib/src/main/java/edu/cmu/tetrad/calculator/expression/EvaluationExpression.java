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

import java.util.Collections;
import java.util.List;

/**
 * An equation expression.
 *
 * @author Tyler Gibson
 */
public class EvaluationExpression implements Expression {
    static final long serialVersionUID = 23L;

    /**
     * The variable part of the expression.
     */
    private VariableExpression variable;


    /**
     * The string you are testing the variable against.
     */
    private String string;


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

    public static EvaluationExpression serializableInstance() {
        return new EvaluationExpression(VariableExpression.serializableInstance(), "a");
    }

    @Override
    public String toString() {
        return super.toString();
    }

    public double evaluate(Context context) {
        Object o = variable.evaluateGeneric(context);
        if (o != null && string.equals(o.toString())) {
            return 1.0;
        }
        return 0.0;
    }

    public String getToken() {
        return "Eval";
    }

    public ExpressionDescriptor.Position getPosition() {
        return ExpressionDescriptor.Position.NEITHER;
    }

    public List<Expression> getExpressions() {
        return Collections.singletonList((Expression) variable);
    }

    @Override
    public RealDistribution getRealDistribution(Context context) {
        return null;
    }

    public IntegerDistribution getIntegerDistribution(Context context) {
        return null;
    }
}




