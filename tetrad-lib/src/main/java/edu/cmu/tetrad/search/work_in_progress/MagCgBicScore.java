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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.score.ConditionalGaussianScore;
import edu.cmu.tetrad.search.score.Score;

import java.util.*;

/**
 * Gives a BIC score for a linear, Gaussian MAG (Mixed Ancestral Graph). It will perform the same as SemBicScore for
 * DAGs.
 *
 * <p>As for all scores in Tetrad, higher scores mean more dependence, and negative
 * scores indicate independence.</p>
 *
 * @author Bryan Andrews
 * @version $Id: $Id
 */
@Experimental
public class MagCgBicScore implements Score {

    private final ConditionalGaussianScore score;

    private Graph mag;

    private List<Node> order;

    /**
     * Constructor.
     *
     * @param dataSet The covarainces to analyze.
     */
    public MagCgBicScore(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.score = new ConditionalGaussianScore(dataSet, 1, true);
        this.mag = null;
        this.order = null;
    }

    /**
     * Constructor.
     *
     * @param dataSet               The continuous dataset to analyze.
     * @param precomputeCovariances a boolean
     */
    public MagCgBicScore(DataSet dataSet, boolean precomputeCovariances) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.score = new ConditionalGaussianScore(dataSet, 1.0, precomputeCovariances);
        this.mag = null;
        this.order = null;
    }

    /**
     * Returns the wrapped MAG.
     *
     * @return This MAG.
     */
    public Graph getMag() {
        return this.mag;
    }

    /**
     * Sets the MAG to wrap.
     *
     * @param mag This MAG.
     */
    public void setMag(Graph mag) {
        this.mag = mag;
    }

    /**
     * Sets the MAG to null.
     */
    public void resetMag() {
        this.mag = null;
    }

    /**
     * Returns the order.
     *
     * @return The order of variables, a list.
     */
    public List<Node> getOrder() {
        return this.order;
    }

    /**
     * Sets the order.
     *
     * @param order The order of variables, a list.
     */
    public void setOrder(List<Node> order) {
        this.order = order;
    }

    /**
     * Sets the order ot null.
     */
    public void resetOrder() {
        this.order = null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Return the BIC score for a node given its parents.
     */
    @Override
    public double localScore(int i, int... js) {
        if (this.mag == null || this.order == null) {
            return this.score.localScore(i, js);
        }

        double score = 0;

        Node v1 = this.score.getVariables().get(i);

        List<Node> mbo = new ArrayList<>();
        Arrays.sort(js);
        for (Node v2 : this.order) {
            if (Arrays.binarySearch(js, this.score.getVariables().indexOf(v2)) >= 0) {
                mbo.add(v2);
            }
        }

        List<List<Node>> heads = new ArrayList<>();
        List<Set<Node>> tails = new ArrayList<>();
        constructHeadsTails(heads, tails, mbo, new ArrayList<>(), new ArrayList<>(), new HashSet<>(), v1);

        for (int l = 0; l < heads.size(); l++) {
            List<Node> head = heads.get(l);
            Set<Node> tail = tails.get(l);

            head.remove(v1);
            int h = head.size();
            int max = h + tail.size();
            for (int j = 0; j < 1 << h; j++) {
                List<Node> condSet = new ArrayList<>(tail);
                for (int k = 0; k < h; k++) {
                    if ((j & (1 << k)) > 0) {
                        condSet.add(head.get(k));
                    }
                }

                int[] parents = new int[j];
                for (int k = 0; k < j; k++) {
                    parents[k] = this.score.getVariables().indexOf(condSet.get(k));
                }

                if (((max - condSet.size()) % 2) == 0) {
                    score += this.score.localScore(i, parents);
                } else {
                    score -= this.score.localScore(i, parents);
                }

//                System.out.print((((max - condSet.size()) % 2) == 0) ? " + " : " - ");
//                System.out.print(v1);
//                System.out.print(" | ");
//                System.out.println(condSet);
            }
//            System.out.println();
        }
        return score;
    }

    /**
     * <p>getPenaltyDiscount.</p>
     *
     * @return The penalty discount, a multiplier on the penalty term of BIC.
     */
    public double getPenaltyDiscount() {
        return this.score.getPenaltyDiscount();
    }

    /**
     * Seets the penalty discount.
     *
     * @param penaltyDiscount This number, a multiplier on the penalty term of BIC.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.score.setPenaltyDiscount(penaltyDiscount);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the sample size.
     */
    @Override
    public int getSampleSize() {
        return this.score.getSampleSize();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the list of variables.
     */
    @Override
    public List<Node> getVariables() {
        return this.score.getVariables();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a judgment for FGES as to whether an edges with this bump (for this score) counts as an effect edge.
     *
     * @see Fges
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a judgment of the max degree needed for this score.
     *
     * @see Fges
     */
    @Override
    public int getMaxDegree() {
        return this.score.getMaxDegree();
    }

    private void constructHeadsTails(List<List<Node>> heads, List<Set<Node>> tails, List<Node> mbo, List<Node> head, List<Node> in, Set<Node> an, Node v1) {
        /*
          Calculates the head and tails of a MAG for vertex v1 and ordered Markov blanket mbo.
         */

        head.add(v1);
        heads.add(head);

        List<Node> sib = new ArrayList<>();
        updateAncestors(an, v1);
        updateIntrinsics(in, sib, an, v1, mbo);

        Set<Node> tail = new HashSet<>(in);
        head.forEach(tail::remove);
        for (Node v2 : in) {
            tail.addAll(this.mag.getParents(v2));
        }
        tails.add(tail);

        for (Node v2 : sib) {
            constructHeadsTails(heads, tails, mbo.subList(mbo.indexOf(v2) + 1, mbo.size()), new ArrayList<>(head), new ArrayList<>(in), new HashSet<>(an), v2);
        }
    }

    private void updateAncestors(Set<Node> an, Node v1) {
        an.add(v1);

        for (Node v2 : this.mag.getParents(v1)) {
            updateAncestors(an, v2);
        }
    }

    private void updateIntrinsics(List<Node> in, List<Node> sib, Set<Node> an, Node v1, List<Node> mbo) {
        in.add(v1);

        List<Node> mb = new ArrayList<>(mbo);
        mb.removeAll(in);

        for (Node v3 : in.subList(0, in.size())) {
            for (Node v2 : mb) {
                Edge e = this.mag.getEdge(v2, v3);
                if (e != null && e.getEndpoint1() == Endpoint.ARROW && e.getEndpoint2() == Endpoint.ARROW) {
                    if (an.contains(v2)) {
                        updateIntrinsics(in, sib, an, v2, mbo);
                    } else {
                        sib.add(v2);
                    }
                }
            }
        }
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link String} object
     */
    public String toString() {
        return "MAG(" + this.score + ")";
    }

}
