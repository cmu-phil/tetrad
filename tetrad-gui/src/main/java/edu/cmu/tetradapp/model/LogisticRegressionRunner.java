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
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the Regression
 * algorithm.
 *
 * @author Frank Wimberly after Joe Ramsey's PcRunner
 */
public class LogisticRegressionRunner implements AlgorithmRunner, RegressionModel {

    static final long serialVersionUID = 23L;

    private String name;
    private final Parameters params;
    private String targetName = null;
    private List<String> regressorNames = new ArrayList<>();
    private List<DataSet> dataSets;
    private String report;
    private Graph outGraph;
    private LogisticRegression.Result result;
    private double alpha = 0.001;

    private int numModels = 1;
    private int modelIndex = 0;
    private String modelSourceName = null;

    private final List<String> variableNames;

    //=========================CONSTRUCTORS===============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public LogisticRegressionRunner(final DataWrapper dataWrapper, final Parameters params) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }

        if (params == null) {
            throw new NullPointerException();
        }

        if (dataWrapper instanceof Simulation) {
            final Simulation simulation = (Simulation) dataWrapper;
            final DataModelList dataModelList = dataWrapper.getDataModelList();
            this.dataSets = new ArrayList<>();

            for (final DataModel dataModel : dataModelList) {
                this.dataSets.add((DataSet) dataModel);
            }

            this.numModels = dataModelList.size();
            this.modelIndex = 0;
            this.modelSourceName = simulation.getName();
        } else {
            final DataModel dataModel = dataWrapper.getSelectedDataModel();

            if (!(dataModel instanceof DataSet)) {
                throw new IllegalArgumentException("Data set must be tabular.");
            }

            setDataSet((DataSet) dataModel);
        }

        this.params = params;

        this.variableNames = getDataModel().getVariableNames();
        this.targetName = null;
        this.regressorNames = new ArrayList<>();

        TetradLogger.getInstance().log("info", "Linear Regression");

        if (this.result == null) {
            TetradLogger.getInstance().log("info", "Please double click this regression node to run the regession.");
        } else {
            TetradLogger.getInstance().log("result", this.report);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static LogisticRegressionRunner serializableInstance() {
        final List<Node> variables = new LinkedList<>();
        final ContinuousVariable var1 = new ContinuousVariable("X");
        final ContinuousVariable var2 = new ContinuousVariable("Y");

        variables.add(var1);
        variables.add(var2);

        final DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(3, variables.size()), variables);
        final double[] col1data = new double[]{0.0, 1.0, 2.0};
        final double[] col2data = new double[]{2.3, 4.3, 2.5};

        for (int i = 0; i < 3; i++) {
            dataSet.setDouble(i, 0, col1data[i]);
            dataSet.setDouble(i, 1, col2data[i]);
        }

        final DataWrapper dataWrapper = new DataWrapper(dataSet);
        return new LogisticRegressionRunner(dataWrapper, new Parameters());
    }

    //===========================PUBLIC METHODS============================//
    public DataModel getDataModel() {
        return this.dataSets.get(getModelIndex());
    }

    /**
     * @return the alpha or -1.0 if the params aren't set.
     */
    public double getAlpha() {
        return this.alpha;//this.params.getDouble("alpha", 0.001);
    }

    public void setAlpha(final double alpha) {
        this.alpha = alpha;
    }

    public LogisticRegression.Result getResult() {
        return this.result;
    }

    public Parameters getParams() {
        return this.params;
    }

    public Graph getResultGraph() {
        return this.outGraph;
    }

    public void setResultGraph(final Graph graph) {
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
        this.outGraph = new EdgeListGraph();

        if (this.regressorNames == null || this.regressorNames.isEmpty() || this.targetName == null) {
            this.report = "Response and predictor variables not set.";

            return;
        }

        if (this.regressorNames.contains(this.targetName)) {
            this.report = "Response must not be a predictor.";

            return;
        }

        final DataSet regressorsDataSet = this.dataSets.get(getModelIndex()).copy();
        final Node target = regressorsDataSet.getVariable(this.targetName);
        regressorsDataSet.removeColumn(target);

        final List<String> names = regressorsDataSet.getVariableNames();

        //Get the list of regressors selected by the user
        final List<Node> regressorNodes = new ArrayList<>();

        for (final String s : this.regressorNames) {
            regressorNodes.add(this.dataSets.get(getModelIndex()).getVariable(s));
        }

        //If the user selected none, use them all
        if (this.regressorNames.size() > 0) {
            for (final String name1 : names) {
                final Node regressorVar = regressorsDataSet.getVariable(name1);
                if (!this.regressorNames.contains(regressorVar.getName())) {
                    regressorsDataSet.removeColumn(regressorVar);
                }
            }
        }

        final int ncases = regressorsDataSet.getNumRows();
        final int nvars = regressorsDataSet.getNumColumns();

        final double[][] regressors = new double[nvars][ncases];

        for (int i = 0; i < nvars; i++) {
            for (int j = 0; j < ncases; j++) {
                regressors[i][j] = regressorsDataSet.getDouble(j, i);
            }
        }

        final LogisticRegression logRegression = new LogisticRegression(this.dataSets.get(getModelIndex()));
        logRegression.setAlpha(this.alpha);

        this.result = logRegression.regress((DiscreteVariable) target, regressorNodes);
    }

    public boolean supportsKnowledge() {
        return false;
    }

    public ImpliedOrientation getMeekRules() {
        throw new UnsupportedOperationException();
    }

    public void setExternalGraph(final Graph graph) {
    }

    public Graph getExternalGraph() {
        return null;
    }

    @Override
    public String getAlgorithmName() {
        return "Logistic-Regression";
    }

    public Graph getOutGraph() {
        return this.outGraph;
    }

    @Override
    public List<String> getVariableNames() {
        return this.variableNames;
    }

    @Override
    public List<String> getRegressorNames() {
        return new ArrayList<>();
    }

    @Override
    public void setRegressorName(final List<String> predictors) {
        this.regressorNames = predictors;
    }

    public String getTargetName() {
        return this.targetName;
    }

    @Override
    public void setTargetName(final String target) {
        this.targetName = target;
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
    private void readObject(final ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public Graph getGraph() {
        return this.outGraph;
    }

    /**
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        return new LinkedList<>();
    }

    /**
     * @param node The node that the classifications are for. All triple from
     *             adjacencies to this node to adjacencies to this node through the given
     *             node will be considered.
     * @return the list of triples corresponding to
     * <code>getTripleClassificationNames</code> for the given node.
     */
    public List<List<Triple>> getTriplesLists(final Node node) {
        return new LinkedList<>();
    }

    @Override
    public Map<String, String> getParamSettings() {
        final Map<String, String> paramSettings = new HashMap<>();
        paramSettings.put("Algorithm", "Regression");
        return paramSettings;
    }

    @Override
    public void setAllParamSettings(final Map<String, String> paramSettings) {
//        Map<String, String> allParamsSettings = paramSettings;
    }

    @Override
    public Map<String, String> getAllParamSettings() {
        return null;
    }

    public int getNumModels() {
        return this.numModels;
    }

    public int getModelIndex() {
        return this.modelIndex;
    }

    public String getModelSourceName() {
        return this.modelSourceName;
    }

    public void setModelIndex(final int modelIndex) {
        this.modelIndex = modelIndex;
    }

    public void setDataSet(final DataSet dataSet) {
        this.dataSets = new ArrayList<>();
        this.dataSets.add(dataSet);
    }

    @Override
    public List<Graph> getGraphs() {
        return null;
    }
}
