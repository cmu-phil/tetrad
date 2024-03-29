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
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionCovariance;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.*;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the Regression algorithm.
 *
 * @author Frank Wimberly after Joe Ramsey's PcRunner
 * @version $Id: $Id
 */
public class RegressionRunner implements AlgorithmRunner, RegressionModel {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The parameters for the algorithm.
     */
    private final Parameters params;

    /**
     * The data models to be used in the regression.
     */
    private final DataModelList dataModels;

    /**
     * The names of the variables in the data model.
     */
    private final List<String> variableNames;

    /**
     * The names of the regressors in the data model.
     */
    private List<String> regressorNames;

    /**
     * The name of the target variable in the data model.
     */
    private String name;

    /**
     * The name of the target variable in the data model.
     */
    private String targetName;

    /**
     * The result of the regression.
     */
    private Graph outGraph;

    /**
     * The result of the regression.
     */
    private RegressionResult result;

    /**
     * The name of the source of the model.
     */
    private Map<String, String> allParamsSettings;

    /**
     * The name of the source of the model.
     */
    private int numModels = 1;

    /**
     * The name of the source of the model.
     */
    private int modelIndex;

    /**
     * The name of the source of the model.
     */
    private String modelSourceName;

    //=========================CONSTRUCTORS===============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must contain a DataSet that is either a DataSet
     * or a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param params      a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RegressionRunner(DataWrapper dataWrapper, Parameters params) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }

        if (params == null) {
            throw new NullPointerException();
        }

        if (dataWrapper instanceof Simulation simulation) {
            this.numModels = dataWrapper.getDataModelList().size();
            this.modelIndex = 0;
            this.modelSourceName = simulation.getName();
        }

        this.params = params;

        DataModel dataModel = dataWrapper.getSelectedDataModel();

        if (dataModel instanceof DataSet _dataSet) {
            if (!_dataSet.isContinuous()) {
                throw new IllegalArgumentException("Data set must be continuous.");
            }
        }

        this.dataModels = dataWrapper.getDataModelList();

        this.variableNames = dataModel.getVariableNames();
        this.targetName = null;
        this.regressorNames = new ArrayList<>();

        TetradLogger.getInstance().forceLogMessage("Linear Regression");

        if (this.result == null) {
            TetradLogger.getInstance().forceLogMessage("Please double click this regression node to run the regession.");
        } else {
            String message = "\n" + this.result.getResultsTable().toString();
            TetradLogger.getInstance().forceLogMessage(message);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.RegressionRunner} object
     * @see TetradSerializableUtils
     */
    public static RegressionRunner serializableInstance() {
        List<Node> variables = new LinkedList<>();
        ContinuousVariable var1 = new ContinuousVariable("X");
        ContinuousVariable var2 = new ContinuousVariable("Y");

        variables.add(var1);
        variables.add(var2);
        DataSet _dataSet = new BoxDataSet(new DoubleDataBox(3, variables.size()), variables);
        double[] col1data = {0.0, 1.0, 2.0};
        double[] col2data = {2.3, 4.3, 2.5};

        for (int i = 0; i < 3; i++) {
            _dataSet.setDouble(i, 0, col1data[i]);
            _dataSet.setDouble(i, 1, col2data[i]);
        }

        DataWrapper dataWrapper = new DataWrapper(_dataSet);
        return new RegressionRunner(dataWrapper, new Parameters());
    }

    //===========================PUBLIC METHODS============================//

    /**
     * <p>getDataModel.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataModel} object
     */
    public DataModel getDataModel() {
        //return (DataModel) this.dataWrapper.getDataModelList().get(0);
        return this.dataModels.get(getModelIndex());
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

    private void setResultGraph(Graph graph) {
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
        if (this.regressorNames.size() == 0 || this.targetName == null) {
            this.outGraph = new EdgeListGraph();
            return;
        }

        if (this.regressorNames.contains(this.targetName)) {
            this.outGraph = new EdgeListGraph();
            return;
        }

        Regression regression;
        Node target;
        List<Node> regressors;

        if (getDataModel() instanceof DataSet _dataSet) {
            regression = new RegressionDataset(_dataSet);
            target = _dataSet.getVariable(this.targetName);
            regressors = new LinkedList<>();

            for (String regressorName : this.regressorNames) {
                regressors.add(_dataSet.getVariable(regressorName));
            }

            double alpha = this.params.getDouble("alpha", 0.001);
            regression.setAlpha(alpha);

            this.result = regression.regress(target, regressors);
            this.outGraph = regression.getGraph();
        } else if (getDataModel() instanceof ICovarianceMatrix covariances) {
            regression = new RegressionCovariance(covariances);
            target = covariances.getVariable(this.targetName);
            regressors = new LinkedList<>();

            for (String regressorName : this.regressorNames) {
                regressors.add(covariances.getVariable(regressorName));
            }

            double alpha = this.params.getDouble("alpha", 0.001);
            regression.setAlpha(alpha);

            this.result = regression.regress(target, regressors);
            this.outGraph = regression.getGraph();
        }

        setResultGraph(this.outGraph);
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

    /**
     * {@inheritDoc}
     */
    public void setExternalGraph(Graph graph) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAlgorithmName() {
        return "Regression";
    }

    /**
     * <p>Getter for the field <code>result</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.regression.RegressionResult} object
     */
    public RegressionResult getResult() {
        return this.result;
    }

    /**
     * <p>Getter for the field <code>outGraph</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getOutGraph() {
        return this.outGraph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getVariableNames() {
        return this.variableNames;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getRegressorNames() {
        return this.regressorNames;
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
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
     *
     * @param s The object input stream.
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.params == null) {
            throw new NullPointerException();
        }

    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        return new LinkedList<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getParamSettings() {
        Map<String, String> paramSettings = new HashMap<>();
        paramSettings.put("Algorithm", "Regression");
        return paramSettings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getAllParamSettings() {
        return this.allParamsSettings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAllParamSettings(Map<String, String> paramSettings) {
        this.allParamsSettings = paramSettings;
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
     * {@inheritDoc}
     */
    @Override
    public List<Graph> getGraphs() {
        return null;
    }
}





