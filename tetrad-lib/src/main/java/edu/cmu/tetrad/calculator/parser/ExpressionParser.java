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

package edu.cmu.tetrad.calculator.parser;

import edu.cmu.tetrad.calculator.expression.*;

import java.text.ParseException;
import java.util.*;

/**
 * Parses a string into a tree-like expression.
 *
 * @author Tyler Gibson
 */
public class ExpressionParser {

    /**
     * The type of restriction on parameters. It's either the case that the expression may only contain parameters
     * in the given list, or may not contain parameters in the given list, or that there is no restribution--whatever
     * parameters occur in the expression are OK.
     */
    public enum RestrictionType {
        MAY_ONLY_CONTAIN, MAY_NOT_CONTAIN, NONE
    }


    /**
     * The getModel token.
     */
    private Token token;


    /**
     * The lexer.
     */
    private ExpressionLexer lexer;


    /**
     * The expressin manager used to get the actual expressions from.
     */
    private final ExpressionManager expressions = ExpressionManager.getInstance();

    /**
     * The parameters read from the string.
     */
    private final Set<String> parameters;


    /**
     * The parameters that are allowed in an expression.
     */
    private final Set<String> restrictionParameters;

    /**
     * The type of restribution on parameters.
     */
    private RestrictionType restrictionType;


    /**
     * Constructrs a parser that has no allowable parameters.
     */
    public ExpressionParser() {
        this.restrictionParameters = Collections.emptySet();
        this.parameters = new LinkedHashSet<>();
    }


    /**
     * Constructs the parser given a collection of allowable parameters.
     */
    public ExpressionParser(Collection<String> parameters, RestrictionType type) {
        if (parameters == null) {
            throw new NullPointerException("Parameters null.");
        }

        if (parameters.contains("$")) {
            throw new IllegalArgumentException("Variable list must not " +
                    "contain the wildcard '$'.");
        }

        this.restrictionParameters = new LinkedHashSet<>(parameters);
        this.restrictionParameters.add("$");
        this.parameters = new LinkedHashSet<>();
        this.restrictionType = type;
    }

    //================================ Public methods ===================================//


    /**
     * Parses the given expression, or throws an exception if its not possible.
     */
    public Expression parseExpression(String expression) throws ParseException {
        this.lexer = new ExpressionLexer(expression);
        nextToken();
        Expression exp = parseExpression();
        expect(Token.EOF);
        return exp;
    }


    /**
     * Parses an equation of the form Variable = Expression.
     */
    public Equation parseEquation(String equation) throws ParseException {
        int index = equation.indexOf("=");
        if (index < 1) {
            throw new ParseException("Equations must be of the form Var = Exp", 0);
        }
        String variable = equation.substring(0, index).trim();
        if (!variable.matches("[^0-9]?[^ \t]*")) {
            throw new ParseException("Invalid variable name.", 1);
        }

        return new Equation(variable, parseExpression(equation.substring(index + 1).trim()), equation);
    }

    public int getNextOffset() {
        return this.lexer.getNextOffset();
    }

    //================================ Private Methods =================================//


    /**
     * Moves to the next token.
     */
    private void nextToken() throws ParseException {
        this.token = this.lexer.nextToken();

        if (this.token == Token.UNKNOWN) {
            throw new ParseException("Unrecognized token,", this.lexer.getCurrentOffset());
        }
    }

    /**
     * Parses the expression.
     */
    private Expression parseExpression() throws ParseException {
//        return parsePlusExpression();
        return parseAndExpression();
    }

    private Expression parseAndExpression() throws ParseException {
        Expression expression = parseOrExpression();

        while (this.token == Token.OPERATOR && "AND".equals(this.lexer.getTokenString())) {
            int offset = this.lexer.getCurrentOffset();

            ExpressionDescriptor descriptor = getDescriptor();
            nextToken();
            Expression expression2 = parseOrExpression();

            try {
                expression = descriptor.createExpression(expression, expression2);
            } catch (ExpressionInitializationException e) {
                // Should never be thrown.
                throw new ParseException("Wrong number of arguments for expression " + descriptor.getName(),
                        offset);
            }
        }

        return expression;
    }

    private Expression parseOrExpression() throws ParseException {
        Expression expression = parseXorExpression();

        while (this.token == Token.OPERATOR && "OR".equals(this.lexer.getTokenString())) {
            int offset = this.lexer.getCurrentOffset();

            ExpressionDescriptor descriptor = getDescriptor();
            nextToken();
            Expression expression2 = parseXorExpression();

            try {
                expression = descriptor.createExpression(expression, expression2);
            } catch (ExpressionInitializationException e) {
                // Should never be thrown.
                throw new ParseException("Wrong number of arguments for expression " + descriptor.getName(),
                        offset);
            }
        }

        return expression;
    }

