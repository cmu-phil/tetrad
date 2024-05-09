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

import edu.cmu.tetrad.data.IndependenceFacts;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.Fges;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements a pscudo-"score" that implmenets implements Chickering and Meek's (2002) locally consistent score
 * criterion. This is not a true score; rather, a -1 is returned in case mseparation holds and a 1 in case mseparation
 * does not hold. This is only meant to be used in the context of FGES, and allows the search to follow its path
 * prescribed by the locally consistent scoring criterion. For a reference to the latter, pleasee this article:
 * <p>
 * Chickering (2002) "Optimal structure identification with greedy search" Journal of Machine Learning Research.
 * <p>
 * For further discussion of using m-separation in the GES search, see:
 * <p>
 * Nandy, P., Hauser, A., &amp; Maathuis, M. H. (2018). High-dimensional consistency in score-based and hybrid structure
 * learning. The Annals of Statistics, 46(6A), 3151-3183.
 * <p>
 * For more discussion please see:
 * <p>
 * Shen, X., Zhu, S., Zhang, J., Hu, S., &amp; Chen, Z. (2022, August). Reframed GES with a neural conditional
 * dependence measure. In Uncertainty in Artificial Intelligence (pp. 1782-1791). PMLR.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see Fges
 */
public class GraphScore implements Score {

    // The variables of the covariance matrix.
    private final List<Node> variables;
    // The DAG, if supplied.
    private Graph dag;
    // The independence facts, if supplied.
    private IndependenceFacts facts;

    /**
     * Constructs a GraphScore from a DAG.
     *
     * @param dag A directed acyclic graph.
     */
    public GraphScore(Graph dag) {
        this.dag = dag;
        this.variables = new ArrayList<>(dag.getNodes());
        this.variables.removeIf(node -> node.getNodeType() == NodeType.LATENT);
    }

    /**
     * Constructs a GraphScore from a list of independence facts.
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
     * @param y a int
     * @param z an array of {@link int} objects
     * @return this score.
     */
    public double localScore(int y, int[] z) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a "score difference", which amounts to a conditional local scoring criterion results. Only difference
     * methods is implemented, since the other methods don't make sense here.
     */
    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return locallyConsistentScoringCriterion(x, y, z);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The "unconditional difference." Only difference methods is implemented, since the other methods don't make sense
     * here.
     */
    @Override
    public double localScoreDiff(int x, int y) {
        return localScoreDiff(x, y, new int[0]);
    }

    /**
     * <p>localScore.</p>
     *
     * @param i      a int
     * @param parent a int
     * @return a double
     * @throws java.lang.UnsupportedOperationException Since the method doesn't make sense here.
     */
    public double localScore(int i, int parent) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public double localScore(int i) {
        throw new UnsupportedOperationException("The 'local score' method is not supported here.");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a judgment for FGES whether a score with the bump is for an effect edge.
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
     * Returns the list of variables.
     */
    @Override
    public List<Node> getVariables() {
        return variables;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the maximum degree, which is set to 1000.
     */
    @Override
    public int getMaxDegree() {
        return 1000;
    }

    /**
     * <p>getSampleSize.</p>
     *
     * @return a int
     * @throws java.lang.UnsupportedOperationException Since the method doesn't make sense here.
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
        Set<Node> _z = getVariableSet(z);

        boolean dSeparatedFrom;

        if (dag != null) {
            dSeparatedFrom = dag.paths().isMSeparatedFrom(_x, _y, _z, false);
        } else if (facts != null) {
            dSeparatedFrom = facts.isIndependent(_x, _y, _z);
        } else {
            throw new IllegalStateException("Expecting either a graph or a IndependenceFacts object.");
        }

        return dSeparatedFrom ? -1.0 : 1.0;
    }

    private boolean isMSeparatedFrom(Node x, Node y, Set<Node> z) {
        if (dag != null) {
            return dag.paths().isMSeparatedFrom(x, y, z, false);
        } else if (facts != null) {
            return facts.isIndependent(x, y, z);
        }

        throw new IllegalArgumentException("Expecting either a DAG or an IndependenceFacts object.");
    }

    private Set<Node> getVariableSet(int[] indices) {
        Set<Node> variables = new HashSet<>();
        for (int i : indices) {
            variables.add(this.variables.get(i));
        }
        return variables;
    }
}



