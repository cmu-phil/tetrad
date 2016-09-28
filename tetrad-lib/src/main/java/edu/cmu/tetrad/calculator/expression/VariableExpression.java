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
 * An Expression for a variable.
 *
 * @author Tyler Gibson
 */
public class VariableExpression implements Expression {
    static final long serialVersionUID = 23L;

    /**
     * The variable that is being evaluated.
     */
    private String variable;


    public VariableExpression(String variable) {
        if (variable == null) {
            throw new NullPointerException("variable is null.");
        }
        this.variable = variable;
    }

    public static VariableExpression serializableInstance() {
        return new VariableExpression("a");
    }

    //======================== Public methods ===================//

    /**
     * @return the variable.
     */
    public String getVariable() {
        return this.variable;
    }


    public Double evaluateGeneric(Context context) {
        return context.getValue(variable);
    }


    public double evaluate(Context context) {
        Double value = context.getValue(variable);

        if (value == null) {
            throw new IllegalArgumentException(variable + " was not assigned a value.");
        }

        return value;
    }

    public String getToken() {
        return "";
    }

    public ExpressionDescriptor.Position getPosition() {
        return ExpressionDescriptor.Position.NEITHER;
    }


    public List<Expression> getExpressions() {
        return Collections.emptyList();
    }

    public String toString() {
        return variable;
    }

    @Override
    public RealDistribution getRealDistribution(Context context) {
        return null;
    }

    public IntegerDistribution getIntegerDistribution(Context context) {
        return null;
    }
}




