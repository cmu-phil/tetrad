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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.regression.LogisticRegression;
import edu.cmu.tetrad.search.ImpliedOrientation;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the Regression
 * algorithm.
 *
 * @author Frank Wimberly after Joe Ramsey's PcRunner
 */
public class LogisticRegressionRunner implements AlgorithmRunner {
    static final long serialVersionUID = 23L;

    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * @serial Cannot be null.
     */
    private LogisticRegressionParams params;

    /**
     * @serial Cannot be null.
     */
    private String targetName;

    /**
     * @serial Cannot be null.
     */
    private DataSet dataSet;

    /**
     * @serial Can be null.
     */
    private String report;

    /**
     * @serial Can be null.
     */
    private Graph outGraph;


    /**
     *@serial Can be null.
     */
    private LogisticRegression.Result result;


    private double[] coefficients;

    //=========================CONSTRUCTORS===============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public LogisticRegressionRunner(DataWrapper dataWrapper,
            LogisticRegressionParams params) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }

        if (params == null) {
            throw new NullPointerException();
        }

        DataModel dataModel = dataWrapper.getSelectedDataModel();

        if (!(dataModel instanceof DataSet)) {
            throw new IllegalArgumentException("Data set must be tabular.");
        }

        DataSet dataSet = (DataSet) dataModel;

        this.params = params;
        this.targetName = params.getTargetName();
        this.dataSet = dataSet;

        TetradLogger.getInstance().log("info", "Linear Regression");

        if (result == null) {
            TetradLogger.getInstance().log("info", "Please double click this regression node to run the regession.");
        } else {
            TetradLogger.getInstance().log("result", report);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static LogisticRegressionRunner serializableInstance() {
        List<Node> variables = new LinkedList<Node>();
        ContinuousVariable var1 = new ContinuousVariable("X");
        ContinuousVariable var2 = new ContinuousVariable("Y");

        variables.add(var1);
        variables.add(var2);

        DataSet dataSet = new ColtDataSet(3, variables);
        double[] col1data = new double[]{0.0, 1.0, 2.0};
        double[] col2data = new double[]{2.3, 4.3, 2.5};

        for (int i = 0; i < 3; i++) {
            dataSet.setDouble(i, 0, col1data[i]);
            dataSet.setDouble(i, 1, col2data[i]);
        }

        DataWrapper dataWrapper = new DataWrapper(dataSet);
        return new LogisticRegressionRunner(dataWrapper,
                LogisticRegressionParams.serializableInstance());
    }

    //===========================PUBLIC METHODS============================//

    public DataModel getDataModel() {
        return this.dataSet;
    }

    public void setParams(LogisticRegressionParams params) {
        this.params = params;
    }


    /**
     * @return the alpha or -1.0 if the params aren't set.
     */
    public double getAlpha(){
        if(this.params != null){
            return this.params.getAlpha();
        }
        return -1.0;
    }


    public LogisticRegression.Result getResult(){
        return this.result;
    }

    public SearchParams getParams() {
        return params;
    }

    public Graph getResultGraph() {
        return outGraph;
    }

    public void setResultGraph(Graph graph) {
        this.outGraph = graph;
    }

    public Graph getSourceGraph() {
        return null;
    }

    //=================PUBLIC METHODS OVERRIDING ABSTRACT=================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
    public void execute() {

        if (params.getRegressorNames().length == 0 ||
                params.getTargetName() == null) {
            report = "Response and predictor variables not set.";
            outGraph = new EdgeListGraph();
            return;
        }

        if (Arrays.asList(params.getRegressorNames()).contains(
                params.getTargetName())) {
            report = "Response ar must not be a predictor.";
            outGraph = new EdgeListGraph();
            return;
        }

        //Regression regression = new Regression();
        //String targetName = ((RegressionParams) getParams()).getTargetName();
        String targetName = params.getTargetName();
        double alpha = params.getAlpha();

        DataSet regressorsDataSet = dataSet.copy();
        Node target = regressorsDataSet.getVariable(targetName);
        int targetIndex = dataSet.getVariables().indexOf(target);
        regressorsDataSet.removeColumn(target);

        Object[] namesObj = (regressorsDataSet.getVariableNames()).toArray();
        String[] names = new String[namesObj.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = (String) namesObj[i];
        }

        //Get the list of regressors selected by the user
        String[] regressorNames = params.getRegressorNames();
        List regressorNamesList = Arrays.asList(regressorNames);

        List<Node> regressorNodes = new ArrayList<Node>();

        for (String s : regressorNames) {
            regressorNodes.add(dataSet.getVariable(s));
        }

        //If the user selected none, use them all
        if (regressorNames.length > 0) {
            for (String name1 : names) {
                Node regressorVar = regressorsDataSet.getVariable(name1);
                if (!regressorNamesList.contains(regressorVar.getName())) {
                    regressorsDataSet.removeColumn(regressorVar);
                }
            }
        }
        else {
            regressorNames = names;  //All names except the targetColumn
        }

        //double[][] regressorsT = regressorsDataSet.getDoubleData();
        //int ncases = regressorsT.length;
        //int nvars = regressorsT[0].length;

        int ncases = regressorsDataSet.getNumRows();
        int nvars = regressorsDataSet.getNumColumns();

        double[][] regressors = new double[nvars][ncases];

        for (int i = 0; i < nvars; i++) {
            for (int j = 0; j < ncases; j++) {
                //regressors[i][j] = regressorsT[j][i];
                regressors[i][j] = regressorsDataSet.getDouble(j, i);
            }
        }

        //targetColumn is the array storing the values of the targetColumn targetColumn
        int[] targetColumn = new int[ncases];

        for (int j = 0; j < ncases; j++) {
            targetColumn[j] = (int) dataSet.getDouble(j, targetIndex);
        }

        LogisticRegression logRegression = new LogisticRegression(dataSet);
        logRegression.setAlpha(alpha);

        LogisticRegression.Result result = logRegression.regress((DiscreteVariable) target, regressorNodes);
//        this.report = logRegression.getReport();
        this.result = result;
        coefficients = result.getCoefs();
//        outGraph = logRegression.getOutGraph();
    }

    public boolean supportsKnowledge() {
        return false;
    }

    public ImpliedOrientation getMeekRules() {
        throw new UnsupportedOperationException();
    }

    public void setInitialGraph(Graph graph) {
        return;
    }

    public Graph getInitialGraph() {
        return null;
    }

    public String getReport() {
        return report;
    }

    public double[] getCoefficients() {
        return coefficients;
    }

    public Graph getOutGraph() {
        return outGraph;
    }

    public String getTargetName() {
        return targetName;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (params == null) {
            throw new NullPointerException();
        }

        /*
        if (targetName == null) {
            throw new NullPointerException();
        }
        */

        if (dataSet == null) {
            throw new NullPointerException();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Graph getGraph() {
        return outGraph;
    }

    /**
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        return new LinkedList<String>();
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>
     * for the given node.
     * @param node The node that the classifications are for. All triple from adjacencies to this
     * node to adjacencies to this node through the given node will be considered.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        return new LinkedList<List<Triple>>();
    }

}






