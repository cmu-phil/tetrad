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

import edu.cmu.tetrad.data.CovarianceMatrixOnTheFly;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradMatrix;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements Chickering and Meek's (2002) locally consistent score criterion.
 *
 * @author Joseph Ramsey
 */
public class ArgesScore implements Score {

    private final IndTestFisherZ test;
    private final ICovarianceMatrix covMatrix;

    // The variables of the covariance matrix.
    private List<Node> variables;

    // True if verbose output should be sent to out.
    private boolean verbose = false;
    private double penaltyDiscount = 1.0;

    private Map<Node, Integer> indexMap;


    /**
     * Constructs the score using a covariance matrix.
     */
    public ArgesScore(DataSet dataSet) {
        this.test = new IndTestFisherZ(dataSet, 0.001);
        this.covMatrix = new CovarianceMatrixOnTheFly(dataSet);

        this.variables = new ArrayList<>();

        for (Node node : test.getVariables()) {
            if (node.getNodeType() == NodeType.MEASURED) {
                this.variables.add(node);
            }
        }

        this.indexMap = indexMap(variables);
    }

    private Map<Node, Integer> indexMap(List<Node> variables) {
        Map<Node, Integer> indexMap = new ConcurrentHashMap<>();

        for (int i = 0; i < variables.size(); i++) {
            indexMap.put(variables.get(i), i);
        }

        return indexMap;
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
        Node _x = variables.get(x);
        Node _y = variables.get(y);
        List<Node> _z = getVariableList(z);

        double r;

        try {
            r = partialCorrelation(_x, _y, _z);
        } catch (SingularMatrixException e) {
            System.out.println(SearchLogUtils.determinismDetected(_z, _x));
            return Double.NaN;
        }

        int N = test.getSampleSize();
        return -N * Math.log(1.0 - r * r) - getPenaltyDiscount() * 2.0 * Math.log(N);
    }

    @Override
    public double localScoreDiff(int x, int y) {
        return localScoreDiff(x, y, new int[0]);
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

    public boolean getAlternativePenalty() {
        return false;
    }

    @Override
    public Node getVariable(String targetName) {
        for (Node node : variables) {
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
    public boolean determines(List<Node> z, Node y) {
        return false;
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    private double partialCorrelation(Node x, Node y, List<Node> z) throws SingularMatrixException {
        if (z.isEmpty()) {
            double a = covMatrix.getValue(indexMap.get(x), indexMap.get(y));
            double b = covMatrix.getValue(indexMap.get(x), indexMap.get(x));
            double c = covMatrix.getValue(indexMap.get(y), indexMap.get(y));

            if (b * c == 0) throw new SingularMatrixException();

            return -a / Math.sqrt(b * c);
        } else {
            int[] indices = new int[z.size() + 2];
            indices[0] = indexMap.get(x);
            indices[1] = indexMap.get(y);
            for (int i = 0; i < z.size(); i++) indices[i + 2] = indexMap.get(z.get(i));
            TetradMatrix submatrix = covMatrix.getSubmatrix(indices).getMatrix();
            return StatUtils.partialCorrelation(submatrix);
        }
    }
}



