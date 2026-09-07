///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.calculator.expression.Context;
import edu.cmu.tetrad.calculator.expression.Expression;
import edu.cmu.tetrad.calculator.expression.ExpressionDescriptor;
import edu.cmu.tetrad.calculator.expression.ExpressionManager;
import edu.cmu.tetrad.calculator.parser.ExpressionParser;
import org.junit.Test;

import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests the algebraic and distribution expressions added to the ExpressionManager, along with the lexer's handling of
 * tokens that are prefixes of other tokens (for example, "log" versus "log10" and "asin" versus "asinh"), and the
 * correctness of the signature strings displayed in the Calculator editor.
 *
 * @author josephramsey
 */
public class TestCalculatorExpressions {

    private static final Map<String, Double> VALUES = new HashMap<>();

    static {
        TestCalculatorExpressions.VALUES.put("x", 8.0);
        TestCalculatorExpressions.VALUES.put("y", 3.0);
    }

    private static double eval(String expr) throws ParseException {
        ExpressionParser parser = new ExpressionParser(Arrays.asList("x", "y"),
                ExpressionParser.RestrictionType.MAY_ONLY_CONTAIN);
        Expression expression = parser.parseExpression(expr);
        Context context = TestCalculatorExpressions.VALUES::get;
        return expression.evaluate(context);
    }

    @Test
    public void testNewAlgebraicFunctions() throws ParseException {
        assertEquals(Math.log(8.0), TestCalculatorExpressions.eval("log(x)"), 1e-10);
        assertEquals(3.0, TestCalculatorExpressions.eval("log2(x)"), 1e-10);
        assertEquals(Math.log1p(8.0), TestCalculatorExpressions.eval("log1p(x)"), 1e-10);
        assertEquals(0.0, TestCalculatorExpressions.eval("logit(0.5)"), 1e-10);
        assertEquals(2.0, TestCalculatorExpressions.eval("cbrt(x)"), 1e-10);
        assertEquals(-2.0, TestCalculatorExpressions.eval("cbrt(0 - 8)"), 1e-10);
        assertEquals(0.0, TestCalculatorExpressions.eval("asinh(0)"), 1e-10);
        assertEquals(0.0, TestCalculatorExpressions.eval("acosh(1)"), 1e-10);
        assertEquals(0.5 * Math.log(3.0), TestCalculatorExpressions.eval("atanh(0.5)"), 1e-10);
        assertEquals(2.0, TestCalculatorExpressions.eval("mod(x, y)"), 1e-10);
    }

    /**
     * The lexer's operator regex is a first-match alternation in registration order, so tokens that are prefixes of
     * other tokens must be registered after them. This test locks in the correct tokenization of every affected pair.
     */
    @Test
    public void testTokenPrefixShadowing() throws ParseException {
        assertEquals(2.0, TestCalculatorExpressions.eval("log10(100)"), 1e-10);
        assertEquals(0.5, TestCalculatorExpressions.eval("logistic(0)"), 1e-10);
        assertEquals(Math.log(8.0), TestCalculatorExpressions.eval("ln(x)"), 1e-10);
        assertEquals(Math.PI / 2, TestCalculatorExpressions.eval("asin(1)"), 1e-10);
        assertEquals(0.0, TestCalculatorExpressions.eval("acos(1)"), 1e-10);
        assertEquals(Math.PI / 4, TestCalculatorExpressions.eval("atan(1)"), 1e-10);
        assertEquals(Math.log(2.0), TestCalculatorExpressions.eval("log(log10(100))"), 1e-10);
        assertEquals(Math.log(8.0 + Math.sqrt(65.0)) + Math.PI / 2,
                TestCalculatorExpressions.eval("asinh(x) + asin(1)"), 1e-10);
    }

    @Test
    public void testNewDistributions() throws ParseException {
        for (int i = 0; i < 200; i++) {
            double b = TestCalculatorExpressions.eval("Bernoulli(0.5)");
            assertTrue("Bernoulli draw out of range: " + b, b == 0.0 || b == 1.0);

            double n = TestCalculatorExpressions.eval("Binomial(10, 0.5)");
            assertTrue("Binomial draw out of range: " + n, 0.0 <= n && n <= 10.0);
            assertEquals("Binomial draw not an integer: " + n, n, Math.floor(n), 0.0);
        }

        assertEquals(0.0, TestCalculatorExpressions.eval("Bernoulli(0)"), 0.0);
        assertEquals(1.0, TestCalculatorExpressions.eval("Bernoulli(1)"), 0.0);
    }

    /**
     * Signatures are displayed in the Calculator editor's function list and inserted into the expression field on
     * double-click, so they must not contain the duplicated token that the old AbstractExpressionDescriptor
     * constructor produced (for example, "pow(pow, expr)").
     */
    @Test
    public void testSignaturesDoNotDuplicateToken() {
        for (ExpressionDescriptor descriptor : ExpressionManager.getInstance().getDescriptors()) {
            String token = descriptor.getToken();
            String signature = descriptor.getSignature().getSignature();
            assertFalse("Signature duplicates its token: " + signature,
                    signature.contains("(" + token + ",") || signature.contains("(" + token + ")"));
        }

        Map<String, String> expected = new HashMap<>();
        expected.put("log", "log(expr)");
        expected.put("mod", "mod(expr, expr)");
        expected.put("Bernoulli", "Bernoulli(expr)");
        expected.put("Binomial", "Binomial(expr, expr)");

        // Declared arities for migrated descriptors, one representative per arity class.
        expected.put("random", "random()");
        expected.put("sqrt", "sqrt(expr)");
        expected.put("pow", "pow(expr, expr)");
        expected.put("Normal", "Normal(expr, expr)");
        expected.put("XOR", "XOR(expr, expr)");
        expected.put("Triangular", "Triangular(expr, expr, expr)");
        expected.put("IF", "IF(expr, expr, expr)");
        expected.put("clip", "clip(expr, expr, expr)");
        expected.put("TruncNormal", "TruncNormal(expr, expr, expr, expr)");

        // Variadic descriptors show an open argument list.
        expected.put("max", "max(expr, ...)");
        expected.put("Discrete", "Discrete(expr, ...)");
        expected.put("mixture", "mixture(expr, ...)");
        expected.put("Switch", "Switch(expr, ...)");

        // Infix operator symbols show the bare token, since "^(expr, expr)" would not parse.
        expected.put("+", "+");
        expected.put("-", "-");
        expected.put("^", "^");
        expected.put("<=", "<=");
        expected.put("=", "=");

        for (ExpressionDescriptor descriptor : ExpressionManager.getInstance().getDescriptors()) {
            String want = expected.remove(descriptor.getToken());
            if (want != null) {
                assertEquals(want, descriptor.getSignature().getSignature());
            }
        }

        assertTrue("Missing descriptors: " + expected.keySet(), expected.isEmpty());
    }
}
