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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.calculator.expression.ConstantExpression;
import edu.cmu.tetrad.calculator.expression.Context;
import edu.cmu.tetrad.calculator.expression.Expression;
import edu.cmu.tetrad.calculator.parser.ExpressionParser;
import org.junit.Test;

import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Tyler Gibson
 */
public final class TestParser {

    /**
     * Tests misc invalid expressions.
     */
    @Test
    public void testInvalidExpressions() {
        ExpressionParser parser = new ExpressionParser();

        parseInvalid(parser, "(1 + 3))");
        parseInvalid(parser, "(1 + (4 * 5) + sqrt(5)");
        parseInvalid(parser, "1+");
        parseInvalid(parser, "113#");
    }

    @Test
    public void testParseEquation() {
        ExpressionParser parser = new ExpressionParser(Arrays.asList("x", "y"), ExpressionParser.RestrictionType.MAY_ONLY_CONTAIN);
        try {
            parser.parseEquation("x = (1 + y)");
        } catch (ParseException ex) {
            fail(ex.getMessage());
        }
    }

    /**
     * Tests expressions without variables (mainly used while writing the parser)
     */
    @Test
    public void testBasicExpressions() {
        ExpressionParser parser = new ExpressionParser();

        Expression expression = parse(parser, "+(1,1)");
        assertTrue(expression.evaluate(new TestingContext()) == 2.0);

        expression = parse(parser, "*(+(1,2), 5)");
        assertTrue(expression.evaluate(new TestingContext()) == 15.0);

        expression = parse(parser, "1 + 2.5");
        assertTrue(expression.evaluate(new TestingContext()) == 3.5);

        expression = parse(parser, "(2 + 3)");
        assertTrue(expression.evaluate(new TestingContext()) == 5.0);

        expression = parse(parser, "1 + (3 + 4)");
        assertTrue(expression.evaluate(new TestingContext()) == 8.0);

        expression = parse(parser, "1 + 2 + 5");
        assertTrue(expression.evaluate(new TestingContext()) == 8.0);

        expression = parse(parser, "1 + (2 * 3)");
        assertTrue(expression.evaluate(new TestingContext()) == 7.0);

        expression = parse(parser, "1 + (2 + (3 * 4))");
        assertTrue(expression.evaluate(new TestingContext()) == 15.0);

        expression = parse(parser, "(2 * 3) + (4 * 5)");
        assertTrue(expression.evaluate(new TestingContext()) == 26.0);

        expression = parse(parser, "((2 * 3) + (1 + (2 + (3 * 4))))");
        assertTrue(expression.evaluate(new TestingContext()) == 21.0);

        expression = parse(parser, "pow(2,3)");
        assertTrue(expression.evaluate(new TestingContext()) == 8.0);

        expression = parse(parser, "sqrt(pow(2,2))");
        assertTrue(expression.evaluate(new TestingContext()) == 2.0);

        expression = parse(parser, ConstantExpression.E.getName());
        assertTrue(expression.evaluate(new TestingContext()) == ConstantExpression.E.evaluate(null));

        expression = parse(parser, ConstantExpression.PI.getName());
        assertTrue(expression.evaluate(new TestingContext()) == ConstantExpression.PI.evaluate(null));

        expression = parse(parser, ConstantExpression.PI.getName() + "+ 2");
        assertTrue(expression.evaluate(new TestingContext()) == Math.PI + 2);
    }


    /**
     * Tests expressions with variables.
     */
    @Test
    public void testVariables() {
        ExpressionParser parser = new ExpressionParser(Arrays.asList("x", "y", "z"), ExpressionParser.RestrictionType.MAY_ONLY_CONTAIN);
        TestingContext context = new TestingContext();

        Expression expression = parse(parser, "x");
        context.assign("x", 5.6);
        assertTrue(expression.evaluate(context) == 5.6);

        expression = parse(parser, "(x + y) * z");
        context.assign("x", 1.0);
        context.assign("y", 2.0);
        context.assign("z", 3.0);
        assertTrue(expression.evaluate(context) == 9.0);

        expression = parse(parser, "3 + (x + (3 * y))");
        context.assign("x", 4.0);
        context.assign("y", 2.0);
        assertTrue(expression.evaluate(context) == 13.0);
    }

    /**
     * Tests that undefined variables aren't allowed.
     */
    @Test
    public void testVariableRestriction() {
        ExpressionParser parser = new ExpressionParser(Arrays.asList("x", "y", "z"), ExpressionParser.RestrictionType.MAY_ONLY_CONTAIN);
        parseInvalid(parser, "x + x1");
    }

    /**
     * Tests commutative operators
     */
    @Test
    public void testCommutativeOperators() {
        ExpressionParser parser = new ExpressionParser();

        Expression expression = parse(parser, "1 + 2 + 3");
        assertTrue(expression.evaluate(new TestingContext()) == 6.0);

        expression = parse(parser, "1 + 1 + 1 + (3 * 4)");
        assertTrue(expression.evaluate(new TestingContext()) == 15.0);

        expression = parse(parser, "1 * 1 * 2 * 3 * (1 + 1)");
        assertTrue(expression.evaluate(new TestingContext()) == 12.0);
    }

    //============================== Private Methods ===========================//

    private static void parseInvalid(ExpressionParser parser, String exp) {
        try {
            Expression e = parser.parseExpression(exp);
            fail("Should not have parsed, " + exp + ", but got " + e);
        } catch (ParseException ex) {
            // Succeeded
        }
    }

    /**
     * Tests the expression on the given parser.
     */
    private static Expression parse(ExpressionParser parser, String expression) {
        try {
            return parser.parseExpression(expression);
        } catch (ParseException ex) {
            int offset = ex.getErrorOffset();
            System.out.println(expression);
            for (int i = 0; i < offset; i++) {
                System.out.print(" ");
            }
            System.out.println("^");
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        return null;
    }

    //====================== Inner class ========================//

    private static class TestingContext implements Context {

        private Map<String, Double> doubleVars = new HashMap<String, Double>();
        private Map<String, Object> vars = new HashMap<String, Object>();

        public void assign(String v, double d) {
            doubleVars.put(v, d);
        }

        public void clear() {
            this.doubleVars.clear();
            vars.clear();
        }

        public Double getValue(String var) {
            return doubleVars.get(var);
        }
    }
}




