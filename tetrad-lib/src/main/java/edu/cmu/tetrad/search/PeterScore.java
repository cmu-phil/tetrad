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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.log;

/**
 * Implements Chickering and Meek's (2002) locally consistent score criterion.
 *
 * @author Joseph Ramsey
 */
public class PeterScore implements Score {

    private final IndTestFisherZ test;
    private final DataSet dataSet;

    // The variables of the covariance matrix.
    private final List<Node> variables;

    // True if verbose output should be sent to out.
    private boolean verbose;
    private double penaltyDiscount = 1.0;

    /**
     * Constructs the score using a covariance matrix.
     */
    public PeterScore(final DataSet dataSet) {
        this.test = new IndTestFisherZ(dataSet, 0.001);
        this.dataSet = dataSet;

        this.variables = new ArrayList<>();

        for (final Node node : this.test.getVariables()) {
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
        this.test.isIndependent(this.variables.get(x), this.variables.get(y), getVariableList(z));
        final double p = this.test.getPValue();
        final int N = getSampleSize();
        return -log(p) - (2 + z.length) * log(N);
//        return 0.000001 - p;
    }

    @Override
    public double localScoreDiff(final int x, final int y) {
        return localScoreDiff(x, y, new int[0]);
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
        return true;
    }

    public DataSet getDataSet() {
        return this.dataSet;
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

    public int getSampleSize() {
        return this.dataSet.getNumRows();
    }

    public boolean getAlternativePenalty() {
        return false;
    }

    @Override
    public Node getVariable(final String targetName) {
        for (final Node node : this.variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    @Override
    public int getMaxDegree() {
        return 1000;
    }

    @Override
    public boolean determines(final List<Node> z, final Node y) {
        return false;
    }

    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    public void setPenaltyDiscount(final double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }
}



