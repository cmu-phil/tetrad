///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Implements a model for the linear adjustment regression. The linear adjustment regression model is used to calculate
 * the total effect of a linear adjustment regression on a target node, given a source node and an adjustment set. The
 * model also provides a method to retrieve the regression result string for a given source node, target node, and
 * adjustment set.
 *
 * @author josephramsey
 */
public class LinearAdjustmentRegressionModel implements SessionModel, GraphSource, KnowledgeBoxInput {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * The data model to check.
     */
    private final DataModel dataModel;
    /**
     * The graph to check.
     */
    private final Graph graph;
    /**
     * The parameters.
     */
    private final Parameters parameters;
    /**
     * A private final List of nodes in a given variable.
     */
    private final List<Node> nodes;
    /**
     * Private final field that holds a list of strings representing node names.
     */
    private final List<String> nodeNames;
    /**
     * The name of this model.
     */
    private String name = "";

    /**
     * Represents a linear adjustment regression model.
     *
     * @param dataModel    The data model used for regression.
     * @param graphSource  The source of the graph.
     * @param parameters   The parameters for the regression model.
     */
    public LinearAdjustmentRegressionModel(DataWrapper dataModel, GraphSource graphSource, Parameters parameters) {
        this.dataModel = dataModel.getSelectedDataModel();
        this.nodes = dataModel.getVariables();
        this.nodeNames = dataModel.getVarNames();
        this.graph = GraphUtils.replaceNodes(graphSource.getGraph(), this.nodes);
        this.parameters = parameters;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link Knowledge} object
     * @see TetradSerializableUtils
     */
    public static Knowledge serializableInstance() {
        return new Knowledge();
    }

    /**
     * Retrieves an adjustment set from the graph between the specified source and target nodes.
     *
     * @param source The source node.
     * @param target The target node.
     * @return A list of sets of nodes representing the adjustment sets.
     * @throws IllegalArgumentException if there are no amenable paths.
     */
    public List<Set<Node>> getAdjustmentSets(Node source, Node target) {
        int maxNumSets = parameters.getInt("pathsMaxNumSets");
        int maxDistanceFromEndpoint = parameters.getInt("pathsMaxDistanceFromEndpoint");
        int nearWhichEndpoint = parameters.getInt("pathsNearWhichEndpoint");
        int maxPathLength = parameters.getInt("pathsMaxLength");

        return graph.paths().adjustmentSets(source, target, maxNumSets, maxDistanceFromEndpoint, nearWhichEndpoint,
                maxPathLength);
    }

    /**
     * Calculates the total effect of a linear adjustment regression on a target node, given a source node
     * and an adjustment set.
     *
     * @param source         The source node.
     * @param target         The target node.
     * @param adjustmentSet  The adjustment set, which should not contain the source or target nodes.
     * @return The total effect of the regression.
     * @throws IllegalArgumentException if the adjustment set contains the source or target nodes.
     */
    public double totalEffect(Node source, Node target, Set<Node> adjustmentSet) {
        if (adjustmentSet.contains(source) || adjustmentSet.contains(target)) {
            throw new IllegalArgumentException("Adjustment set cannot contain source or target nodes.");
        }

        RegressionDataset regressionDataset = new RegressionDataset((DataSet) dataModel);

        List<Node> regressors = new ArrayList<>();
        regressors.add(source);
        regressors.addAll(adjustmentSet);

        RegressionResult result = regressionDataset.regress(target, regressors);
        return result.getCoef()[1];
    }

    /**
     * Retrieves the regression result string for a given source node, target node, and adjustment set.
     *
     * @param source         The source node.
     * @param target         The target node.
     * @param adjustmentSet  The adjustment set, which should not contain the source or target nodes.
     * @return The regression result string.
     * @throws IllegalArgumentException if the adjustment set contains the source or target nodes.
     */
    public String getRegressionString(Node source, Node target, Set<Node> adjustmentSet) {
        if (adjustmentSet.contains(source) || adjustmentSet.contains(target)) {
            throw new IllegalArgumentException("Adjustment set cannot contain source or target nodes.");
        }

        RegressionDataset regressionDataset = new RegressionDataset((DataSet) dataModel);

        List<Node> regressors = new ArrayList<>();
        regressors.add(source);
        regressors.addAll(adjustmentSet);

        RegressionResult result = regressionDataset.regress(target, regressors);
        return result.toString();
    }

    /**
     * Retrieves the graph associated with this linear adjustment regression model.
     *
     * @return The graph.
     */
    @Override
    public Graph getGraph() {
        return graph;
    }

    /**
     * Retrieves the source graph associated with this linear adjustment regression model.
     *
     * @return The source graph.
     */
    @Override
    public Graph getSourceGraph() {
        return graph;
    }

    /**
     * Retrieves the result graph associated with this linear adjustment regression model.
     *
     * @return The result graph.
     */
    @Override
    public Graph getResultGraph() {
        return graph;
    }

    /**
     * Retrieves the list of variables associated with this method.
     *
     * @return the list of variables.
     */
    @Override
    public List<Node> getVariables() {
        return new ArrayList<>(nodes);
    }

    /**
     * Retrieves the list of variable names associated with this method.
     *
     * @return the list of variable names.
     */
    @Override
    public List<String> getVariableNames() {
        return new ArrayList<>(nodeNames);
    }

    /**
     * Retrieves the name of the session model.
     *
     * @return the name of the session model.
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the session model.
     *
     * @param name the name of the session model.
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * The parameters.
     *
     * @return the parameters.
     */
    public Parameters getParameters() {
        return parameters;
    }
}




