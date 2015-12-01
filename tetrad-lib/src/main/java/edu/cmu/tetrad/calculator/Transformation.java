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

package edu.cmu.tetrad.calculator;

import edu.cmu.tetrad.calculator.expression.*;
import edu.cmu.tetrad.calculator.parser.ExpressionParser;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a transformation on some dataset. For instance if the
 * equation is X = Z + W, where Z and W are columns in the data set,
 *
 * @author Tyler Gibson
 * @return a column that is the sum of Z and W row-wise.
 */
public class Transformation {

    /**
     * Don't instantiate.
     */
    private Transformation() {

    }

    //======================== Public Methods ======================//


    /**
     * Transforms the given data using the given representations of transforming
     * equations.
     *
     * @param data      - The data that is being transformed.
     * @param equations - The equations used to transform the data.
     * @throws ParseException - Throws a parse exception if any of the given equations isn't
     *                        "valid".
     */
    public static void transform(DataSet data, String... equations) throws ParseException {
        if (equations.length == 0) {
            return;
        }
        for (String equation : equations) {
            transformEquation(data, equation);
        }
    }

    //======================== Private Methods ============================//

    /**
     * Transforms the given dataset using the given equation.
     */
    private static void transformEquation(DataSet data, String eq) throws ParseException {
        ExpressionParser parser = new ExpressionParser(data.getVariableNames(), ExpressionParser.RestrictionType.MAY_ONLY_CONTAIN);
        Equation equation = parser.parseEquation(eq);

        addVariableIfRequired(data, equation.getVariable());
        Expression expression = equation.getExpression();
        Node variable = data.getVariable(equation.getVariable());
        if (variable == null) {
            throw new IllegalStateException("Unknown variable " + variable);
        }
        int column = data.getColumn(variable);
        // build the context pairs.
        List<String> contextVars = getContextVariables(expression);
        // now do the transformation row by row.
        DataBackedContext context = new DataBackedContext(data, contextVars);
        int rows = data.getNumRows();
        for (int row = 0; row < rows; row++) {
            // build the context
            context.setRow(row);
            double newValue = expression.evaluate(context);
            data.setDouble(row, column, newValue);
        }
    }

    /**
     * Adds a column for the given varible if required.
     */
    private static void addVariableIfRequired(DataSet data, String var) {
        List<String> nodes = data.getVariableNames();
        if (!nodes.contains(var)) {
            data.addVariable(new ContinuousVariable(var));
        }
    }


    /**
     * @return the variables used in the expression.
     */
    private static List<String> getContextVariables(Expression exp) {
        List<String> variables = new ArrayList<String>();

        for (Expression sub : exp.getExpressions()) {
            if (sub instanceof VariableExpression) {
                variables.add(((VariableExpression) sub).getVariable());
            } else if (!(sub instanceof ConstantExpression)) {
                variables.addAll(getContextVariables(sub));
            }
        }

        return variables;
    }

    //============================== Inner Class ==============================//

    private static class DataBackedContext implements Context {


        /**
         * The data.
         */
        private DataSet data;


        /**
         * Var -> index mapping.
         */
        private Map<String, Integer> indexes = new HashMap<String, Integer>();


        /**
         * The getModel row.
         */
        private int row;


        public DataBackedContext(DataSet data, List<String> vars) {
            this.data = data;
            for (String v : vars) {
                Node n = data.getVariable(v);
                indexes.put(v, data.getColumn(n));
            }
        }

        public void setRow(int row) {
            this.row = row;
        }

        public Double getValue(String var) {
            Integer i = indexes.get(var);
            if (i != null) {
                return data.getDouble(row, i);
            }
            return null;
        }
    }
}




