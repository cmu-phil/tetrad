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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.commons.math3.util.FastMath.min;
import static org.apache.commons.math3.util.FastMath.sqrt;

/**
 * Checks conditional independence of variable in a continuous data set using Fisher's Z test. See Spirtes, Glymour, and
 * Scheines, "Causation, Prediction and Search," 2nd edition, page 94.
 *
 * @author Joseph Ramsey
 * @author Frank Wimberly adapted IndTestCramerT for Fisher's Z
 */
public final class IndTestCodec implements IndependenceTest {

    private double alpha = 0;
    private final List<Node> variables;
    private final DataSet dataSet;
    private final double[][] data;
    private final boolean verbose = true;


    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new Independence test which checks independence facts based on the correlation matrix implied by the
     * given data set (must be continuous). The given significance level is used.
     *
     * @param dataSet A data set containing only continuous columns.
     */
    public IndTestCodec(DataSet dataSet, double alpha) {
        if (!(dataSet.isContinuous())) {
            throw new IllegalArgumentException("Data set must be continuous.");
        }

        this.dataSet = dataSet;
        this.data = dataSet.getDoubleData().transpose().toArray();
        this.alpha = alpha;

        this.variables = dataSet.getVariables();

        Map<Node, Integer> nodesHash = new HashMap<>();

        for (int i = 0; i < variables.size(); i++) {
            nodesHash.put(variables.get(i), i);
        }
    }

    //==========================PUBLIC METHODS=============================//

    /**
     * Creates a new independence test instance for a subset of the variables.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Determines whether variable x is independent of variable y given a list of conditioning variables z.
     *
     * @param x the one variable being compared.
     * @param y the second variable being compared.
     * @param z the list of conditioning variables.
     * @return true iff x _||_ y | z.
     * @throws RuntimeException if a matrix singularity is encountered.
     */
    public IndependenceResult checkIndependence(Node y, Node z, List<Node> x) {
        IndependenceFact fact = new IndependenceFact(y, z, x);

        int N = data[0].length;

        List<Integer> X = new ArrayList<>();
        for (Node node : x) X.add(indexOf(node));

        List<Integer> XZ = new ArrayList<>();
        XZ.add(indexOf(z));
        XZ.addAll(X);

        int[] R = new int[N];
        int _y = indexOf(y);
        double[] ydata = data[_y];

        for (int j = 0; j < N; j++) {
            int count = 0;

            for (int i = 0; i < N; i++) {
                if (ydata[i] < ydata[j]) count++;
            }

            R[j] = count;
        }


//        for (int j = 0; j < N; j++) {
//            double Ndistance = Double.POSITIVE_INFINITY;
//            int Nj = 0;
//
//            for (int i = 0; i < N; i++) {
//                if (i == j) continue;
//                double d = distance(data, X, i, j);
//                if (d < Ndistance) {
//                    Ndistance = d;
//                    Nj = i;
//                }
//            }
//        }
//
//        for (int j = 0; j < N; j++) {
//            double Mdistance = Double.POSITIVE_INFINITY;
//            int Mj = 0;
//
//            for (int i = 0; i < N; i++) {
//                if (i == j) continue;
//                double d = distance(data, XZ, i, j);
//                if (d < Mdistance) {
//                    Mdistance = d;
//                    Mj = i;
//                }
//            }
//        }

        double num = 0;
        double den = 0;

        for (int j = 0; j < N; j++) {
            double Ndistance = Double.POSITIVE_INFINITY;
            int Nj = 0;

            for (int i = 0; i < N; i++) {
                if (i == j) continue;
                double d = distance(data, X, i, j);
                if (d < Ndistance) {
                    Ndistance = d;
                    Nj = i;
                }
            }

            double Mdistance = Double.POSITIVE_INFINITY;
            int Mj = 0;

            for (int i = 0; i < N; i++) {
                if (i == j) continue;
                double d = distance(data, XZ, i, j);
                if (d < Mdistance) {
                    Mdistance = d;
                    Mj = i;
                }
            }

            num += min(R[j], R[Mj]) - min(R[j], R[Nj]);
            den += R[j] - min(R[j], R[Nj]);
        }

        double t = num / den;
//        t = abs(t);
//        System.out.println(t);

        if (t < 0) return new IndependenceResult(fact, true, t);
        if (t > 1) return new IndependenceResult(fact, false, t);


        return new IndependenceResult(fact, t <= alpha, t);
    }

    private int indexOf(Node node) {
        for (int i = 0; i < variables.size(); i++)
            if (variables.get(i).getName().equals(node.getName())) return i;
        return -1;
    }

    @Override
    public List<Node> getVariables() {
        return variables;
    }

    @Override
    public boolean determines(List<Node> z, Node y) {
        return false;
    }

    @Override
    public double getAlpha() {
        return 0;
    }

    @Override
    public void setAlpha(double alpha) {

    }

    @Override
    public DataModel getData() {
        return dataSet;
    }


    @Override
    public double getScore() {
        return 0;
    }

    @Override
    public void setVerbose(boolean verbose) {

    }

    @Override
    public boolean isVerbose() {
        return false;
    }

    private double distance(double[][] data, List<Integer> cols, int i, int j) {
        double sum = 0.0;

        for (int col : cols) {
            double d = (data[col][i] - data[col][j]) / 2;

            if (!Double.isNaN(d)) {
                sum += d * d;
            }
        }

        return sqrt(sum);
    }

    public String toString() {
        return "Fisher Z, alpha = " + new DecimalFormat("0.0###").format(getAlpha());
    }
}




