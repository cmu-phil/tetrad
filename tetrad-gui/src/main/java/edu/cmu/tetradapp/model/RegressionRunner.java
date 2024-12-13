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
import java.io.ObjectOutputStream;
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

        TetradLogger.getInstance().log("Linear Regression");

        if (this.result == null) {
            TetradLogger.getInstance().log("Please double click this regression node to run the regession.");
        } else {
            String message = "\n" + this.result.getResultsTable().toString();
            TetradLogger.getInstance().log(message);
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
        //return (DataModel) this.dataWrapper.getDataModelList().getFirst();
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
     * Sets the external graph to be used by the algorithm.
     *
     * @param graph a {@link Graph} object representing the external graph
     */
    public void setExternalGraph(Graph graph) {
    }

    /**
     * Returns the name of the algorithm.
     *
     * @return the name of the algorithm as a string
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
     * Returns the list of variable names used in the algorithm.
     *
     * @return a List of String representing the variable names
     */
    @Override
    public List<String> getVariableNames() {
        return this.variableNames;
    }

    /**
     * Returns the list of regressor names used in the algorithm.
     *
     * @return a list of String representing the regressor names
     */
    @Override
    public List<String> getRegressorNames() {
        return this.regressorNames;
    }

    /**
     * Sets the names of the regressors used in the algorithm.
     *
     * @param predictors the names of the regressors
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
     * Sets the target name for the regression model.
     *
     * @param target the name of the target variable as a String
     */
    @Override
    public void setTargetName(String target) {
        this.targetName = target;
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
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
     * Sets the name of the session model.
     *
     * @param name the name of the session model
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the graph associated with the current RegressionRunner instance.
     *
     * @return the graph associated with the current RegressionRunner instance
     */
    public Graph getGraph() {
        return this.outGraph;
    }

    /**
     * Returns the list of classification types for triples.
     *
     * @return a list of strings representing the classification types for triples
     */
    public List<String> getTriplesClassificationTypes() {
        return new LinkedList<>();
    }

    /**
     * Retrieves the list of triple lists associated with a given node.
     *
     * @param node The node for which to retrieve the triple lists.
     * @return The list of triple lists associated with the given node.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        return new LinkedList<>();
    }

    /**
     * Retrieves the parameter settings for the algorithm.
     *
     * @return a map of parameter names to their corresponding values as strings
     */
    @Override
    public Map<String, String> getParamSettings() {
        Map<String, String> paramSettings = new HashMap<>();
        paramSettings.put("Algorithm", "Regression");
        return paramSettings;
    }

    /**
     * Retrieves all parameter settings for the algorithm.
     *
     * @return a map of parameter names to their corresponding values as strings
     */
    @Override
    public Map<String, String> getAllParamSettings() {
        return this.allParamsSettings;
    }

    /**
     * Sets all parameter settings for the algorithm.
     *
     * @param paramSettings a map of parameter names to their corresponding values as strings.
     */
    @Override
    public void setAllParamSettings(Map<String, String> paramSettings) {
        this.allParamsSettings = paramSettings;
    }

    /**
     * Retrieves the number of models in the RegressionRunner instance.
     *
     * @return the number of models as an integer
     */
    public int getNumModels() {
        return this.numModels;
    }

    /**
     * Get the index of the current model.
     *
     * @return the index of the current model as an integer
     */
    public int getModelIndex() {
        return this.modelIndex;
    }

    /**
     * Sets the index of the current model.
     *
     * @param modelIndex the index of the current model
     */
    public void setModelIndex(int modelIndex) {
        this.modelIndex = modelIndex;
    }

    /**
     * Returns the source name of the model.
     *
     * @return the source name of the model as a string
     */
    public String getModelSourceName() {
        return this.modelSourceName;
    }

    /**
     * Returns the list of Graph objects associated with the current RegressionRunner instance.
     *
     * @return a List of Graph objects
     */
    @Override
    public List<Graph> getGraphs() {
        return null;
    }
}





