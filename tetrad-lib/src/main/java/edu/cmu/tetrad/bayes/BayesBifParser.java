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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@code BayesBifParser} class provides a set of static methods for parsing Bayesian Network Interchange Format
 * (BIF) files. The BIF file format is used to represent Bayesian network models.
 *
 * @author josephramsey
 */
public final class BayesBifParser {

    /**
     * Private constructor to prevent instantiation.
     */
    private BayesBifParser() {
    }

    public static BayesIm makeBayesIm(String text) {
        text = text.replace("\n", "");
        text = text.replace("\r", "");
        text = text.replace("\t", "");

        String remainder;

        // Parse the network name.
        Texts s1 = expectWord(text);

        if (!"network".equals(s1.text1)) {
            throw new IllegalArgumentException("Expecting 'network'.");
        }

        remainder = s1.text2;

        Texts s2 = expectWord(remainder.trim());
        remainder = s2.text2;

        Texts s2a = expectCurlyString(remainder.trim());
        remainder = s2a.text2;

        Dag dag = new Dag();

        while (true) {
            Texts t1 = expectWord(remainder.trim());

            if (!"variable".equals(t1.text1.trim())) {
                break;
            }

            // chomp
            remainder = t1.text2;

            Texts t2 = expectWord(remainder.trim());

            Texts t2a = expectCurlyString(t2.text2.trim());
            remainder = t2a.text2;

            Texts t2b = expectWord(t2a.text1.trim());

            if (!"type".equals(t2b.text1.trim())) {
                throw new IllegalArgumentException("Expecting 'type'.");
            }

            Texts t2c = expectWord(t2b.text2.trim());

            if (!"discrete".equals(t2c.text1.trim())) {
                throw new IllegalArgumentException("Expecting 'discrete'.");
            }

            Texts t3 = expectSquareBracketString(t2c.text2.trim());

            int numCategories = Integer.parseInt(t3.text1.trim());

            Texts t4 = expectCurlyString(t3.text2.trim());

            String[] tokens = t4.text1.split(",");

            List<String> categories = new ArrayList<>();

            for (int i = 0; i < numCategories; i++) {
                categories.add(tokens[i].trim());
            }

            DiscreteVariable var = new DiscreteVariable(t2.text1.trim(), categories);
            dag.addNode(var);
        }

        System.out.println(dag);

        String _remainder = remainder;

        // Go through once to get the DAG structure.

        do {
            if (remainder.trim().isBlank()) {
                break;
            }

            System.out.println(remainder);

            Texts t5 = expectWord(remainder);

            if (!"probability".equals(t5.text1.trim())) {
                break;
            }

            // Chomp.
            remainder = t5.text2.trim();

            Texts t6 = expectParenthesizedExpression(t5.text2.trim());

            if (t6 == null) {
                throw new IllegalArgumentException("Expecting parenthesized expression.");
            }

            String[] split;

            if (t6.text1 != null) {
                split = t6.text1.split("\\|");
                remainder = t6.text2.trim();
            } else {
                split = new String[0];
            }

            String childName = split[0].trim();
            Node child = dag.getNode(childName);

            if (child == null) {
                throw new IllegalArgumentException("Variable " + childName + " not found.");
            }

            if (split.length > 1) {
                String[] parentNames = split[1].trim().split(",");
                List<DiscreteVariable> parents = new ArrayList<>();
                for (String parentName : parentNames) {
                    DiscreteVariable parent = (DiscreteVariable) dag.getNode(parentName.trim());
                    if (parent == null) {
                        throw new IllegalArgumentException("Parent variable " + parentName + " not found.");
                    }
                    parents.add(parent);
                }

                for (DiscreteVariable parent : parents) {
                    dag.addDirectedEdge(parent, child);
                }
            }

            Texts t7 = expectCurlyString(remainder.trim());
            remainder = t7.text2;
        } while (true);

        BayesPm pm = new BayesPm(dag);
        MlBayesIm bayesIm = new MlBayesIm(pm);
        remainder = _remainder;

        // OK, go through it once again and get the probabilities.

        do {
            System.out.println(remainder);

            Texts t5 = expectWord(remainder);

            if (!"probability".equals(t5.text1.trim())) {
                break;
            }

            // Chomp.
            remainder = t5.text2.trim();

            Texts t6 = expectParenthesizedExpression(t5.text2.trim());

            if (t6 == null) {
                throw new IllegalArgumentException("Expecting parenthesized expression.");
            }

            String[] split;

            if (t6.text1 != null) {
                split = t6.text1.split("\\|");
                remainder = t6.text2.trim();
            } else {
                split = new String[0];
            }

            String childName = split[0].trim();
            Node child = dag.getNode(childName);

            if (child == null) {
                throw new IllegalArgumentException("Variable " + childName + " not found.");
            }

            List<DiscreteVariable> parents = new ArrayList<>();

            if (split.length > 1) {
                String[] parentNames = split[1].trim().split(",");
                parents = new ArrayList<>();
                for (String parentName : parentNames) {
                    DiscreteVariable parent = (DiscreteVariable) dag.getNode(parentName.trim());
                    if (parent == null) {
                        throw new IllegalArgumentException("Parent variable " + parentName + " not found.");
                    }
                    parents.add(parent);
                }
            }

            int nodeIndex = bayesIm.getNodeIndex(child);

            Texts t7 = expectCurlyString(remainder.trim());
            remainder = t7.text2;

            String[] tokens1 = t7.text1.split(";");

            for (String token : tokens1) {
                Texts t8 = expectParenthesizedExpression(token.trim());

                int[] values = new int[parents.size()];
                String[] tokens2;

                if (t8 == null) {
                    Texts t9 = expectWord(token.trim());

                    String[] split2 = t9.text2.split(",");

                    int rowIndex = 0;

                    for (int i = 0; i < split2.length; i++) {
                        double value = Double.parseDouble(split2[i].trim());
                        bayesIm.setProbability(nodeIndex, rowIndex, i, value);
                    }
                } else {
                    String[] split2 = t8.text1.split(",");

                    for (int i = 0; i < parents.size(); i++) {
                        values[i] = parents.get(i).getCategories().indexOf(split2[i].trim());
                    }

                    tokens2 = t8.text2.trim().split(",");

                    int rowIndex = bayesIm.getRowIndex(nodeIndex, values);

                    for (int i = 0; i < tokens2.length; i++) {
                        double value = Double.parseDouble(tokens2[i].trim());
                        bayesIm.setProbability(nodeIndex, rowIndex, i, value);
                    }
                }
            }
        } while (true);

        return bayesIm;
    }

