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

import java.util.Collections;
import java.util.List;

/**
 * An Expression for a variable.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class VariableExpression implements Expression {
    private static final long serialVersionUID = 23L;

    /**
     * The variable that is being evaluated.
     */
    private final String variable;


    /**
     * <p>Constructor for VariableExpression.</p>
     *
     * @param variable a {@link java.lang.String} object
     */
    public VariableExpression(String variable) {
        if (variable == null) {
            throw new NullPointerException("variable is null.");
        }
        this.variable = variable;
    }

    /**
     * <p>serializableInstance.</p>
     *
     * @return a {@link edu.cmu.tetrad.calculator.expression.VariableExpression} object
     */
    public static VariableExpression serializableInstance() {
        return new VariableExpression("a");
    }

    //======================== Public methods ===================//

    /**
     * <p>Getter for the field <code>variable</code>.</p>
     *
     * @return the variable.
     */
    public String getVariable() {
        return this.variable;
    }


    /**
     * <p>evaluateGeneric.</p>
     *
     * @param context a {@link edu.cmu.tetrad.calculator.expression.Context} object
     * @return a {@link java.lang.Double} object
     */
    public Double evaluateGeneric(Context context) {
        return context.getValue(this.variable);
    }


    /**
     * {@inheritDoc}
     */
    public double evaluate(Context context) {
        Double value = context.getValue(this.variable);

        if (value == null) {
            throw new IllegalArgumentException(this.variable + " was not assigned a value.");
        }

        return value;
    }

    /**
     * <p>getToken.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getToken() {
        return "";
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
        return Collections.emptyList();
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return this.variable;
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