    private Expression parseXorExpression() throws ParseException {
        Expression expression = parseComparisonExpression();

        while (this.token == Token.OPERATOR && "XOR".equals(this.lexer.getTokenString())) {
            int offset = this.lexer.getCurrentOffset();

            ExpressionDescriptor descriptor = getDescriptor();
            nextToken();
            Expression expression2 = parsePlusExpression();

            try {
                expression = descriptor.createExpression(expression, expression2);
            } catch (ExpressionInitializationException e) {
                // Should never be thrown.
                throw new ParseException("Wrong number of arguments for expression " + descriptor.getName(),
                        offset);
            }
        }

        return expression;
    }

    private Expression parseComparisonExpression() throws ParseException {
        Expression expression = parsePlusExpression();
        Set<String> comparisonOperators = new HashSet<>();
        comparisonOperators.add("<");
        comparisonOperators.add("<=");
        comparisonOperators.add("=");
        comparisonOperators.add(">");
        comparisonOperators.add(">=");

        while (this.token == Token.OPERATOR && comparisonOperators.contains(this.lexer.getTokenString())) {
            int offset = this.lexer.getCurrentOffset();

            ExpressionDescriptor descriptor = getDescriptor();
            nextToken();
            Expression expression2 = parsePlusExpression();

            try {
                expression = descriptor.createExpression(expression, expression2);
            } catch (ExpressionInitializationException e) {
                // Should never be thrown.
                throw new ParseException("Wrong number of arguments for expression " + descriptor.getName(),
                        offset);
            }
        }

        return expression;
    }


    private Expression parsePlusExpression() throws ParseException {
        Expression expression = parseMultDivExpression();

        while (this.token == Token.OPERATOR && "+".equals(this.lexer.getTokenString()) || "-".equals(this.lexer.getTokenString())) {
            int offset = this.lexer.getCurrentOffset();

            ExpressionDescriptor descriptor = getDescriptor();
            nextToken();
            Expression expression2 = parseMultDivExpression();

            try {
                expression = descriptor.createExpression(expression, expression2);
            } catch (ExpressionInitializationException e) {
                // Should never be thrown.
                throw new ParseException("Wrong number of arguments for expression " + descriptor.getName(),
                        offset);
            }
        }

        return expression;
    }

    private Expression parseMultDivExpression() throws ParseException {
        Expression expression = parsePowerExpression();

        while (this.token == Token.OPERATOR && "*".equals(this.lexer.getTokenString()) || "/".equals(this.lexer.getTokenString())) {
            int offset = this.lexer.getCurrentOffset();

            ExpressionDescriptor descriptor = getDescriptor();
            nextToken();
            Expression expression2 = parsePowerExpression();

            try {
                expression = descriptor.createExpression(expression, expression2);
            } catch (ExpressionInitializationException e) {
                // Should never be thrown.
                throw new ParseException("Wrong number of arguments for expression " + descriptor.getName(),
                        offset);
            }
        }

        return expression;
    }

    private Expression parsePowerExpression() throws ParseException {
        Expression expression = parseChompExpression();

        while (this.token == Token.OPERATOR && "^".equals(this.lexer.getTokenString())) {
            int offset = this.lexer.getCurrentOffset();

            ExpressionDescriptor descriptor = getDescriptor();
            nextToken();
            Expression expression2 = parseChompExpression();

            try {
                expression = descriptor.createExpression(expression, expression2);
            } catch (ExpressionInitializationException e) {
                // Should never be thrown.
                throw new ParseException("Wrong number of arguments for expression " + descriptor.getName(),
                        offset);
            }
        }

        return expression;
    }


