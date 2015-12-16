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

import edu.cmu.tetrad.calculator.expression.Context;
import edu.cmu.tetrad.calculator.expression.Expression;
import edu.cmu.tetrad.calculator.parser.ExpressionParser;
import org.junit.Test;

import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * Tests the PC search.
 *
 * @author Joseph Ramsey
 */
public class TestExpressionParser {

    @Test
    public void test1() {
        final Map<String, Double> values = new HashMap<String, Double>();

        values.put("b11", 1.0);
        values.put("X1", 2.0);
        values.put("X2", 3.0);
        values.put("B22", 4.0);
        values.put("B12", 5.0);
        values.put("X4", 6.0);
        values.put("b13", 7.0);
        values.put("X5", 8.0);
        values.put("b10", 9.0);
        values.put("X", 10.0);
        values.put("Y", 11.0);
        values.put("Z", 12.0);
        values.put("W", 13.0);
        values.put("T", 14.0);
        values.put("R", 15.0);

        Context context = new Context() {
            public Double getValue(String var) {
                return values.get(var);
            }
        };


        Map<String, Double> formulasToEvaluations = new HashMap<String, Double>();

        formulasToEvaluations.put("0", 0.0);
        formulasToEvaluations.put("b11*X1 + sin(X2) + B22*X2 + B12*X4+b13*X5", 100.14);
        formulasToEvaluations.put("X5*X4*X4", 288.0);
        formulasToEvaluations.put("sin(b10*X1)", -0.75097);
        formulasToEvaluations.put("((X + ((Y * (Z ^ W)) * T)) + R)", 16476953628377113.0);
        formulasToEvaluations.put("X + Y * Z ^ W * T + R", 16476953628377113.0);
        formulasToEvaluations.put("pow(2, 5)", 32.0);
        formulasToEvaluations.put("2^5", 32.0);
        formulasToEvaluations.put("exp(1)", 2.718);
        formulasToEvaluations.put("sqrt(2)", 1.414);
        formulasToEvaluations.put("cos(0)", 1.0);
        formulasToEvaluations.put("cos(3.14/2)", 0.0);
        formulasToEvaluations.put("sin(0)", 0.0);
        formulasToEvaluations.put("sin(3.14/2)", 1.0);
        formulasToEvaluations.put("tan(1)", 1.56);
        formulasToEvaluations.put("cosh(1)", 1.54);
        formulasToEvaluations.put("sinh(1)", 1.18);
        formulasToEvaluations.put("tanh(1)", 0.76);
        formulasToEvaluations.put("acos(1)", 0.0);
        formulasToEvaluations.put("asin(1)", 1.57);
        formulasToEvaluations.put("atan(1)", 0.78);
        formulasToEvaluations.put("ln(1)", 0.0);
        formulasToEvaluations.put("log10(10)", 1.0);
        formulasToEvaluations.put("ceil(2.5)", 3.0);
        formulasToEvaluations.put("floor(2.5)", 2.0);
        formulasToEvaluations.put("abs(-5)", 5.0);
        formulasToEvaluations.put("max(2, 5, 3, 1, 10, -3)", 10.0);
        formulasToEvaluations.put("min(2, 5, 3, 1, 10, -3)", -3.0);

        // Logical.
        formulasToEvaluations.put("AND(1, 1)", 1.0);
        formulasToEvaluations.put("AND(1, 0)", 0.0);
        formulasToEvaluations.put("AND(0, 1)", 0.0);
        formulasToEvaluations.put("AND(0, 0)", 0.0);
        formulasToEvaluations.put("AND(0, 0.5)", 0.0);

        formulasToEvaluations.put("1 AND 1", 1.0);

        formulasToEvaluations.put("OR(1, 1)", 1.0);
        formulasToEvaluations.put("OR(1, 0)", 1.0);
        formulasToEvaluations.put("OR(0, 1)", 1.0);
        formulasToEvaluations.put("OR(0, 0)", 0.0);
        formulasToEvaluations.put("OR(0, 0.5)", 0.0);

        formulasToEvaluations.put("1 OR 1", 1.0);

        formulasToEvaluations.put("XOR(1, 1)", 0.0);
        formulasToEvaluations.put("XOR(1, 0)", 1.0);
        formulasToEvaluations.put("XOR(0, 1)", 1.0);
        formulasToEvaluations.put("XOR(0, 0)", 0.0);
        formulasToEvaluations.put("XOR(0, 0.5)", 0.0);

        formulasToEvaluations.put("1 XOR 1", 0.0);

        formulasToEvaluations.put("1 AND 0 OR 1 XOR 1 + 1", 1.0);

        formulasToEvaluations.put("1 < 2", 1.0);
        formulasToEvaluations.put("1 < 0", 0.0);
        formulasToEvaluations.put("1 < 1", 0.0);
        formulasToEvaluations.put("1 <= 2", 1.0);
        formulasToEvaluations.put("1 <= 1", 1.0);
        formulasToEvaluations.put("1 <= -1", 0.0);
        formulasToEvaluations.put("1 = 2", 0.0);
        formulasToEvaluations.put("1 = 1", 1.0);
        formulasToEvaluations.put("1 = -1", 0.0);
        formulasToEvaluations.put("1 > 2", 0.0);
        formulasToEvaluations.put("1 > 1", 0.0);
        formulasToEvaluations.put("1 > -1", 1.0);
        formulasToEvaluations.put("1 >= 2", 0.0);
        formulasToEvaluations.put("1 >= 1", 1.0);
        formulasToEvaluations.put("1 >= -1", 1.0);
        formulasToEvaluations.put("IF(1 > 2, 1, 2)", 2.0);
        formulasToEvaluations.put("IF(1 < 2, 1, 2)", 1.0);
        formulasToEvaluations.put("IF(1 < 2 AND 3 < 4, 1, 2)", 1.0);


        ExpressionParser parser = new ExpressionParser();

        try {
            for (String formula : formulasToEvaluations.keySet()) {
                Expression expression = parser.parseExpression(formula);

                double value = expression.evaluate(context);

                assertEquals(formulasToEvaluations.get(formula), value, 0.01);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public void test2() {
        final Map<String, Double> values = new HashMap<String, Double>();

        values.put("b11", 1.0);
        values.put("X1", 2.0);
        values.put("X2", 3.0);
        values.put("B22", 4.0);
        values.put("B12", 5.0);
        values.put("X4", 6.0);
        values.put("b13", 7.0);
        values.put("X5", 8.0);
        values.put("b10", 9.0);
        values.put("X", 10.0);
        values.put("Y", 11.0);
        values.put("Z", 12.0);
        values.put("W", 13.0);
        values.put("T", 14.0);
        values.put("R", 15.0);
        values.put("s2", 0.0);
        values.put("s3", 1.0);

        Context context = new Context() {
            public Double getValue(String var) {
                return values.get(var);
            }
        };

        List<String> formulas = new ArrayList<String>();
//
        formulas.add("ChiSquare(s3)");
        formulas.add("Gamma(1, 1)");
        formulas.add("Beta(3, 5)");
        formulas.add("Poisson(5)");
        formulas.add("Indicator(0.3)");
        formulas.add("ExponentialPower(3)");
        formulas.add("exp(Normal(s2, s3))");    // Log normal
        formulas.add("Normal(0, s3)");
        formulas.add("abs(Normal(s2, s3) ^ 3)");  // Gaussian Power
        formulas.add("Discrete(3, 1, 5)");
        formulas.add("0.3 * Normal(-2.0e2, 0.5) + 0.7 * Normal(2.0, 0.5)");  // Mixture of Gaussians
        formulas.add("StudentT(s3)");
        formulas.add("s3"); // Single value.
        formulas.add("Hyperbolic(5, 3)");
        formulas.add("Uniform(s2, s3)");
        formulas.add("VonMises(s3)");
        formulas.add("Split(0, 1, 5, 6)");
        formulas.add("Mixture(0.5, N(-2, 0.5), 0.5, N(2, 0.5))");
//
        ExpressionParser parser = new ExpressionParser();

        try {
            for (String formula : formulas) {
                Expression expression = parser.parseExpression(formula);
                double value = expression.evaluate(context);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void test3() {

        // Need a regex that will match all numbers (and only numbers).

        String regex1 = "[0-9]+((\\.?)[0-9]+)?";
        String regex2 = "[0-9]+(\\.?)[0-9]*";
        String regex3 = "(\\.?)[0-9]+";
        String regex4 = "[0-9]+(\\.?)";
        String regex5 = "([0-9]+)|([0-9]+\\.?[0-9]*)|([0-9]*\\.?[0-9]+)";
        String regex6 = "(-?[0-9]+\\.[0-9]*)|(-?[0-9]*\\.[0-9]+)|(-?[0-9]+)";

//        String regex = regex1;
//        String regex = regex2;
//        String regex = regex3;
//        String regex = regex4;
//        String regex = regex5;
        String regex = regex6;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        Matcher matcher = pattern.matcher("0.5");
        boolean matches = matcher.matches();

        assertTrue(matches);

        matcher = pattern.matcher(".5");
        matches = matcher.matches();

        assertTrue(matches);

        matcher = pattern.matcher("5.");
        matches = matcher.matches();

        assertTrue(matches);

        matcher = pattern.matcher("5");
        matches = matcher.matches();

        assertTrue(matches);

        matcher = pattern.matcher(".");
        matches = matcher.matches();

        assertTrue(!matches);

        matcher = pattern.matcher("-.5");
        matches = matcher.matches();

        assertTrue(matches);

        matcher = pattern.matcher("-5.");
        matches = matcher.matches();

        assertTrue(matches);

        matcher = pattern.matcher("-5");
        matches = matcher.matches();

        assertTrue(matches);

        matcher = pattern.matcher("-.");
        matches = matcher.matches();

        assertTrue(!matches);
    }

    // Formulas that should and should not fail.
    @Test
    public void test4() {
        Map<String, Integer> formulasToOffsets = new LinkedHashMap<String, Integer>();

        // -1 means it should parse.
        formulasToOffsets.put("X X", 2);
        formulasToOffsets.put("b11 b11", 4);
        formulasToOffsets.put("X + Y ++", 7);
        formulasToOffsets.put("b1*X1 *+ b2 * X2 +", 17);
        formulasToOffsets.put("cos()", 0);
        formulasToOffsets.put("2..3 * X", 0);
        formulasToOffsets.put("+X1", -1);
        formulasToOffsets.put("-X1", -1);
        formulasToOffsets.put("A / B", -1);
        formulasToOffsets.put("b1*X1 +@**!! b2 * X2", 7);
        formulasToOffsets.put("X7", 0);
        
        List<String> otherNodes = new ArrayList<String>();
        otherNodes.add("X7");

        ExpressionParser parser = new ExpressionParser(otherNodes, ExpressionParser.RestrictionType.MAY_NOT_CONTAIN);

        for (String formula : formulasToOffsets.keySet()) {
            try {
                parser.parseExpression(formula);
                assertEquals(formulasToOffsets.get(formula).intValue(), -1);
            } catch (ParseException e) {
                int offset = e.getErrorOffset();
                assertEquals(formulasToOffsets.get(formula).intValue(), offset);
            }
        }
    }

    // Test distribution means.
    @Test
    public void test5() {
        final Map<String, Double> values = new HashMap<String, Double>();

        Context context = new Context() {
            public Double getValue(String var) {
                return values.get(var);
            }
        };

        Map<String, Double> formulas = new LinkedHashMap<String, Double>();

        formulas.put("ChiSquare(1)", 1.0);
        formulas.put("Gamma(2, .5)", 1.0);
        formulas.put("Beta(1, 2)", 0.33);
        formulas.put("Normal(2, 3)", 2.0);
        formulas.put("N(2, 3)", 2.0);
        formulas.put("StudentT(5)", 0.0);
        formulas.put("U(0, 1)", 0.5);
        formulas.put("Uniform(0, 1)", 0.5);
        formulas.put("Split(0, 1, 5, 6)", 3.0);

        ExpressionParser parser = new ExpressionParser();

        try {
            for (String formula : formulas.keySet()) {
                Expression expression = parser.parseExpression(formula);

                double sum = 0.0;
                int sampleSize = 10000;

                for (int i = 0; i < sampleSize; i++) {
                    double value = expression.evaluate(context);
                    sum += value;
                }

                double mean = sum / sampleSize;

                assertEquals(formulas.get(formula), mean, 0.1);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }
}


