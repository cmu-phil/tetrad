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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.calculator.parser.ExpressionParser;
import edu.cmu.tetrad.graph.Node;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Expands templates for the generalized SEM PM. The template provided needs to be parseable, but the
 * expanded template may not be parseable. If it is not, the original template was ill-formedm, and
 * the expansion should be disgarded.
 *
 * @author Joseph Ramsey
 */
public class TemplateExpander {
    private static TemplateExpander INSTANCE = new TemplateExpander();

    private TemplateExpander() {
    }

    public static TemplateExpander getInstance() {
        return INSTANCE;
    }

    /**
     * @param template A template formula, which includes the functions TSUM and TPROD, which are not nestable,
     *                 and NEW. TSUM(f($)) = f(X1) + f(X2) + .... TPROD(f($)) = f(X1) * f(X2) * .... NEW(a) = a new parameter with
     *                 name beginning with a, such as a3, if a1 and a2 have already been used.
     * @param semPm    The generalized SEM PM for which this is intended. This is needed in order to figure out what
     *                 the parents of <code>node</code> are, and in order to determine new freeParameters. May be null. If it is null,
     *                 then the template may not contain any TSUM, TPROD, or NEW expressions.
     * @param node     The node this is intended for, if there is one. Like I said, the parents of a node need to
     *                 be calculated. This may be null. If it is null, the template may not contain any TSUM or TPROD expressions
     *                 --i.e. any TSUM or TPROD expressions--but it may contain NEW expressions. If semPm is null, then node must
     *                 be null as well, since the node is relative to a generalized SEM PM.
     * @return The expanded template.
     * @throws ParseException for any of a variety of reasons. It may be that the original template cannot be parsed.
     *                        It may be that the expanded formula without the error term added cannot be parsed. It may be that the
     *                        expanded formula contains a "$", which means there was a "$" that was not properly embedded in a TSUM or
     *                        TPROD. It may be that a TSUM or TPROD was embedded inside another TSUM or TPROD.
     */
    public String expandTemplate(String template, GeneralizedSemPm semPm, Node node) throws ParseException {
        ExpressionParser parser = new ExpressionParser();
        List<String> usedNames;

        if (semPm == null && template.contains("$")) {
            throw new IllegalArgumentException("If semPm is null, the template may not contain any parameters or " +
                    "$ expressions.");
        }

        if (semPm == null && node != null) {
            throw new IllegalArgumentException("If semPm is not specified, then node may not be specified either. The" +
                    " node must be for a specific generalized SEM PM.");
        }

        // Must make sure the original template is parseable.
        parser.parseExpression(template);
        usedNames = parser.getParameters();

        template = replaceTemplateSums(semPm, template, node);
        template = replaceTemplateProducts(semPm, template, node);
        template = replaceNewParameters(semPm, template, usedNames);
        template = replaceError(semPm, template, node);

        Node error = null;

        if (node != null) {
            error = semPm.getErrorNode(node);
        }

        template = template.trim();

        if (template.equals("")) {
            template = "";
        }

        if (!"".equals(template)) {

            // This will throw an exception if the expansion without the error term added is not parseable.
            try {
                parser.parseExpression(template);
            } catch (ParseException e) {
                template = "";
            }

            if (node == null && !parser.getParameters().isEmpty()) {
                throw new IllegalArgumentException("If node is null, the template may not contain any $ expressions.");
            }
        }

        if (node != null && node != error && !template.contains(error.getName())) {
            if (template.trim().equals("")) {
                template = error.getName();
            } else {
                template += " + " + error.getName();
            }
        }

        if (template.contains("$")) {
            throw new ParseException("Template contains a $ not inside TSUM or TPROD.", template.indexOf("$"));
        }

        return template;
    }

    private String replaceTemplateSums(GeneralizedSemPm semPm, String formula, Node node)
            throws ParseException {
        formula = replaceLists("TSUM", semPm, formula, node);
        formula = replaceLists("tsum", semPm, formula, node);
        return formula;
    }

    private String replaceTemplateProducts(GeneralizedSemPm semPm, String formula, Node node)
            throws ParseException {
        formula = replaceLists("TPROD", semPm, formula, node);
        formula = replaceLists("tprod", semPm, formula, node);
        return formula;
    }

