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

import com.mathworks.toolbox.javabuilder.MWApplication;
import com.mathworks.toolbox.javabuilder.MWException;
import com.mathworks.toolbox.javabuilder.MWNumericArray;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradVector;
import org.rosuda.JRI.REXP;
import org.rosuda.JRI.RVector;
import org.rosuda.JRI.Rengine;

import java.text.NumberFormat;
import java.util.*;

/**
 * Checks conditional independence of variable in a continuous data set using a conditional correlation test
 * for the nonlinear nonGaussian case.
 *
 * @author Joseph Ramsey
 */
public final class IndTestRcitJRI implements IndependenceTest {

    private final double[][] _data;
    /**
     * The variables of the covariance data, in order. (Unmodifiable list.)
     */
    private List<Node> variables;

    /**
     * The significance level of the independence tests.
     */
    private double alpha;

    /**
     * Formats as 0.0000.
     */
    private static NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    /**
     * Stores a reference to the dataset being analyzed.
     */
    private DataSet dataSet;
    private TetradMatrix data;
    private Map<Node, Integer> nodeMap;
    private int numTests;
    private boolean verbose = false;
    private static Rengine r;

    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new Independence test which checks independence facts based on the correlation data implied by the
     * given data set (must be continuous). The given significance level is used.
     *
     * @param dataSet A data set containing only continuous columns.
     * @param alpha   The alpha level of the test.
     */
    public IndTestRcitJRI(DataSet dataSet, double alpha) {
        if (!(dataSet.isContinuous())) {
            throw new IllegalArgumentException("Data set must be continuous.");
        }

        List<Node> nodes = dataSet.getVariables();

        this.variables = Collections.unmodifiableList(nodes);
        setAlpha(alpha);

        this.dataSet = dataSet;
        data = this.dataSet.getDoubleData();

        _data = data.transpose().toArray();

        nodeMap = new HashMap<>();

        for (int i = 0; i < nodes.size(); i++) {
            nodeMap.put(nodes.get(i), i);
        }

        numTests = 0;

        System.out.println("Testing R code in Java");

        System.out.println(System.getProperty("java.library.path"));
        System.out.println(System.getProperty("R_HOME"));

        // just making sure we have the right version of everything
        if (!Rengine.versionCheck()) {
            System.err.println("** Version mismatch - Java files don't match library version.");
            System.exit(1);
        }
        System.out.println("Creating Rengine (with arguments)");
        // 1) we pass the arguments from the command line
        // 2) we won't use the main loop at first, we'll start it later
        //    (that's the "false" as second argument)
        // 3) the callbacks are implemented by the TextConsole class above

        if (r == null) {

            r = new Rengine(new String[]{"--save"}, false, new TextConsole());
            System.out.println("Rengine created, waiting for R");
            // the engine creates R is a new thread, so we should wait until it's ready
            if (!r.waitForR()) {
                throw new IllegalStateException("Cannot load R");
            }

            r.eval("library(MASS)");
            r.eval("library(momentchi2)");
            r.eval("library(devtools");
            r.eval("libriary(devtools");
//        r.eval("install_github(\"ericstrobl/RCIT\")");
            r.eval("library(RCIT)");

        }

    }

    //==========================PUBLIC METHODS=============================//

    /**
     * Creates a new IndTestCramerT instance for a subset of the variables.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    public boolean isIndependent(Node x, Node y, List<Node> z) {

        r.assign("x", _data[nodeMap.get(x)]);
        r.assign("y", _data[nodeMap.get(y)]);

        r.eval("z<-NULL");

        for (int s = 0; s < z.size(); s++) {
            double[] col = _data[nodeMap.get(z.get(s))];

            IndTestRcitJRI.r.assign("z0", col);
            IndTestRcitJRI.r.eval("if (is.null(z)) {z <- rbind(z0)} else {z<-rbind(z, z0)}");
        }

        r.eval("if (!is.null(z)) z = t(z)");

        double p = r.eval("RCIT(x,y,z,approx=\"lpd4\")$p").asDouble();
        return p > alpha;
    }

    public boolean isIndependent(Node x, Node y, Node... z) {
        return isIndependent(x, y, Arrays.asList(z));
    }

    public boolean isDependent(Node x, Node y, List<Node> z) {
        boolean independent = isIndependent(x, y, z);

        if (verbose) {
            if (independent) {
                TetradLogger.getInstance().log("independencies",
                        SearchLogUtils.independenceFactMsg(x, y, z, getPValue()));
            } else {
                TetradLogger.getInstance().log("dependencies",
                        SearchLogUtils.dependenceFactMsg(x, y, z, getPValue()));
            }
        }

        return !independent;
    }

    public boolean isDependent(Node x, Node y, Node... z) {
        List<Node> zList = Arrays.asList(z);
        return isDependent(x, y, zList);
    }

    /**
     * @return the probability associated with the most recently computed independence test.
     */
    public double getPValue() {
        return 0.0;
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
        for (Node node : variables) {
            if (node.getName().equals(name)) {
                return node;
            }
        }

        throw new IllegalArgumentException("Node a node in this test.");
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

    /**
     * If <code>isDeterminismAllowed()</code>, deters to IndTestFisherZD; otherwise throws
     * UnsupportedOperationException.
     */
    public boolean determines(List<Node> z, Node x) throws UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the data set being analyzed.
     */
    public DataSet getData() {
        return dataSet;
    }

    @Override
    public ICovarianceMatrix getCov() {
        return null;
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
        return "Conditional Correlation, alpha = " + nf.format(getAlpha());
    }

    //==================================PRIVATE METHODS================================

    private double[][] columns(double[][] data, List<Node> z) {
        double[][] cols = new double[z.size()][];
        for (int i = 0; i < z.size(); i++) {
            cols[i] = data[nodeMap.get(z.get(i))];
        }
        return cols;
    }

    private Number[][] getNumbers(double[][] d) {
        final Number[][] d2 = new Number[d.length][d[0].length];

        for (int i = 0; i < d.length; i++) {
            for (int j = 0; j < d[0].length; j++) {
                d2[i][j] = d[i][j];
            }
        }

        return d2;
    }

    public int getNumTests() {
        return numTests;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}




