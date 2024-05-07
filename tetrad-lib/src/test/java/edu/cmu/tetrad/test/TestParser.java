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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.calculator.expression.ConstantExpression;
import edu.cmu.tetrad.calculator.expression.Context;
import edu.cmu.tetrad.calculator.expression.Expression;
import edu.cmu.tetrad.calculator.parser.ExpressionParser;
import org.apache.commons.math3.util.FastMath;
import org.junit.Test;

import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Tyler Gibson
 */
public final class TestParser {

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

    /**
     * Tests misc invalid expressions.
     */
    @Test
    public void testInvalidExpressions() {
        ExpressionParser parser = new ExpressionParser();

        TestParser.parseInvalid(parser, "(1 + 3))");
        TestParser.parseInvalid(parser, "(1 + (4 * 5) + sqrt(5)");
        TestParser.parseInvalid(parser, "1+");
        TestParser.parseInvalid(parser, "113#");
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
        assertEquals(2.0, expression.evaluate(new TestingContext()), 0.0);

        expression = parse(parser, "*(+(1,2), 5)");
        assertEquals(15.0, expression.evaluate(new TestingContext()), 0.0);

        expression = parse(parser, "1 + 2.5");
        assertEquals(3.5, expression.evaluate(new TestingContext()), 0.0);

        expression = parse(parser, "(2 + 3)");
        assertEquals(5.0, expression.evaluate(new TestingContext()), 0.0);

        expression = parse(parser, "1 + (3 + 4)");
        assertEquals(8.0, expression.evaluate(new TestingContext()), 0.0);

        expression = parse(parser, "1 + 2 + 5");
        assertEquals(8.0, expression.evaluate(new TestingContext()), 0.0);

        expression = parse(parser, "1 + (2 * 3)");
        assertEquals(7.0, expression.evaluate(new TestingContext()), 0.0);

        expression = parse(parser, "1 + (2 + (3 * 4))");
        assertEquals(15.0, expression.evaluate(new TestingContext()), 0.0);

        expression = parse(parser, "(2 * 3) + (4 * 5)");
        assertEquals(26.0, expression.evaluate(new TestingContext()), 0.0);

        expression = parse(parser, "((2 * 3) + (1 + (2 + (3 * 4))))");
        assertEquals(21.0, expression.evaluate(new TestingContext()), 0.0);

        expression = parse(parser, "pow(2,3)");
        assertEquals(8.0, expression.evaluate(new TestingContext()), 0.0);

        expression = parse(parser, "sqrt(pow(2,2))");
        assertEquals(2.0, expression.evaluate(new TestingContext()), 0.0);

        expression = parse(parser, ConstantExpression.E.getName());
        assertEquals(expression.evaluate(new TestingContext()), ConstantExpression.E.evaluate(null), 0.0);

        expression = parse(parser, ConstantExpression.PI.getName());
        assertEquals(expression.evaluate(new TestingContext()), ConstantExpression.PI.evaluate(null), 0.0);

        expression = parse(parser, ConstantExpression.PI.getName() + "+ 2");
        assertEquals(FastMath.PI + 2, expression.evaluate(new TestingContext()), 0.0);
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
        assertEquals(5.6, expression.evaluate(context), 0.0);

        expression = parse(parser, "(x + y) * z");
        context.assign("x", 1.0);
        context.assign("y", 2.0);
        context.assign("z", 3.0);
        assertEquals(9.0, expression.evaluate(context), 0.0);

        expression = parse(parser, "3 + (x + (3 * y))");
        context.assign("x", 4.0);
        context.assign("y", 2.0);
        assertEquals(13.0, expression.evaluate(context), 0.0);
    }

    //============================== Private Methods ===========================//

    /**
     * Tests that undefined variables aren't allowed.
     */
    @Test
    public void testVariableRestriction() {
        ExpressionParser parser = new ExpressionParser(Arrays.asList("x", "y", "z"), ExpressionParser.RestrictionType.MAY_ONLY_CONTAIN);
        TestParser.parseInvalid(parser, "x + x1");
    }

    /**
     * Tests commutative operators
     */
    @Test
    public void testCommutativeOperators() {
        ExpressionParser parser = new ExpressionParser();

        Expression expression = TestParser.parse(parser, "1 + 2 + 3");
        assertEquals(6.0, expression.evaluate(new TestingContext()), 0.0);

        expression = TestParser.parse(parser, "1 + 1 + 1 + (3 * 4)");
        assertEquals(15.0, expression.evaluate(new TestingContext()), 0.0);

        expression = TestParser.parse(parser, "1 * 1 * 2 * 3 * (1 + 1)");
        assertEquals(12.0, expression.evaluate(new TestingContext()), 0.0);
    }

    //====================== Inner class ========================//

    private static class TestingContext implements Context {

        private final Map<String, Double> doubleVars = new HashMap<>();
        private final Map<String, Object> vars = new HashMap<>();

        public void assign(String v, double d) {
            this.doubleVars.put(v, d);
        }

        public void clear() {
            this.doubleVars.clear();
            this.vars.clear();
        }

        public Double getValue(String var) {
            return this.doubleVars.get(var);
        }
    }
}




