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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.*;

import static java.lang.Math.*;

/**
 * Calculates independence from pooled residuals.
 *
 * @author Joseph Ramsey
 */
public final class IndTestFisherZFisherPValue implements IndependenceTest {
    private final List<Node> variables;
    private final int sampleSize;
    private List<DataSet> dataSets;
    private double alpha;
    private double pValue = Double.NaN;
    private int[] rows;
    //    private List<TetradMatrix> data;
    private List<ICovarianceMatrix> ncov;
    private Map<Node, Integer> variablesMap;
    private double percent = .5;
    private List<DataSet> allLagged;

    private List<IndependenceTest> tests = new ArrayList<IndependenceTest>();
    private boolean verbose = false;

    //==========================CONSTRUCTORS=============================//

    public IndTestFisherZFisherPValue(List<DataSet> dataSets, double alpha) {

        this.sampleSize = dataSets.get(0).getNumRows();
        setAlpha(alpha);
        ncov = new ArrayList<ICovarianceMatrix>();
        allLagged = new ArrayList<DataSet>();

        for (DataSet dataSet : dataSets) {
//            dataSet = DataUtils.center(dataSet);
//            TetradMatrix d = dataSet.getDoubleData();
            ncov.add(new CovarianceMatrixOnTheFly(dataSet));
//            allLagged.add(d);
        }

        rows = new int[dataSets.get(0).getNumRows()];
        for (int i = 0; i < getRows().length; i++) getRows()[i] = i;

        this.variables = dataSets.get(0).getVariables();
        variablesMap = new HashMap<Node, Integer>();
        for (int i = 0; i < variables.size(); i++) {
            variablesMap.put(variables.get(i), i);
        }

        for (DataSet dataSet : dataSets) {
            this.tests.add(new IndTestFisherZ(dataSet, alpha));
        }

        this.dataSets = dataSets;
    }

    //==========================PUBLIC METHODS=============================//

    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    public boolean isIndependent(Node x, Node y, List<Node> z) {
        int[] all = new int[z.size() + 2];
        all[0] = variablesMap.get(x);
        all[1] = variablesMap.get(y);
        for (int i = 0; i < z.size(); i++) {
            all[i + 2] = variablesMap.get(z.get(i));
        }

        List<Double> pValues = new ArrayList<Double>();

        for (int m = 0; m < ncov.size(); m++) {
            TetradMatrix _ncov = ncov.get(m).getSelection(all, all);
            TetradMatrix inv = _ncov.inverse();
            double r = -inv.get(0, 1) / sqrt(inv.get(0, 0) * inv.get(1, 1));
//            r *= 0.6;
            double _z = sqrt(sampleSize - z.size() - 3.0) * 0.5 * (log(1.0 + r) - log(1.0 - r));
            double pvalue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, Math.abs(_z)));
            pValues.add(pvalue);
        }

        Collections.sort(pValues);
//        int numPValues = (int) floor(percent * pValues.size());
        int n = 0;
        double tf = 0.0;

//        for (int i = pValues.size() - numPValues; i < pValues.size(); i++) {
//            double p = pValues.get(i);
//            if (p == 0.) continue;
//            tf += -2.0 * log(p);
//            n++;
//        }

        int numZeros = 0;

        for (int i = 0; i < pValues.size(); i++) {
            double p = pValues.get(i);
            if (p == 0) {
                numZeros++;
                continue;
            }
            tf += -2.0 * log(p);
            n++;
        }

        if (numZeros >= pValues.size() / 2) return false;

        if (tf == 0) throw new IllegalArgumentException(
                "For the Fisher method, all component p values in the calculation may not be zero, " +
                        "\nsince not all p values can be ignored. Maybe try calculating AR residuals.");
        double p = 1.0 - ProbUtils.chisqCdf(tf, 2 * n);
        this.pValue = p;

        boolean independent = p > alpha;

        if (verbose) {
            if (independent) {
                TetradLogger.getInstance().log("independencies",
                        SearchLogUtils.independenceFactMsg(x, y, z, getPValue()));
                System.out.println(SearchLogUtils.independenceFactMsg(x, y, z, getPValue()));
            } else {
                TetradLogger.getInstance().log("dependencies",
                        SearchLogUtils.dependenceFactMsg(x, y, z, getPValue()));
            }
        }

        return independent;
    }

    private static List<Double> getAvailablePValues(List<IndependenceTest> independenceTests, Node x, Node y, List<Node> condSet) {
        List<Double> allPValues = new ArrayList<Double>();

        for (IndependenceTest test : independenceTests) {
//            if (missingVariable(x, y, condSet, test)) continue;
            List<Node> localCondSet = new ArrayList<Node>();
            for (Node node : condSet) {
                localCondSet.add(test.getVariable(node.getName()));
            }

            try {
                test.isIndependent(test.getVariable(x.getName()), test.getVariable(y.getName()), localCondSet);
                allPValues.add(test.getPValue());
            } catch (Exception e) {
                // Skip that test.
            }
        }

        return allPValues;
    }

    public boolean isIndependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return isIndependent(x, y, zList);
    }

    public boolean isDependent(Node x, Node y, List<Node> z) {
        return !isIndependent(x, y, z);
    }

    public boolean isDependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return isDependent(x, y, zList);
    }

    /**
     * @return the probability associated with the most recently computed independence test.
     */
    public double getPValue() {
        return this.pValue;
    }

    /**
     * Sets the significance level at which independence judgments should be made.  Affects the cutoff for partial
     * correlations to be considered statistically equal to zero.
     */
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range.");
        }

        this.alpha = alpha;
    }

    /**
     * Gets the getModel significance level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * @return the list of variables over which this independence checker is capable of determinine independence
     * relations-- that is, all the variables in the given graph or the given data set.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * @return the variable with the given name.
     */
    public Node getVariable(String name) {
        for (int i = 0; i < getVariables().size(); i++) {
            Node variable = getVariables().get(i);
            if (variable.getName().equals(name)) {
                return variable;
            }
        }

        return null;
    }

    /**
     * @return the list of variable varNames.
     */
    public List<String> getVariableNames() {
        List<Node> variables = getVariables();
        List<String> variableNames = new ArrayList<String>();
        for (Node variable1 : variables) {
            variableNames.add(variable1.getName());
        }
        return variableNames;
    }

    /**
     * @throws UnsupportedOperationException
     */
    public boolean determines(List z, Node x) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * @throws UnsupportedOperationException
     */
    public DataSet getData() {
        return (DataSet) tests.get(0).getData();
    }

    public ICovarianceMatrix getCov() {
        List<DataSet> _dataSets = new ArrayList<DataSet>();

        for (DataSet d : dataSets) {
            _dataSets.add(DataUtils.standardizeData(d));
        }

        return new CovarianceMatrix(DataUtils.concatenate(_dataSets));
    }

    @Override
    public List<DataSet> getDataSets() {
        return null;
    }

    @Override
    public int getSampleSize() {
        return 0;
    }

    @Override
    public List<TetradMatrix> getCovMatrices() {
        return null;
    }

    @Override
    public double getScore() {
        return getPValue();
    }

    /**
     * @return a string representation of this test.
     */
    public String toString() {
        return "Fisher Z, Fisher P Value Percent = " + round(percent * 100);
    }

    public int[] getRows() {
        return rows;
    }

    public double getPercent() {
        return percent;
    }

    public void setPercent(double percent) {
        if (percent < 0.0 || percent > 1.0) throw new IllegalArgumentException();
        this.percent = percent;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}


