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
 * Represents a constant expression, that is an expression that always evaluates to the same value.
 *
 * @author Tyler Gibson
 */
public class ConstantExpression implements Expression {
    static final long serialVersionUID = 23L;

    /**
     * Constant expression for PI.
     */
    public static final ConstantExpression PI = new ConstantExpression(Math.PI, "PI");//"\u03C0");


    /**
     * Constant expression for e.
     */
    public static final ConstantExpression E = new ConstantExpression(Math.E, "E");// "e");

    /**
     * THe value of the expression.
     */
    private double value;


    /**
     * The name of the constant or null if there isn't one. Constants with names are things
     * like PI or E.
     */
    private String name;


    /**
     * Constructs the constant expression given the value to use.
     */
    public ConstantExpression(double value) {
        this.value = value;
    }


    /**
     * Constructs the constant expression given the value and the name.
     */
    public ConstantExpression(double value, String name) {
        if (name == null) {
            throw new NullPointerException("name was null.");
        }
        this.value = value;
        this.name = name;
    }

    public static ConstantExpression serializableInstance() {
        return new ConstantExpression(1.2);
    }

    //========================== Public Methods ===============================//


    /**
     * @return the name of the constant or null if there isn't one.
     */
    public String getName() {
        return this.name;
    }

    /**
     * @return the constant value.
     */
    public double evaluate(Context context) {
        return this.value;
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
        if (name == null) {
            return Double.toString(value);
        } else {
            return name;
        }
    }

    @Override
    public RealDistribution getRealDistribution(Context context) {
        return null;
    }

    public IntegerDistribution getIntegerDistribution(Context context) {
        return null;
    }
}




