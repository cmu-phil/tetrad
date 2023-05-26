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

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IndependenceFacts;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.Fges;

import javax.help.UnsupportedOperationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * <p>Implements a pscudo-"score" that implmenets implements Chickering and Meek's
 * (2002) locally consistent score criterion. This is not a true score; rather, a -1 is returned in case dseparation
 * holds and a 1 in case dseparation does not hold. This is only meant to be used in the context of FGES, and allows the
 * search to follow its path prescribed by the locally consistent scoring criterion. For a reference to the latter,
 * pleasee this article:</p>
 *
 * <p>Chickering (2002) "Optimal structure identification with greedy search"
 * Journal of Machine Learning Research.</p>
 *
 * <p>For further discussion of using d-separation in the GES search, see:</p>
 *
 * <p>Nandy, P., Hauser, A., &amp; Maathuis, M. H. (2018). High-dimensional consistency
 * in score-based and hybrid structure learning. The Annals of Statistics, 46(6A), 3151-3183.</p>
 *
 * <p>For more discussion please see:</p>
 *
 * <p>Shen, X., Zhu, S., Zhang, J., Hu, S., &amp; Chen, Z. (2022, August). Reframed GES
 * with a neural conditional dependence measure. In Uncertainty in Artificial Intelligence (pp. 1782-1791). PMLR.</p>
 *
 * @author josephramsey
 * @see Fges
 */
public class GraphScore implements Score {

    private Graph dag;
    private IndependenceFacts facts;

    // The variables of the covariance matrix.
    private final List<Node> variables;

    // True if verbose output should be sent to out.
    private boolean verbose = false;

    /**
     * Constructor
     *
     * @param dag A directed acyclic graph.
     */
    public GraphScore(Graph dag) {
        this.dag = dag;
        this.variables = new ArrayList<>(dag.getNodes());
        this.variables.removeIf(node -> node.getNodeType() == NodeType.LATENT);
    }

    /**
     * Constructor.
     *
     * @param facts A list known independence facts; a lookup will be donw from these facts.
     * @see IndependenceFacts
     */
    public GraphScore(IndependenceFacts facts) {
        this.facts = facts;
        this.variables = new ArrayList<>(facts.getVariables());
        this.variables.removeIf(node -> node.getNodeType() == NodeType.LATENT);
    }

    /**
     * Calculates the sample likelihood and BIC score for y given its z in a simple SEM model.
     *
     * @return this score.
     */
    public double localScore(int y, int[] z) {
        return getPearlParentsTest().size();
    }

    private Node n = null;
    private List<Node> prefix = null;

    private Set<Node> getPearlParentsTest() {
        Set<Node> mb = new HashSet<>();

        for (Node z0 : prefix) {
            List<Node> cond = new ArrayList<>(prefix);
            cond.remove(z0);

            if (dag.paths().isDConnectedTo(n, z0, cond)) {
                mb.add(z0);
            }
        }

        return mb;
    }


    /**
     * Returns a "score difference", which amounts to a conditional local scoring criterion results
     *
     * @return The "difference".
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return locallyConsistentScoringCriterion(x, y, z);
    }

    /**
     * The "unconditional difference."
     *
     * @return This.
     */
    @Override
    public double localScoreDiff(int x, int y) {
        return localScoreDiff(x, y, new int[0]);
    }

    /**
     * @throws UnsupportedOperationException Since the method doesn't make sense here.
     */
    public double localScore(int i, int parent) {
        throw new UnsupportedOperationException();
    }

    /**
     * @throws UnsupportedOperationException Since the method doesn't make sense here.
     */
    public double localScore(int i) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a judgment for FGES as to whether a score with the bump is for an effect edge.
     *
     * @param bump The bump
     * @return True if so.
     * @see Fges
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * @throws UnsupportedOperationException Since the method doesn't make sense here.
     */
    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the list of variables.
     *
     * @return This list.
     */
    @Override
    public List<Node> getVariables() {
        return variables;
    }

    /**
     * Returns the maximum degree, which is set to 1000.
     *
     * @return 1000.
     */
    @Override
    public int getMaxDegree() {
        return 1000;
    }

    /**
     * @throws UnsupportedOperationException Since this method doesn't make sense here.
     */
    @Override
    public boolean determines(List<Node> z, Node y) {
        throw new UnsupportedOperationException("The 'determines' method is not implemented for this score.");
    }

    /**
     * @throws UnsupportedOperationException Since this "score" does not use data.
     */
    public DataModel getData() {
        throw new UnsupportedOperationException("This score does not use data.");
    }

    /**
     * @throws UnsupportedOperationException Since this score does not use data.
     */
    public int getSampleSize() {
        throw new UnsupportedOperationException("This score does not use data, so no sample size is available.");
    }

    /**
     * Returns a copy of the DAG being searched over.
     *
     * @return This DAG.
     */
    public Graph getDag() {
        return new EdgeListGraph(dag);
    }

    private double locallyConsistentScoringCriterion(int x, int y, int[] z) {
        Node _y = variables.get(y);
        Node _x = variables.get(x);
        List<Node> _z = getVariableList(z);

        boolean dSeparatedFrom;

        if (dag != null) {
            dSeparatedFrom = dag.paths().isDSeparatedFrom(_x, _y, _z);
        } else if (facts != null) {
            dSeparatedFrom = facts.isIndependent(_x, _y, _z);
        } else {
            throw new IllegalStateException("Expecting either a graph or a IndependenceFacts object.");
        }

        return dSeparatedFrom ? -1.0 : 1.0;
    }

    private boolean isDSeparatedFrom(Node x, Node y, List<Node> z) {
        if (dag != null) {
            return dag.paths().isDSeparatedFrom(x, y, z);
        } else if (facts != null) {
            return facts.isIndependent(x, y, z);
        }

        throw new IllegalArgumentException("Expecting either a DAG or an IndependenceFacts object.");
    }

    private boolean isDConnectedTo(Node x, Node y, List<Node> z) {
        return !isDSeparatedFrom(x, y, z);
    }

    private List<Node> getVariableList(int[] indices) {
        List<Node> variables = new ArrayList<>();
        for (int i : indices) {
            variables.add(this.variables.get(i));
        }
        return variables;
    }
}



