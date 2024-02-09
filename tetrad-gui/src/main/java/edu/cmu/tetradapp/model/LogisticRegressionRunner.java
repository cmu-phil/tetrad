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
package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.Triple;
import edu.cmu.tetrad.regression.LogisticRegression;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the Regression algorithm.
 *
 * @author Frank Wimberly after Joe Ramsey's PcRunner
 * @version $Id: $Id
 */
public class LogisticRegressionRunner implements AlgorithmRunner, RegressionModel {

    private static final long serialVersionUID = 23L;
    private final Parameters params;
    private final List<String> variableNames;
    private String name;
    private String targetName;
    private List<String> regressorNames = new ArrayList<>();
    private List<DataSet> dataSets;
    private String report;
    private Graph outGraph;
    private LogisticRegression.Result result;
    private double alpha = 0.001;
    private int numModels = 1;
    private int modelIndex;
    private String modelSourceName;

    //=========================CONSTRUCTORS===============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must contain a DataSet that is either a DataSet
     * or a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public LogisticRegressionRunner(DataWrapper dataWrapper, Parameters params) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }

        if (params == null) {
            throw new NullPointerException();
        }

        if (dataWrapper instanceof Simulation) {
            Simulation simulation = (Simulation) dataWrapper;
            DataModelList dataModelList = dataWrapper.getDataModelList();
            dataSets = new ArrayList<>();

            for (DataModel dataModel : dataModelList) {
                dataSets.add((DataSet) dataModel);
            }

            numModels = dataModelList.size();
            modelIndex = 0;
            modelSourceName = simulation.getName();
        } else {
            DataModel dataModel = dataWrapper.getSelectedDataModel();

            if (!(dataModel instanceof DataSet)) {
                throw new IllegalArgumentException("Data set must be tabular.");
            }

            this.setDataSet((DataSet) dataModel);
        }

        this.params = params;

        variableNames = this.getDataModel().getVariableNames();
        targetName = null;
        regressorNames = new ArrayList<>();

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
     * @see TetradSerializableUtils
     * @return a {@link edu.cmu.tetradapp.model.LogisticRegressionRunner} object
     */
    public static LogisticRegressionRunner serializableInstance() {
        List<Node> variables = new LinkedList<>();
        ContinuousVariable var1 = new ContinuousVariable("X");
        ContinuousVariable var2 = new ContinuousVariable("Y");

        variables.add(var1);
        variables.add(var2);

        DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(3, variables.size()), variables);
        double[] col1data = {0.0, 1.0, 2.0};
        double[] col2data = {2.3, 4.3, 2.5};

        for (int i = 0; i < 3; i++) {
            dataSet.setDouble(i, 0, col1data[i]);
            dataSet.setDouble(i, 1, col2data[i]);
        }

        DataWrapper dataWrapper = new DataWrapper(dataSet);
        return new LogisticRegressionRunner(dataWrapper, new Parameters());
    }

    //===========================PUBLIC METHODS============================//
    /**
     * <p>getDataModel.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataModel} object
     */
    public DataModel getDataModel() {
        return dataSets.get(this.getModelIndex());
    }

    /**
     * <p>Getter for the field <code>alpha</code>.</p>
     *
     * @return the alpha or -1.0 if the params aren't set.
     */
    public double getAlpha() {
        return alpha;//this.params.getDouble("alpha", 0.001);
    }

    /**
     * <p>Setter for the field <code>alpha</code>.</p>
     *
     * @param alpha a double
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * <p>Getter for the field <code>result</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.regression.LogisticRegression.Result} object
     */
    public LogisticRegression.Result getResult() {
        return this.result;
    }

    /**
     * <p>Getter for the field <code>params</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Parameters getParams() {
        return this.params;
    }

    /**
     * <p>getResultGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getResultGraph() {
        return this.outGraph;
    }

    /**
     * <p>setResultGraph.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void setResultGraph(Graph graph) {
        this.outGraph = graph;
    }

    /**
     * <p>getSourceGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getSourceGraph() {
        return null;
    }

    //=================PUBLIC METHODS OVERRIDING ABSTRACT=================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be implemented in the extending class.
     */
    public void execute() {
        this.outGraph = new EdgeListGraph();

        if (this.regressorNames == null || this.regressorNames.isEmpty() || this.targetName == null) {
            this.report = "Response and predictor variables not set.";

            return;
        }

        if (this.regressorNames.contains(this.targetName)) {
            this.report = "Response must not be a predictor.";

            return;
        }

        DataSet regressorsDataSet = this.dataSets.get(getModelIndex()).copy();
        Node target = regressorsDataSet.getVariable(this.targetName);
        regressorsDataSet.removeColumn(target);

        List<String> names = regressorsDataSet.getVariableNames();

        //Get the list of regressors selected by the user
        List<Node> regressorNodes = new ArrayList<>();

        for (String s : this.regressorNames) {
            regressorNodes.add(this.dataSets.get(getModelIndex()).getVariable(s));
        }

        //If the user selected none, use them all
        if (this.regressorNames.size() > 0) {
            for (String name1 : names) {
                Node regressorVar = regressorsDataSet.getVariable(name1);
                if (!this.regressorNames.contains(regressorVar.getName())) {
                    regressorsDataSet.removeColumn(regressorVar);
                }
            }
        }

        int ncases = regressorsDataSet.getNumRows();
        int nvars = regressorsDataSet.getNumColumns();

        double[][] regressors = new double[nvars][ncases];

        for (int i = 0; i < nvars; i++) {
            for (int j = 0; j < ncases; j++) {
                regressors[i][j] = regressorsDataSet.getDouble(j, i);
            }
        }

        LogisticRegression logRegression = new LogisticRegression(this.dataSets.get(getModelIndex()));
        logRegression.setAlpha(this.alpha);

        this.result = logRegression.regress((DiscreteVariable) target, regressorNodes);
    }

    /**
     * <p>supportsKnowledge.</p>
     *
     * @return a boolean
     */
    public boolean supportsKnowledge() {
        return false;
    }

    /**
     * <p>getMeekRules.</p>
     *
     * @return a {@link edu.cmu.tetrad.search.utils.MeekRules} object
     */
    public MeekRules getMeekRules() {
        throw new UnsupportedOperationException();
    }

    /**
     * <p>getExternalGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getExternalGraph() {
        return null;
    }

    /** {@inheritDoc} */
    public void setExternalGraph(Graph graph) {
    }

    /** {@inheritDoc} */
    @Override
    public String getAlgorithmName() {
        return "Logistic-Regression";
    }

    /**
     * <p>Getter for the field <code>outGraph</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getOutGraph() {
        return this.outGraph;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getVariableNames() {
        return this.variableNames;
    }

    /** {@inheritDoc} */
    @Override
    public List<String> getRegressorNames() {
        return new ArrayList<>();
    }

    /** {@inheritDoc} */
    @Override
    public void setRegressorName(List<String> predictors) {
        this.regressorNames = predictors;
    }

    /**
     * <p>Getter for the field <code>targetName</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getTargetName() {
        return this.targetName;
    }

    /** {@inheritDoc} */
    @Override
    public void setTargetName(String target) {
        this.targetName = target;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /** {@inheritDoc} */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return this.outGraph;
    }

    /**
     * <p>getTriplesClassificationTypes.</p>
     *
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        return new LinkedList<>();
    }

    /** {@inheritDoc} */
    public List<List<Triple>> getTriplesLists(Node node) {
        return new LinkedList<>();
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, String> getParamSettings() {
        Map<String, String> paramSettings = new HashMap<>();
        paramSettings.put("Algorithm", "Regression");
        return paramSettings;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, String> getAllParamSettings() {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void setAllParamSettings(Map<String, String> paramSettings) {
//        Map<String, String> allParamsSettings = paramSettings;
    }

    /**
     * <p>Getter for the field <code>numModels</code>.</p>
     *
     * @return a int
     */
    public int getNumModels() {
        return this.numModels;
    }

    /**
     * <p>Getter for the field <code>modelIndex</code>.</p>
     *
     * @return a int
     */
    public int getModelIndex() {
        return this.modelIndex;
    }

    /**
     * <p>Setter for the field <code>modelIndex</code>.</p>
     *
     * @param modelIndex a int
     */
    public void setModelIndex(int modelIndex) {
        this.modelIndex = modelIndex;
    }

    /**
     * <p>Getter for the field <code>modelSourceName</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getModelSourceName() {
        return this.modelSourceName;
    }

    /**
     * <p>setDataSet.</p>
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public void setDataSet(DataSet dataSet) {
        this.dataSets = new ArrayList<>();
        this.dataSets.add(dataSet);
    }

    /** {@inheritDoc} */
    @Override
    public List<Graph> getGraphs() {
        return null;
    }
}
