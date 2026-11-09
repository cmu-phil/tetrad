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

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.session.DoNotAddOldModel;
import edu.cmu.tetradapp.session.SessionModel;
import edu.cmu.tetradapp.session.SimulationParamsSource;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Compares a target workbench with a reference workbench by counting errors of omission and commission. (for edge
 * presence only, not orientation).
 *
 * @author josephramsey
 * @author Erin Korber (added remove latents functionality July 2004)
 * @version $Id: $Id
 */
public final class TabularComparison implements SessionModel, SimulationParamsSource,
        DoNotAddOldModel {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The target graph.
     */
    private final Graph targetGraph;

    /**
     * The reference graph.
     */
    private final Graph referenceGraph;

    /**
     * The parameters for the comparison.
     */
    private final Parameters params;

    /**
     * The data model.
     */
    private final DataModel dataModel = null;

    /**
     * The elapsed time in milliseconds.
     */
    private long elapsedTime = 0L;

    /**
     * The name of the comparison.
     */
    private String name;

    /**
     * The parameters for the comparison.
     */
    private Map<String, String> allParamSettings;

    /**
     * The data set.
     */
    private DataSet dataSet;

    /**
     * The statistics.
     */
    private ArrayList<Statistic> statistics;

    /**
     * The name of the target graph.
     */
    private String targetName;

    /**
     * The name of the reference graph.
     */
    private String referenceName;

    /**
     * The target graphs, in display order, when more than one graph source is compared to the reference. Null for
     * instances deserialized from sessions saved before multi-target comparison was added; see getTargetGraphs().
     */
    private List<Graph> targetGraphs;

    /**
     * The names of the target graphs, in display order. Null for instances deserialized from older sessions; see
     * getTargetNames().
     */
    private List<String> targetNames;

    /**
     * The elapsed times in milliseconds for the target graphs, in display order, with -1 for targets that are not
     * algorithm runs. Null for instances deserialized from older sessions; see getElapsedTimes().
     */
    private List<Long> elapsedTimes;

    //=============================CONSTRUCTORS==========================//

    /**
     * Compares the results an algorithm run using various statistics.
     *
     * @param model1 the first model to compare; its graph is used.
     * @param model2 the second model to compare; its graph is used.
     * @param params the parameters for the comparison.
     */
    public TabularComparison(GraphSource model1, GraphSource model2,
                             Parameters params) {
        this(model1, model2, null, params);
    }

    /**
     * Compares the results an algorithm run using various statistics.
     *
     * @param model1      the first model to compare; its graph is used.
     * @param model2      the second model to compare; its graph is used.
     * @param dataWrapper the data wrapper to use for the comparison. Its data are unused, but if it is also a graph
     *                    source (a Simulation box is both), its graph is compared along with the other two; see below.
     * @param params      the parameters for the comparison.
     */
    public TabularComparison(GraphSource model1, GraphSource model2,
                             DataWrapper dataWrapper, Parameters params) {
        // A Simulation is both a DataWrapper and a GraphSource, so with a Simulation and two search boxes as parents
        // the session binds this constructor exactly (two graph sources, one "data wrapper", Parameters), and
        // dropping the wrapper here silently lost the true graph: one search box became the reference. Treat a
        // wrapper that is a graph source as a third source, so this constructor and the array form agree whichever
        // one reflection offers first. A plain data box is still accepted and ignored. Fixed 2026-10-28.
        this(dataWrapper instanceof GraphSource source
                ? new GraphSource[]{requireNonNull(model1), requireNonNull(model2), source}
                : checkPair(model1, model2), params);
    }

    /**
     * Null-checks a graph source, with the legacy constructors' error message.
     */
    private static GraphSource requireNonNull(GraphSource model) {
        if (model == null) {
            throw new NullPointerException("Null graph source>");
        }

        return model;
    }

    /**
     * Compares the graphs of any number of graph sources against a single reference (true) graph, which is one of
     * them. The reference is chosen by the "referenceGraphName" parameter, falling back to a Simulation box if one
     * is attached; the remaining sources are the targets, ordered by the "targetGraphNames" parameter where
     * available. The session machinery matches this constructor when the comparison box has two or more graph
     * source parents; see SessionNode's handling of (X[], Parameters) constructors.
     *
     * @param models the graph sources to compare; one is the reference, the rest are targets.
     * @param params the parameters for the comparison.
     */
    public TabularComparison(GraphSource[] models, Parameters params) {
        if (params == null) {
            throw new NullPointerException("Parameters must not be null");
        }

        if (models == null || models.length == 0) {
            throw new IllegalArgumentException("At least one graph source expected.");
        }

        for (GraphSource model : models) {
            if (model == null) {
                throw new NullPointerException("Null graph source>");
            }
        }

        this.params = params;

        List<GraphSource> sources = GraphComparisonUtils.sortByName(models);
        GraphSource reference = GraphComparisonUtils.resolveReference(sources, params);
        List<GraphSource> targets = GraphComparisonUtils.orderTargets(sources, reference, params);

        this.referenceName = reference.getName();
        this.referenceGraph = reference.getGraph();

        this.targetGraphs = new ArrayList<>();
        this.targetNames = new ArrayList<>();
        this.elapsedTimes = new ArrayList<>();

        for (GraphSource target : targets) {
            this.targetGraphs.add(target.getGraph());
            this.targetNames.add(target.getName());
            this.elapsedTimes.add(target instanceof GeneralAlgorithmRunner runner ? runner.getElapsedTime() : -1L);
        }

        // Keep the single-target fields populated for the single-target code paths and for older code reading them.
        if (targets.isEmpty()) {
            this.targetGraph = reference.getGraph();
            this.targetName = reference.getName();
        } else {
            this.targetGraph = this.targetGraphs.getFirst();
            this.targetName = this.targetNames.getFirst();
            this.elapsedTime = Math.max(this.elapsedTimes.getFirst(), 0L);
        }

        newExecution();

        TetradLogger.getInstance().log("Graph Comparison");
    }

    /**
     * Bundles the two graph sources of the legacy pair constructors into an array, null-checking first so the
     * legacy constructors keep their error behavior.
     */
    private static GraphSource[] checkPair(GraphSource model1, GraphSource model2) {
        if (model1 == null || model2 == null) {
            throw new NullPointerException("Null graph source>");
        }

        return new GraphSource[]{model1, model2};
    }

    private void newExecution() {
        this.statistics = new ArrayList<>();
        this.statistics.add(new AdjacencyPrecision());
        this.statistics.add(new AdjacencyRecall());
        this.statistics.add(new ArrowheadPrecision());
        this.statistics.add(new ArrowheadRecall());
        this.statistics.add(new TwoCyclePrecision());
        this.statistics.add(new TwoCycleRecall());
        this.statistics.add(new TwoCycleFalsePositive());

        List<Node> variables = new ArrayList<>();

        for (Statistic statistic : this.statistics) {
            variables.add(new ContinuousVariable(statistic.getAbbreviation()));
        }

        this.dataSet = new BoxDataSet(new DoubleDataBox(0, variables.size()), variables);
        this.dataSet.setNumberFormat(new DecimalFormat("0.00"));
    }

    private void addRecord() {
        int newRow = this.dataSet.getNumRows();

        for (int j = 0; j < this.statistics.size(); j++) {
            Statistic statistic = this.statistics.get(j);
            double value = statistic.getValue(this.referenceGraph, this.targetGraph, null, new Parameters());
            this.dataSet.setDouble(newRow, j, value);
        }
    }

    //==============================PUBLIC METHODS========================//

    /**
     * <p>Getter for the field <code>dataSet</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet getDataSet() {
        return this.dataSet;
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

    //============================PRIVATE METHODS=========================//

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
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getParamSettings() {
        return new HashMap<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getAllParamSettings() {
        return this.allParamSettings;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAllParamSettings(Map<String, String> paramSettings) {
        this.allParamSettings = new LinkedHashMap<>(paramSettings);
    }

    /**
     * <p>Getter for the field <code>referenceGraph</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getReferenceGraph() {
        return this.referenceGraph;
    }

    /**
     * <p>Getter for the field <code>targetGraph</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getTargetGraph() {
        return this.targetGraph;
    }

    /**
     * Returns the target graphs, in display order. For instances deserialized from sessions saved before
     * multi-target comparison was added, returns the single target graph in a list.
     *
     * @return the target graphs.
     */
    public List<Graph> getTargetGraphs() {
        if (this.targetGraphs == null) {
            return Collections.singletonList(this.targetGraph);
        }

        return this.targetGraphs;
    }

    /**
     * Returns the names of the target graphs, in display order. For instances deserialized from older sessions,
     * returns the single target name in a list.
     *
     * @return the target names.
     */
    public List<String> getTargetNames() {
        if (this.targetNames == null) {
            return Collections.singletonList(this.targetName);
        }

        return this.targetNames;
    }

    /**
     * Returns the elapsed times in milliseconds for the target graphs, in display order, with -1 for targets that
     * are not algorithm runs. For instances deserialized from older sessions, returns the single elapsed time in a
     * list.
     *
     * @return the elapsed times.
     */
    public List<Long> getElapsedTimes() {
        if (this.elapsedTimes == null) {
            return Collections.singletonList(this.elapsedTime);
        }

        return this.elapsedTimes;
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
     * <p>Setter for the field <code>targetName</code>.</p>
     *
     * @param targetName a {@link java.lang.String} object
     */
    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    /**
     * <p>Getter for the field <code>referenceName</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getReferenceName() {
        return this.referenceName;
    }

    /**
     * <p>Setter for the field <code>referenceName</code>.</p>
     *
     * @param referenceName a {@link java.lang.String} object
     */
    public void setReferenceName(String referenceName) {
        this.referenceName = referenceName;
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
     * <p>Getter for the field <code>dataModel</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataModel} object
     */
    public DataModel getDataModel() {
        return this.dataModel;
    }

    /**
     * Returns the elapsed time in milliseconds. If the elapsed time is not available, this method returns -1.
     *
     * @return the elapsed time in milliseconds.
     */
    public long getElapsedTime() {
        return elapsedTime;
    }
}

