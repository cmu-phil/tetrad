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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;

import java.util.*;

/**
 * Provides static methods for creating Bayes nets.
 *
 * @author David Danks
 * @author Joseph Ramsey modifications for BayesIm.
 * @author Juan Casares modifications 2001/10/20
 */
final class BayesImFactory {

    /**
     * This method creates a BayesNetIM with the particular parameters given in
     * the method argument.  This method randomly assigns probabilities to the
     * Bayes net.
     *
     * @param vars    An array of variable names.
     * @param varVals An array of Lists of variable values.
     * @param edges   An array of edge names in the form "FromVar->ToVar".
     * @return The BayesNetIM for these parameters.
     */
    public static BayesIm createBayesNet(final String[] vars,
            final List<String>[] varVals, final String[] edges) {
        return createBayesNet(vars, varVals, edges, null);
    }

    /**
     * This method creates a BayesNetIM with the particular parameters given in
     * the method argument.  This method randomly assigns probabilities to the
     * Bayes net.
     *
     * @param vars    An array of variable names
     * @param varVals A 2-d String array of variable values
     * @param edges   An array of edge names in the form "FromVar->ToVar"
     * @return The BayesNetIM for these parameters
     */
    public static BayesIm createBayesNet(final String[] vars,
            final String[][] varVals, final String[] edges) {

        final List<String>[] valVect = new ArrayList[varVals.length];

        for (int i = 0; i < valVect.length; i++) {
            valVect[i] = new ArrayList<String>();

            for (int j = 0; j < varVals[i].length; j++) {
                valVect[i].add(varVals[i][j]);
            }
        }

        return createBayesNet(vars, valVect, edges, null);
    }

    /**
     * This method creates a BayesNetIM with the particular parameters given in
     * the method argument.  No error checking is done in this method.
     *
     * @param vars    An array of variable names
     * @param varVals A 2-d String array of variable values
     * @param edges   An array of edge names in the form "FromVar->ToVar"
     * @param probs   A 2-d array of Strings in the following form:
     *                "VAL|par1=val;...;parN=val|PROB", with the array
     *                structured as [var#][pValue]
     * @return The BayesNetIM for these parameters
     */
    public static BayesIm createBayesNet(final String[] vars,
            final String[][] varVals, final String[] edges,
            final String[][] probs) {

        final List<String>[] valVect = new ArrayList[varVals.length];

        for (int i = 0; i < valVect.length; i++) {
            valVect[i] = new ArrayList<String>();

            for (int j = 0; j < varVals[i].length; j++) {
                valVect[i].add(varVals[i][j]);
            }
        }

        return createBayesNet(vars, valVect, edges, probs);
    }

    /**
     * This method creates a BayesNetIM with the particular parameters given in
     * the method argument.  No error checking is done in this method.
     *
     * @param vars    An array of variable names
     * @param varVals An array of Lists of variable values
     * @param edges   An array of edge names in the form "FromVar->ToVar"
     * @param probs   A 2-d array of Strings in the following form:
     *                "VAL|par1=val;...;parN=val|PROB", with the array
     *                structured as [var#][pValue]
     * @return The BayesNetIM for these parameters
     */
    private static BayesIm createBayesNet(final String[] vars,
            final List<String>[] varVals, final String[] edges,
            final String[][] probs) {

        // construct the DAG
        final Dag dag = new Dag();

        for (String var : vars) {
            Node v = new GraphNode(var);
            dag.addNode(v);
        }

        for (String edge : edges) {
            StringTokenizer st = new StringTokenizer(edge, "->");
            Node node1 = dag.getNode(st.nextToken());
            Node node2 = dag.getNode(st.nextToken());
            dag.addDirectedEdge(node1, node2);
        }

        // now construct the BayesPm
        BayesPm bayesPm = new BayesPm(dag);

        for (int i = 0; i < varVals.length; i++) {
            try {
                Node node = dag.getNode(vars[i]);
                List<String> categories = new LinkedList<String>();

                for (int j = 0; j < varVals[i].size(); j++) {
                    categories.add(varVals[i].get(j));
                }

                bayesPm.setCategories(node, categories);
            }
            catch (Exception e) {
                throw new RuntimeException("Problem setting node values.", e);
            }
        }

        // now construct the BayesIm
        BayesIm bnim = new MlBayesIm(bayesPm);

        // if the user doesn't specify probabilities, just use the randoms
        if (probs == null) {
            return bnim;
        }

        // otherwise, use the given probs
        for (int i = 0; i < probs.length; i++) {
            Node childNode = dag.getNode(vars[i]);
            int nodeIndex = bnim.getNodeIndex(childNode);

            for (int j = 0; j < probs[i].length; j++) {

                // figure out what pValue[i][j] encodes
                StringTokenizer st = new StringTokenizer(probs[i][j], "|");
                boolean areConds = (st.countTokens() == 3);
                String childNodeValName = st.nextToken();
                String[] conds = new String[0];

                if (areConds) {
                    StringTokenizer c =
                            new StringTokenizer(st.nextToken(), ";");

                    conds = new String[c.countTokens()];

                    for (int q = 0; q < conds.length; q++) {
                        conds[q] = c.nextToken();
                    }
                }

                double prob;

                try {
                    prob = Double.parseDouble(st.nextToken());
                }
                catch (NumberFormatException nfe) {
                    System.err.println("Bad probability: " + nfe.toString());

                    return null;
                }

                // now figure out which parameter this refers to
                // cond[] has tokens of the form par=val.
                // if no conds, parVals.size is zero
                //                int[] parents = bnim.getParents(nodeIndex);
                //                int[] parVals = new int[parents.length];

                int numParents = bnim.getNumParents(nodeIndex);
                int[] parVals = new int[numParents];

                Arrays.fill(parVals, -1);

                // get the conditioning variables values
                // only do this if there are conds
                if (areConds) {
                    for (int k = 0; k < conds.length; k++) {
                        StringTokenizer d = new StringTokenizer(conds[k], "=");
                        d.nextToken(); // Ignore--skip the parent name.
                        String valName = d.nextToken();
                        int parent = bnim.getParents(nodeIndex)[k];
                        parVals[k] = bayesPm.getCategoryIndex(
                                bnim.getNode(parent), valName);
                    }
                }

                // make sure values were specified for every conditioning variable
                for (int parVal : parVals) {
                    if (parVal == -1) {
                        System.out.println("Incomplete specification.");

                        throw new IllegalArgumentException(
                                "Incomplete specification.");
                    }
                }

                int rowIndex = bnim.getRowIndex(nodeIndex, parVals);
                int colIndex =
                        bayesPm.getCategoryIndex(childNode, childNodeValName);

                // assign this probability
                bnim.setProbability(nodeIndex, rowIndex, colIndex, prob);
            }
        }

        return bnim;
    }
}





