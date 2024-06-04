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
     * @param dataWrapper the data wrapper to use for the comparison. (Unused here.)
     * @param params      the parameters for the comparison.
     */
    public TabularComparison(GraphSource model1, GraphSource model2,
                             DataWrapper dataWrapper, Parameters params) {
        if (params == null) {
            throw new NullPointerException("Parameters must not be null");
        }

        if (model1 == null || model2 == null) {
            throw new NullPointerException("Null graph source>");
        }

        if (model1 instanceof GeneralAlgorithmRunner) {
            this.elapsedTime = ((GeneralAlgorithmRunner) model1).getElapsedTime();
        } else if (model2 instanceof GeneralAlgorithmRunner) {
            this.elapsedTime = ((GeneralAlgorithmRunner) model2).getElapsedTime();
        }

        this.params = params;

        this.referenceName = params.getString("referenceGraphName", null);
        this.targetName = params.getString("targetGraphName", null);

        String model1Name = model1.getName();
        String model2Name = model2.getName();

        if (this.referenceName.equals(model1Name)) {
            this.referenceGraph = model1.getGraph();
            this.targetGraph = model2.getGraph();
        } else if (this.referenceName.equals(model2Name)) {
            this.referenceGraph = model2.getGraph();
            this.targetGraph = model1.getGraph();
        } else {
            this.referenceGraph = model1.getGraph();
            this.targetGraph = model2.getGraph();
        }

        newExecution();

        TetradLogger.getInstance().log("Graph Comparison");
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
            double value = statistic.getValue(this.referenceGraph, this.targetGraph, null);
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