    private String replaceLists(String operator, GeneralizedSemPm semPm, String formula, Node node)
            throws ParseException {
        List<String> templateOperators = new ArrayList<String>();
        templateOperators.add("TSUM");
        templateOperators.add("TPROD");
        templateOperators.add("tsum");
        templateOperators.add("tprod");

        Pattern p = Pattern.compile(Pattern.quote(operator));

        while (true) {
            Matcher m = p.matcher(formula);

            if (!m.find()) {
                break;
            }

            // Count parentheses.
            int numLeft = 0;
            int numRight = 0;
            int pos;

            for (pos = m.end(); pos < formula.length(); pos++) {
                char c = formula.charAt(pos);

                if (c == '(') numLeft++;
                else if (c == ')') numRight++;

                if (numLeft == numRight) {
                    break;
                }
            }

            String target = formula.substring(m.end() + 1, pos);

//            if (!target.contains("$")) {
//                throw new ParseException("Templating operators only apply to expressions containg $.", 0);
//            }

            for (String _operator : templateOperators) {
                if (operator.equals(_operator)) continue;

                if (target.contains(_operator)) {
                    throw new ParseException("Template operators may not be nested.", m.end() + target.indexOf(_operator));
                }
            }

            List<Node> parents = new ArrayList<Node>();

            if (semPm != null && node != null) {
                parents = semPm.getParents(node);
            }

            StringBuilder buf = new StringBuilder();

            for (int j = 0; j < parents.size(); j++) {
                Node parent = parents.get(j);

                if (!semPm.getVariableNodes().contains(parent)) {
                    continue;
                }

                String copy = target;
                copy = copy.replaceAll("\\$", parent.getName());
                buf.append(copy);

                if (j < parents.size() - 2) {
                    if (operator.equals("TSUM") || operator.equals("tsum")) {
                        buf.append(" + ");
                    } else if (operator.equals("TPROD") || operator.equals("tprod")) {
                        buf.append(" * ");
                    }
                }
            }

            String toReplace = formula.substring(m.start(), pos + 1);

            toReplace = Pattern.quote(toReplace);
            String replacement = buf.toString();
            formula = formula.replaceFirst(toReplace, replacement);
        }

        formula = removeOperatorStrings(formula);

        // lop off initial + or *.
        formula = formula.trim();

        if (formula.startsWith("+")) {
            formula = formula.substring(1, formula.length());
            formula = formula.trim();
        }

        if (formula.startsWith("*")) {
            formula = formula.substring(1, formula.length());
            formula = formula.trim();
        }

        return formula;
    }

    private String replaceError(GeneralizedSemPm semPm, String formula, Node node) {
        Node error = semPm.getErrorNode(node);

        if (error != null) {
            return formula.replaceAll("ERROR", error.getName());
        } else return formula;
    }

    private String removeOperatorStrings(String formula) {
        // Some rewrite rules to get rid or strings of +'s or *'s. Not perfect.
        boolean found = true;

        WHILE:
        while (found) {
            found = false;

            List<Character> operatorList = new ArrayList<Character>();
            int first = 0;
            int last = 0;

            for (int i = last; i < formula.length(); i++) {
                char symbol = formula.charAt(i);
                boolean plusOrTimes = '+' == symbol || '*' == symbol;
                boolean space = ' ' == symbol;

                if (space) {
                    // continue; // (last statement)
                } else if (plusOrTimes) {
                    if (operatorList.isEmpty()) {
                        first = i;
                    }

                    operatorList.add(symbol);
                } else {
                    last = i - 1;

                    if (operatorList.size() > 1) {
                        found = true;
                        boolean allStar = true;

                        for (Character c : operatorList) {
                            if (c != '*') {
                                allStar = false;
                                break;
                            }
                        }

                        if (allStar) {
                            formula = formula.substring(0, first - 1) + " * " +
                                    formula.substring(last + 1, formula.length());
                        } else {
                            formula = formula.substring(0, first - 1) + " + " +
                                    formula.substring(last + 1, formula.length());
                        }
                    }

                    operatorList.clear();
                    continue WHILE;
                }
            }
        }
        return formula;
    }

    private String replaceNewParameters(GeneralizedSemPm semPm, String formula, List<String> usedNames) {
        String parameterPattern = "\\$|(([a-zA-Z]{1})([a-zA-Z0-9-_/]*))";
        Pattern p = Pattern.compile("NEW\\((" + parameterPattern + ")\\)");

        while (true) {
            Matcher m = p.matcher(formula);

            if (!m.find()) {
                break;
            }

            String group0 = Pattern.quote(m.group(0));
            String group1 = m.group(1);

            String nextName = semPm.nextParameterName(group1, usedNames);
            formula = formula.replaceFirst(group0, nextName);
            usedNames.add(nextName);
        }

        Pattern p2 = Pattern.compile("new\\((" + parameterPattern + ")\\)");

        while (true) {
            Matcher m = p2.matcher(formula);

            if (!m.find()) {
                break;
            }

            String group0 = Pattern.quote(m.group(0));
            String group1 = m.group(1);

            String nextName = semPm.nextParameterName(group1, usedNames);
            formula = formula.replaceFirst(group0, nextName);
            usedNames.add(nextName);
        }

        return formula;
    }
}


