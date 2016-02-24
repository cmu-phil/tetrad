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

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.*;

import static java.lang.Math.*;

/**
 * Calculates independence from pooled residuals.
 *
 * @author Joseph Ramsey
 */
public final class IndTestFisherZPercentIndependent implements IndependenceTest {
    private final List<Node> variables;
    private List<DataSet> dataSets;
    private double alpha;
    private double pValue = Double.NaN;
    private int[] rows;
    private List<TetradMatrix> data;
    private List<TetradMatrix> ncov;
    private Map<Node, Integer> variablesMap;
    private double percent = .75;
    private boolean fdr = true;
    private final ArrayList<RecursivePartialCorrelation> recursivePartialCorrelation;
    private boolean verbose = false;

    //==========================CONSTRUCTORS=============================//

    public IndTestFisherZPercentIndependent(List<DataSet> dataSets, double alpha) {
        this.dataSets = dataSets;
        this.variables = dataSets.get(0).getVariables();

        if (!(alpha >= 0 && alpha <= 1)) {
            throw new IllegalArgumentException("Alpha mut be in [0, 1]");
        }

        data = new ArrayList<TetradMatrix>();

        for (DataSet dataSet : dataSets) {
            dataSet = DataUtils.center(dataSet);
            TetradMatrix _data = dataSet.getDoubleData();
            data.add(_data);
        }

        ncov = new ArrayList<TetradMatrix>();
        for (TetradMatrix d : this.data) ncov.add(d.transpose().times(d).scalarMult(1.0 / d.rows()));

        setAlpha(alpha);
        rows = new int[dataSets.get(0).getNumRows()];
        for (int i = 0; i < getRows().length; i++) getRows()[i] = i;

        variablesMap = new HashMap<Node, Integer>();
        for (int i = 0; i < variables.size(); i++) {
            variablesMap.put(variables.get(i), i);
        }

        this.recursivePartialCorrelation = new ArrayList<RecursivePartialCorrelation>();
        for (TetradMatrix covMatrix : ncov) {
            recursivePartialCorrelation.add(new RecursivePartialCorrelation(getVariables(), covMatrix));
        }
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

        int sampleSize = data.get(0).rows();
        List<Double> pValues = new ArrayList<Double>();

        for (int m = 0; m < ncov.size(); m++) {
            TetradMatrix _ncov = ncov.get(m).getSelection(all, all);
            TetradMatrix inv = _ncov.inverse();
            double r = -inv.get(0, 1) / sqrt(inv.get(0, 0) * inv.get(1, 1));

            double fisherZ = sqrt(sampleSize - z.size() - 3.0) * 0.5 * (Math.log(1.0 + r) - Math.log(1.0 - r));
            double pValue;

            if (Double.isInfinite(fisherZ)) {
                pValue = 0;
            } else {
                pValue = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, abs(fisherZ)));
            }

            pValues.add(pValue);
        }

        double _cutoff = alpha;

        if (fdr) {
            _cutoff = StatUtils.fdrCutoff(alpha, pValues, false);
        }

        Collections.sort(pValues);
        int index = (int) round((1.0 - percent) * pValues.size());
        this.pValue = pValues.get(index);

//        if (this.pValue == 0) {
//            System.out.println("Zero pvalue "+ SearchLogUtils.independenceFactMsg(x, y, z, getScore()));
//        }

        boolean independent = this.pValue > _cutoff;

        if (verbose) {
            if (independent) {
                TetradLogger.getInstance().log("independencies",
                        SearchLogUtils.independenceFactMsg(x, y, z, getPValue()));
//            System.out.println(SearchLogUtils.independenceFactMsg(x, y, z, getScore()));
            } else {
                TetradLogger.getInstance().log("dependencies",
                        SearchLogUtils.dependenceFactMsg(x, y, z, getPValue()));
            }
        }

        return independent;
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
        return DataUtils.concatenate(dataSets);
    }

    public ICovarianceMatrix getCov() {
        List<DataSet> _dataSets = new ArrayList<DataSet>();

        for (DataSet d : dataSets) {
            _dataSets.add(DataUtils.standardizeData(d));
        }

        return new CovarianceMatrix(DataUtils.concatenate(dataSets));
    }

    @Override
    public List<DataSet> getDataSets() {
        return dataSets;
    }

    @Override
    public int getSampleSize() {
        return dataSets.get(0).getNumRows();
    }

    @Override
    public List<TetradMatrix> getCovMatrices() {
        return ncov;
    }

    @Override
    public double getScore() {
        return getPValue();
    }

    /**
     * @return a string representation of this test.
     */
    public String toString() {
        return "Fisher Z, Percent Independent Percent = " + round(pValue * 100);
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

    public void setFdr(boolean fdr) {
        this.fdr = fdr;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}


