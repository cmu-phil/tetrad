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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;

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
    private final List<DataSet> dataSets;
    private double alpha;
    private double pValue = Double.NaN;
    private final int[] rows;
    //    private List<TetradMatrix> data;
    private final List<ICovarianceMatrix> ncov;
    private final Map<Node, Integer> variablesMap;
    private double percent = .5;

    private final List<IndependenceTest> tests = new ArrayList<>();
    private boolean verbose;

    //==========================CONSTRUCTORS=============================//

    public IndTestFisherZFisherPValue(List<DataSet> dataSets, double alpha) {

        this.sampleSize = dataSets.get(0).getNumRows();
        setAlpha(alpha);
        this.ncov = new ArrayList<>();

        for (DataSet dataSet : dataSets) {
            this.ncov.add(new CovarianceMatrix(dataSet));
        }

        this.rows = new int[dataSets.get(0).getNumRows()];
        for (int i = 0; i < getRows().length; i++) getRows()[i] = i;

        this.variables = dataSets.get(0).getVariables();
        this.variablesMap = new HashMap<>();
        for (int i = 0; i < this.variables.size(); i++) {
            this.variablesMap.put(this.variables.get(i), i);
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

    public IndependenceResult checkIndependence(Node x, Node y, List<Node> z) {
        int[] all = new int[z.size() + 2];
        all[0] = this.variablesMap.get(x);
        all[1] = this.variablesMap.get(y);
        for (int i = 0; i < z.size(); i++) {
            all[i + 2] = this.variablesMap.get(z.get(i));
        }

        List<Double> pValues = new ArrayList<>();

        for (ICovarianceMatrix iCovarianceMatrix : this.ncov) {
            Matrix _ncov = iCovarianceMatrix.getSelection(all, all);
            Matrix inv = _ncov.inverse();
            double r = -inv.get(0, 1) / sqrt(inv.get(0, 0) * inv.get(1, 1));
//            r *= 0.6;
            double _z = sqrt(this.sampleSize - z.size() - 3.0) * 0.5 * (log(1.0 + r) - log(1.0 - r));
            double pvalue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(_z)));
            pValues.add(pvalue);
        }

        Collections.sort(pValues);
        int n = 0;
        double tf = 0.0;

        int numZeros = 0;

        for (double p : pValues) {
            if (p == 0) {
                numZeros++;
                continue;
            }
            tf += -2.0 * log(p);
            n++;
        }

        if (numZeros >= pValues.size() / 2)
            return new IndependenceResult(new IndependenceFact(x, y, z).toString(), true, Double.NaN);

        if (tf == 0) throw new IllegalArgumentException(
                "For the Fisher method, all component p values in the calculation may not be zero, " +
                        "\nsince not all p values can be ignored. Maybe try calculating AR residuals.");
        double p = 1.0 - ProbUtils.chisqCdf(tf, 2 * n);
        this.pValue = p;

        boolean independent = p > this.alpha;

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().forceLogMessage(
                        SearchLogUtils.independenceFactMsg(x, y, z, this.pValue));
            }
        }


        return new IndependenceResult(new IndependenceFact(x, y, z).toString(), independent, p);
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
        List<String> variableNames = new ArrayList<>();
        for (Node variable1 : variables) {
            variableNames.add(variable1.getName());
        }
        return variableNames;
    }

    public boolean determines(List<Node> z, Node x) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    public DataSet getData() {
        return (DataSet) this.tests.get(0).getData();
    }

    public ICovarianceMatrix getCov() {
        List<DataSet> _dataSets = new ArrayList<>();

        for (DataSet d : this.dataSets) {
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
    public List<Matrix> getCovMatrices() {
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
        return "Fisher Z, Fisher P Value Percent = " + round(this.percent * 100);
    }

    public int[] getRows() {
        return this.rows;
    }

    public double getPercent() {
        return this.percent;
    }

    public void setPercent(double percent) {
        if (percent < 0.0 || percent > 1.0) throw new IllegalArgumentException();
        this.percent = percent;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}