    /**
     * Chomps an expression.
     */
    private Expression parseChompExpression() throws ParseException {
        int chompOffset = this.lexer.getCurrentOffset();
        String chompTokenString = this.lexer.getTokenString();

        // parse a number.
        if (this.token == Token.NUMBER) {
            String numberString = this.lexer.getTokenString();

            // The parseNumber will throw a parse exception.
            Expression exp = new ConstantExpression(convertNumber(numberString));
            nextToken();
            return exp;
        }

        // parse a variable.
        if (this.token == Token.PARAMETER) {
            if (this.lexer.getTokenString().equals("pi") || this.lexer.getTokenString().equals("PI")) {
                nextToken();
                return ConstantExpression.PI;
            }

            if (this.lexer.getTokenString().equals("e") || this.lexer.getTokenString().equals("E")) {
                nextToken();
                return ConstantExpression.E;
            }

            String stringToken = this.lexer.getTokenString();
            if (getRestrictionType() == RestrictionType.MAY_ONLY_CONTAIN) {
                if (!this.restrictionParameters.contains(stringToken)) {
                    throw new ParseException("Variable " + stringToken + " is not known.", chompOffset);
                }
            } else if (getRestrictionType() == RestrictionType.MAY_NOT_CONTAIN) {
                if (this.restrictionParameters.contains(stringToken)) {
                    throw new ParseException("Variable " + stringToken + " may not be used in this expression.", chompOffset);
                }
            }

            this.parameters.add(stringToken);
            VariableExpression exp = new VariableExpression(stringToken);
            nextToken();
            if (this.token == Token.EQUATION) {
                return parseEvaluation(exp);
            }

            return exp;
        }

        // deal with prefix operator.
        if (this.token == Token.OPERATOR) {
            ExpressionDescriptor descriptor = getDescriptor();
            nextToken();
            Expression[] expressions;

            if (this.token == Token.LPAREN) {
                nextToken();
                if (this.token == Token.RPAREN) {
                    nextToken();
                    expressions = new Expression[0];
                } else {
                    List<Expression> expressionList = parseExpressionList();
                    expect(Token.RPAREN);
                    expressions = expressionList.toArray(new Expression[0]);
                }
            } else if ("+".equals(chompTokenString) || "-".equals(chompTokenString)) {
                List<Expression> expressionList = parseSingleExpression();
                expressions = expressionList.toArray(new Expression[0]);
            } else {
                throw new ParseException("Expecting a parenthesized list of arguments.", chompOffset);
            }

            try {
                return descriptor.createExpression(expressions);
            } catch (ExpressionInitializationException e) {
                throw new ParseException("Wrong number of arguments: " + expressions.length + " " + this.token, chompOffset);
            }

        }

        // deal with parens.
        if (this.token == Token.LPAREN) {
            nextToken();
            Expression exp = parseExpression();
            expect(Token.RPAREN);
            return exp;
        }

        throw new ParseException("Unexpected token: " + this.lexer.getTokenString(), this.lexer.getCurrentOffset());
    }


    /**
     * Creates an evaluation expression.
     */
    private Expression parseEvaluation(VariableExpression variable) throws ParseException {
        expect(Token.EQUATION);
        if (this.token != Token.STRING) {
            throw new ParseException("Evaluations must be of the form Var = String", this.lexer.getCurrentOffset());
        }
        String s = this.lexer.getTokenString();
        nextToken();
        return new EvaluationExpression(variable, s.replace("\"", ""));
    }


    /**
     * Pareses a comma seperated list of expressions.
     */
    private List<Expression> parseExpressionList() throws ParseException {
        List<Expression> expressions = new LinkedList<>();

        expressions.add(parseExpression());
        while (this.token == Token.COMMA) {
            nextToken();
            expressions.add(parseExpression());
        }

        return expressions;
    }

    /**
     * Pareses a comma seperated list of expressions.
     */
    private List<Expression> parseSingleExpression() throws ParseException {
        List<Expression> expressions = new LinkedList<>();
        expressions.add(parseExpression());
        return expressions;
    }

    private double convertNumber(String number) throws ParseException {
        try {
            return Double.parseDouble(number);
        } catch (Exception ex) {
            throw new ParseException("Not a number: " + number + ".", this.lexer.getCurrentOffset());
        }
    }


    /**
     * @return the descriptor represented by the getModel token or throws an exception if there isn't one.
     */
    private ExpressionDescriptor getDescriptor() throws ParseException {
        String tokenString = this.lexer.getTokenString();
        ExpressionDescriptor descriptor = this.expressions.getDescriptorFromToken(tokenString);
        if (descriptor == null) {
            throw new ParseException("Not a function name: " + tokenString, this.lexer.getCurrentOffset());
        }
        return descriptor;
    }


    /**
     * Expects the given token and then reads the next token.
     */
    private void expect(Token token) throws ParseException {
        if (token != this.token) {
            throw new ParseException("Unexpected token: " + getTokenString(), this.lexer.getCurrentOffset());
        }
        nextToken();
    }


    private RestrictionType getRestrictionType() {
        return this.restrictionType;
    }

    public List<String> getParameters() {
        return new LinkedList<>(this.parameters);
    }

    private String getTokenString() {
        return this.lexer.getTokenString();
    }
}



