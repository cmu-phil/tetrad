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
public class GraphScore implements FgsScore {

    private final Graph dag;

    // The variables of the covariance matrix.
    private List<Node> variables;

    // True if verbose output should be sent to out.
    private boolean verbose = false;

    /**
     * Constructs the score using a covariance matrix.
     */
    public GraphScore(Graph dag) {
        this.dag = dag;

        this.variables = new ArrayList<>();

        for (Node node : dag.getNodes()) {
            if (node.getNodeType() == NodeType.MEASURED) {
                this.variables.add(node);
            }
        }
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(int i, int[] parents) {
        throw new UnsupportedOperationException();
    }

    private List<Node> getVariableList(int[] indices) {
        List<Node> variables = new ArrayList<>();
        for (int i : indices) {
            variables.add(this.variables.get(i));
        }
        return variables;
    }


    @Override
    public double localScoreDiff(int x, int y, int[] z) {
        return locallyConsistentScoringCriterion(x, y, z);
//        return aBetterScore(x, y, z);
    }

    private double locallyConsistentScoringCriterion(int x, int y, int[] z) {
        Node _y = variables.get(y);
        Node _x = variables.get(x);
        List<Node> _z = getVariableList(z);
        return dag.isDSeparatedFrom(_x, _y, _z) ? -1.0 : 1.0;
    }

    private double aBetterScore(int x, int y, int[] z) {
        Node _y = variables.get(y);
        Node _x = variables.get(x);
        List<Node> _z = getVariableList(z);
        boolean dsep = dag.isDSeparatedFrom(_x, _y, _z);
        int count = 0;

        if (!dsep) count++;

        for (Node z0 : _z) {
            if (dag.isDSeparatedFrom(_x, z0, _z)) {
                count += 1;
            }
        }

        double score = dsep ? -1 - count : 1 + count;

//        if (score == 1) score -= Math.tanh(z.length);
        return score;
    }

    private List<Node> minus(List<Node> z, Node z0) {
        List<Node> diff = new ArrayList<>(z);
        diff.remove(z0);
        return diff;
    }

    int[] append(int[] parents, int extra) {
        int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    /**
     * Specialized scoring method for a single parent. Used to speed up the effect edges search.
     */

    public double localScore(int i, int parent) {
        throw new UnsupportedOperationException();
    }

    /**
     * Specialized scoring method for no parents. Used to speed up the effect edges search.
     */
    public double localScore(int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEffectEdge(double bump) {
        return true;
    }

    public DataSet getDataSet() {
        throw new UnsupportedOperationException();
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public List<Node> getVariables() {
        return variables;
    }

    public int getSampleSize() {
        return 0;
    }

    @Override
    public boolean isDiscrete() {
        return false;
    }

    public double getParameter1() {
        throw new UnsupportedOperationException("No alpha can be set when searching usign d-separation.");
    }

    public void setParameter1(double alpha) {
        throw new UnsupportedOperationException("No alpha can be set when searching usign d-separation.");
    }
}



