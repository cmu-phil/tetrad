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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements Chickering and Meek's (2002) locally consistent score criterion.
 *
 * @author Joseph Ramsey
 */
public class GraphScore implements Score {

    private final Graph dag;

    // The variables of the covariance matrix.
    private final List<Node> variables;

    // True if verbose output should be sent to out.
    private boolean verbose;

    /**
     * Constructs the score using a covariance matrix.
     */
    public GraphScore(final Graph dag) {
        this.dag = dag;

        this.variables = new ArrayList<>();

        for (final Node node : dag.getNodes()) {
            if (node.getNodeType() == NodeType.MEASURED) {
                this.variables.add(node);
            }
        }
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(final int i, final int[] parents) {
        throw new UnsupportedOperationException();
    }

    private List<Node> getVariableList(final int[] indices) {
        final List<Node> variables = new ArrayList<>();
        for (final int i : indices) {
            variables.add(this.variables.get(i));
        }
        return variables;
    }


    @Override
    public double localScoreDiff(final int x, final int y, final int[] z) {
        return locallyConsistentScoringCriterion(x, y, z);
//        return aBetterScore(x, y, z);
    }

    @Override
    public double localScoreDiff(final int x, final int y) {
        return localScoreDiff(x, y, new int[0]);
//        return localScore(y, x) - localScore(y);
    }

    private double locallyConsistentScoringCriterion(final int x, final int y, final int[] z) {
        final Node _y = this.variables.get(y);
        final Node _x = this.variables.get(x);
        final List<Node> _z = getVariableList(z);
        final boolean dSeparatedFrom = this.dag.isDSeparatedFrom(_x, _y, _z);

//        if (dSeparatedFrom) {
//            System.out.println(SearchLogUtils.independenceFact(_x, _y, _z));
//        } else {
//            System.out.println("\t NOT " + SearchLogUtils.independenceFact(_x, _y, _z));
//        }

        return dSeparatedFrom ? -1.0 : 1.0;
    }

    private double aBetterScore(final int x, final int y, final int[] z) {
        final Node _y = this.variables.get(y);
        final Node _x = this.variables.get(x);
        final List<Node> _z = getVariableList(z);
        final boolean dsep = this.dag.isDSeparatedFrom(_x, _y, _z);
        int count = 0;

        if (!dsep) count++;

        for (final Node z0 : _z) {
            if (this.dag.isDSeparatedFrom(_x, z0, _z)) {
                count += 1;
            }
        }

        final double score = dsep ? -1 - count : 1 + count;

//        if (score == 1) score -= Math.tanh(z.length);
        return score;
    }

    private List<Node> minus(final List<Node> z, final Node z0) {
        final List<Node> diff = new ArrayList<>(z);
        diff.remove(z0);
        return diff;
    }

    int[] append(final int[] parents, final int extra) {
        final int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    /**
     * Specialized scoring method for a single parent. Used to speed up the effect edges search.
     */

    public double localScore(final int i, final int parent) {
        throw new UnsupportedOperationException();
    }

    /**
     * Specialized scoring method for no parents. Used to speed up the effect edges search.
     */
    public double localScore(final int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEffectEdge(final double bump) {
        return bump > 0;
    }

    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    public Node getVariable(final String name) {
        for (final Node node : this.variables) {
            if (node.getName().equals(name)) {
                return node;
            }
        }

        throw new IllegalArgumentException("No variable by that name: " + name);
    }

    @Override
    public int getMaxDegree() {
        return 1000;
    }

    @Override
    public boolean determines(final List<Node> z, final Node y) {
        return false;
    }

    public int getSampleSize() {
        return 0;
    }

    public boolean getAlternativePenalty() {
        return false;
    }

    public void setAlternativePenalty(final double alpha) {
        throw new UnsupportedOperationException("No alpha can be set when searching usign d-separation.");
    }

    public Graph getDag() {
        return this.dag;
    }
}



