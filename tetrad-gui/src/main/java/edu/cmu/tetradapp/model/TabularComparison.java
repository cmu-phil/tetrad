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

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.session.DoNotAddOldModel;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.session.SimulationParamsSource;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Compares a target workbench with a reference workbench by counting errors of
 * omission and commission. (for edge presence only, not orientation).
 *
 * @author Joseph Ramsey
 * @author Erin Korber (added remove latents functionality July 2004)
 */
public final class TabularComparison implements SessionModel, SimulationParamsSource,
        DoNotAddOldModel {

    static final long serialVersionUID = 23L;
    private final Parameters params;
    private final Graph targetGraph;
    private final Graph referenceGraph;
    private DataModel dataModel = null;
    private String name;
    private Map<String, String> allParamSettings;
    private DataSet dataSet;
    private ArrayList<Statistic> statistics;
    private String targetName;
    private String referenceName;

    //=============================CONSTRUCTORS==========================//

    public TabularComparison(GraphSource model1, GraphSource model2,
                             Parameters params) {
        this(model1, model2, null, params);
    }

    /**
     * Compares the results of a PC to a reference workbench by counting errors
     * of omission and commission. The counts can be retrieved using the methods
     * <code>countOmissionErrors</code> and <code>countCommissionErrors</code>.
     */
    public TabularComparison(GraphSource model1, GraphSource model2,
                             DataWrapper dataWrapper, Parameters params) {
        if (params == null) {
            throw new NullPointerException("Parameters must not be null");
        }

        if (model1 == null || model2 == null) {
            throw new NullPointerException("Null graph source>");
        }

        this.params = params;

        if (dataWrapper != null) {
            this.dataModel = dataWrapper.getDataModelList().get(0);
        }

        this.referenceName = params.getString("referenceGraphName", null);
        this.targetName = params.getString("targetGraphName", null);

//        if (model1 == null) {
//            this.referenceGraph = model2.getGraph();
//            this.targetGraph = model2.getGraph();
//        } else {

        if (referenceName.equals(model2.getName())) {
            GraphSource g = model1;
            model1 = model2;
            model2 = g;
        }

        if (referenceName.equals(model1.getName())) {
            this.referenceGraph = model1.getGraph();
            this.targetGraph = model2.getGraph();
        } else {
            throw new IllegalArgumentException(
                    "Neither of the supplied session models is named '"
                            + referenceName + "'.");
        }

        if (targetGraph.isPag() || referenceGraph.isPag()) {
            targetGraph.setPag(true);
            referenceGraph.setPag(true);
        }
//        }

        newExecution();

        addRecord();

        TetradLogger.getInstance().log("info", "Graph Comparison");
    }

    private void newExecution() {
        statistics = new ArrayList<>();
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new TwoCyclePrecision());
        statistics.add(new TwoCycleRecall());
        statistics.add(new TwoCycleFalsePositive());

        List<Node> variables = new ArrayList<>();

        for (Statistic statistic : statistics) {
            variables.add(new ContinuousVariable(statistic.getAbbreviation()));
        }

        dataSet = new BoxDataSet(new DoubleDataBox(0, variables.size()), variables);
        dataSet.setNumberFormat(new DecimalFormat("0.00"));
    }

    private void addRecord() {
        int newRow = dataSet.getNumRows();

        for (int j = 0; j < statistics.size(); j++) {
            Statistic statistic = statistics.get(j);
            double value = statistic.getValue(this.referenceGraph, this.targetGraph, null);
            dataSet.setDouble(newRow, j, value);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
//    public static TabularComparison serializableInstance() {
//        return new TabularComparison(DagWrapper.serializableInstance(),
//                DagWrapper.serializableInstance(),
//                new Parameters());
//    }
    //==============================PUBLIC METHODS========================//
    public DataSet getDataSet() {
        return this.dataSet;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    //============================PRIVATE METHODS=========================//

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    @Override
    public Map<String, String> getParamSettings() {
        return new HashMap<>();
    }

    @Override
    public Map<String, String> getAllParamSettings() {
        return allParamSettings;
    }

    @Override
    public void setAllParamSettings(Map<String, String> paramSettings) {
        this.allParamSettings = new LinkedHashMap<>(paramSettings);
    }

    public Graph getReferenceGraph() {
        return referenceGraph;
    }

    public Graph getTargetGraph() {
        return targetGraph;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getReferenceName() {
        return referenceName;
    }

    public void setReferenceName(String referenceName) {
        this.referenceName = referenceName;
    }

    public Parameters getParams() {
        return params;
    }

    public DataModel getDataModel() {
        return dataModel;
    }
}
