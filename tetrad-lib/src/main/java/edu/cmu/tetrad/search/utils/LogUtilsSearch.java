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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.algcomparison.statistic.BicEst;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.text.NumberFormat;
import java.util.*;

/**
 * Contains utilities for logging search steps.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LogUtilsSearch {

    /**
     * Constructor.
     */
    public LogUtilsSearch() {
    }

    /**
     * <p>edgeOrientedMsg.</p>
     *
     * @param reason a {@link java.lang.String} object
     * @param edge   a {@link edu.cmu.tetrad.graph.Edge} object
     * @return a {@link java.lang.String} object
     */
    public static String edgeOrientedMsg(String reason, Edge edge) {
        return "Orienting edge (" + reason + "): " + edge;
    }

    /**
     * <p>colliderOrientedMsg.</p>
     *
     * @param note a {@link java.lang.String} object
     * @param x    a {@link edu.cmu.tetrad.graph.Node} object
     * @param y    a {@link edu.cmu.tetrad.graph.Node} object
     * @param z    a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link java.lang.String} object
     */
    public static String colliderOrientedMsg(String note, Node x, Node y, Node z) {
        return "Orienting collider (" + note + "): " + x.getName() + " *-> " +
               y.getName() + " <-* " + z.getName();
    }

    /**
     * <p>colliderOrientedMsg.</p>
     *
     * @param x a {@link edu.cmu.tetrad.graph.Node} object
     * @param y a {@link edu.cmu.tetrad.graph.Node} object
     * @param z a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link java.lang.String} object
     */
    public static String colliderOrientedMsg(Node x, Node y, Node z) {
        return "Orienting collider: " + x.getName() + " *-> " +
               y.getName() + " <-* " + z.getName();
    }

    /**
     * <p>colliderOrientedMsg.</p>
     *
     * @param x      a {@link edu.cmu.tetrad.graph.Node} object
     * @param y      a {@link edu.cmu.tetrad.graph.Node} object
     * @param z      a {@link edu.cmu.tetrad.graph.Node} object
     * @param sepset a {@link java.util.Set} object
     * @return a {@link java.lang.String} object
     */
    public static String colliderOrientedMsg(Node x, Node y, Node z, Set<Node> sepset) {
        return "Orienting collider: " + x.getName() + " *-> " +
               y.getName() + " <-* " + z.getName() + "\t(Sepset = " + sepset +
               ")";
    }

    /**
     * <p>determinismDetected.</p>
     *
     * @param sepset a {@link java.util.Set} object
     * @param x      a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link java.lang.String} object
     */
    public static String determinismDetected(Set<Node> sepset, Node x) {
        return "Determinism detected: " + sepset + " -> " + x.getName();
    }

    /**
     * <p>independenceFactMsg.</p>
     *
     * @param x       a {@link edu.cmu.tetrad.graph.Node} object
     * @param y       a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet a {@link java.util.Set} object
     * @param pValue  a double
     * @return a {@link java.lang.String} object
     */
    public static String independenceFactMsg(Node x, Node y, Set<Node> condSet, double pValue) {
        StringBuilder sb = new StringBuilder();
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        sb.append("Independence accepted: ");
        sb.append(LogUtilsSearch.independenceFact(x, y, condSet));

        if (!Double.isNaN(pValue)) {
            sb.append("\tp = ").append(nf.format(pValue));
        }

        return sb.toString();
    }

    /**
     * <p>dependenceFactMsg.</p>
     *
     * @param x       a {@link edu.cmu.tetrad.graph.Node} object
     * @param y       a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet a {@link java.util.Set} object
     * @param pValue  a double
     * @return a {@link java.lang.String} object
     */
    public static String dependenceFactMsg(Node x, Node y, Set<Node> condSet, double pValue) {
        StringBuilder sb = new StringBuilder();
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        sb.append("Dependent: ");
        sb.append(LogUtilsSearch.independenceFact(x, y, condSet));

        if (!Double.isNaN(pValue)) {
            sb.append("\tp = ").append(nf.format(pValue));
        }

        return sb.toString();
    }


    /**
     * <p>independenceFact.</p>
     *
     * @param x       a {@link edu.cmu.tetrad.graph.Node} object
     * @param y       a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet a {@link java.util.Set} object
     * @return a {@link java.lang.String} object
     */
    public static String independenceFact(Node x, Node y, Set<Node> condSet) {
        StringBuilder sb = new StringBuilder();

        sb.append(x.getName());
        sb.append(" _||_ ");
        sb.append(y.getName());

        Iterator<Node> it = condSet.iterator();

        if (it.hasNext()) {
            sb.append(" | ");
            sb.append(it.next());
        }

        while (it.hasNext()) {
            sb.append(", ");
            sb.append(it.next());
        }

        return sb.toString();
    }

    /**
     * <p>getScoreFact.</p>
     *
     * @param i         a int
     * @param parents   an array of  objects
     * @param variables a {@link java.util.List} object
     * @return a {@link java.lang.String} object
     */
    public static String getScoreFact(int i, int[] parents, List<Node> variables) {
        StringBuilder fact = new StringBuilder(variables.get(i) + " | ");

        for (int j = 0; j < parents.length; j++) {
            int p = parents[j];
            fact.append(variables.get(p));

            if (j < parents.length - 1) {
                fact.append(", ");
            }
        }

        return fact.toString();
    }

    /**
     * <p>getScoreFact.</p>
     *
     * @param i       a {@link edu.cmu.tetrad.graph.Node} object
     * @param parents a {@link java.util.List} object
     * @return a {@link java.lang.String} object
     */
    public static String getScoreFact(Node i, List<Node> parents) {
        StringBuilder fact = new StringBuilder(i + " | ");

        for (int p = 0; p < parents.size(); p++) {
            fact.append(parents.get(p));

            if (p < parents.size() - 1) {
                fact.append(", ");
            }
        }

        return fact.toString();
    }

    /**
     * <p>buildIndexing.</p>
     *
     * @param nodes a {@link java.util.List} object
     * @return a {@link java.util.Map} object
     */
    public static Map<Node, Integer> buildIndexing(List<Node> nodes) {
        Map<Node, Integer> hashIndices = new HashMap<>();

        int i = -1;

        for (Node n : nodes) {
            hashIndices.put(n, ++i);
        }

        return hashIndices;
    }

    /**
     * <p>stampWithScore.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @param score a {@link edu.cmu.tetrad.search.score.Score} object
     */
    public static void stampWithScore(Graph graph, Score score) {
        if (score instanceof GraphScore) return;

        if (true) {//!graph.getAllAttributes().containsKey("Score")) {
            Graph dag = GraphTransforms.dagFromCpdag(graph);
            Map<Node, Integer> hashIndices = buildIndexing(dag.getNodes());

            double _score = 0.0;

            for (Node node : dag.getNodes()) {
                List<Node> x = dag.getParents(node);

                int[] parentIndices = new int[x.size()];

                int count = 0;
                for (Node parent : x) {
                    parentIndices[count++] = hashIndices.get(parent);
                }

                double score1 = score.localScore(hashIndices.get(node), parentIndices);

                if (!Double.isNaN(score1)) _score += score1;
//                _score += score1;
            }

            graph.addAttribute("Score", _score);
        }
    }

    /**
     * <p>stampWithBic.</p>
     *
     * @param graph     a {@link edu.cmu.tetrad.graph.Graph} object
     * @param dataModel a {@link edu.cmu.tetrad.data.DataModel} object
     */
    public static void stampWithBic(Graph graph, DataModel dataModel) {
        if (dataModel != null && (dataModel.isContinuous() || dataModel.isDiscrete())
            && !graph.getAllAttributes().containsKey("BIC")) {
            try {
                graph.addAttribute("BIC", new BicEst().getValue(null, graph, dataModel));
            } catch (Exception e) {
                TetradLogger.getInstance().log("Error computing BIC: " + e.getMessage());
            }
        }
    }
}