    /**
     * Parses a word from the beginning of the text and returns that word and the remainder of the text.
     *
     * @param text the line of text
     * @return an array of three strings
     */
    private static Texts expectWord(String text) {
        String[] tokens = text.split("\\s+");
        return new Texts(tokens[0], text.substring(tokens[0].length()));
    }


    /**
     * Parses a parenthesized expression from the beginning of the text and returns that expression and the remainder of
     * the text.
     *
     * @param text the line of text
     * @return the two strings.
     */
    private static Texts expectParenthesizedExpression(String text) {
        if (text.trim().charAt(0) != '(') {
            return null;
        }

        int i = 1;
        int depth = 1;

        while (depth > 0) {
            if (text.charAt(i) == '(') {
                depth++;
            } else if (text.charAt(i) == ')') {
                depth--;
            }

            i++;
        }


        return new Texts(text.substring(1, i - 1), text.substring(i));
    }

    /**
     * Parses an expression starting with '[' and ending with ']' from the beginning of the text and returns that
     * expression and the remainder of the text.
     *
     * @param text the line of text
     * @return the two strings.
     */
    private static Texts expectSquareBracketString(String text) {
        if (text.charAt(0) != '[') {
            throw new IllegalArgumentException("Expecting '['.");
        }

        int i = 1;
        int depth = 1;

        while (depth > 0) {
            if (text.charAt(i) == '[') {
                depth++;
            } else if (text.charAt(i) == ']') {
                depth--;
            }

            i++;
        }

        return new Texts(text.substring(1, i - 1), text.substring(i));
    }

    /**
     * Parses an expression starting with '{' and ending with '}' from the beginning of the text and returns that
     * expression and the remainder of the text.
     *
     * @param text the line of text
     * @return the two strings.
     */
    private static Texts expectCurlyString(String text) {
        if (text.charAt(0) != '{') {
            throw new IllegalArgumentException("Expecting '{'.");
        }

        int i = 1;
        int depth = 1;

        while (depth > 0) {
            if (text.charAt(i) == '{') {
                depth++;
            } else if (text.charAt(i) == '}') {
                depth--;
            }

            i++;
        }

        return new Texts(text.substring(1, i - 1), text.substring(i));
    }

    record Texts(String text1, String text2) {
    }
}





