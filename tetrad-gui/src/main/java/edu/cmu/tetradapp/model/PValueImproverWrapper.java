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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.search.work_in_progress.Hbsms;
import edu.cmu.tetrad.search.work_in_progress.HbsmsBeam;
import edu.cmu.tetrad.search.work_in_progress.HbsmsGes;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.*;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the GES algorithm.
 *
 * @author Ricardo Silva
 */
public class PValueImproverWrapper extends AbstractAlgorithmRunner {
    @Serial
    private static final long serialVersionUID = 23L;
    private final DataWrapper dataWrapper;
    private final Parameters params = new Parameters();
    private AlgorithmType algorithmType = AlgorithmType.BEAM;
    private String name;
    private Graph externalGraph;
    private Graph graph;
    private transient List<PropertyChangeListener> listeners;
    private Parameters params2;
    private SemIm originalSemIm;
    private SemIm newSemIm;

    public PValueImproverWrapper(DataWrapper dataWrapper,
                                 Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        this.graph = new EdgeListGraph(dataWrapper.getSelectedDataModel().getVariables());
    }

    //============================CONSTRUCTORS============================//

    public PValueImproverWrapper(DataWrapper dataWrapper,
                                 Parameters params) {
        super(dataWrapper, params, null);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(dataWrapper.getSelectedDataModel().getVariables()));
    }

    public PValueImproverWrapper(GraphWrapper graphWrapper,
                                 DataWrapper dataWrapper,
                                 Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(GraphWrapper graphWrapper,
                                 DataWrapper dataWrapper,
                                 Parameters params) {
        super(dataWrapper, params);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(DagWrapper graphWrapper,
                                 DataWrapper dataWrapper,
                                 Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(DagWrapper graphWrapper,
                                 DataWrapper dataWrapper,
                                 Parameters params) {
        super(dataWrapper, params);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(SemGraphWrapper graphWrapper,
                                 DataWrapper dataWrapper,
                                 Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(SemGraphWrapper graphWrapper,
                                 DataWrapper dataWrapper,
                                 Parameters params) {
        super(dataWrapper, params);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(AbstractAlgorithmRunner graphWrapper,
                                 DataWrapper dataWrapper,
                                 Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(AbstractAlgorithmRunner graphWrapper,
                                 DataWrapper dataWrapper,
                                 Parameters params) {
        super(dataWrapper, params);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    public AlgorithmType getAlgorithmType() {
        return this.algorithmType;
    }

    public void setAlgorithmType(AlgorithmType algorithmType) {
        this.algorithmType = algorithmType;
    }

    //============================PUBLIC METHODS==========================//

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isShuffleMoves() {
        return false;
    }

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be implemented in the extending class.
     */

    public void execute() {
        DataModel dataModel = getDataModel();

        Knowledge knowledge = (Knowledge) this.params2.get("knowledge", new Knowledge());

        if (this.externalGraph == null) {
            this.externalGraph = new EdgeListGraph(dataModel.getVariables());
        }

        Graph graph2 = new EdgeListGraph(this.externalGraph);
        graph2 = GraphUtils.replaceNodes(graph2, dataModel.getVariables());

        Hbsms search;

        if (dataModel instanceof DataSet) {
            DataSet dataSet = (DataSet) dataModel;

            if (getAlgorithmType() == AlgorithmType.BEAM) {
                search = new HbsmsBeam(graph2, dataSet, knowledge);
            } else if (getAlgorithmType() == AlgorithmType.FGES) {
                search = new HbsmsGes(graph2, dataSet);
                search.setKnowledge(knowledge);
            } else {
                throw new IllegalStateException();
            }
        } else if (dataModel instanceof CovarianceMatrix) {
            CovarianceMatrix covarianceMatrix = (CovarianceMatrix) dataModel;

            if (getAlgorithmType() == AlgorithmType.BEAM) {
                search = new HbsmsBeam(graph2, covarianceMatrix, knowledge);
            } else if (getAlgorithmType() == AlgorithmType.FGES) {
                throw new IllegalArgumentException("GES method requires a dataset; a covariance matrix was provided.");
            } else {
                throw new IllegalStateException();
            }
        } else {
            throw new IllegalStateException();
        }

        Parameters params = getParams();

        search.setAlpha(params.getDouble("alpha", 0.001));
        search.setBeamWidth(params.getInt("beamWidth", 5));
        search.setHighPValueAlpha(params.getDouble("zeroEdgeP", 0.05));
        this.graph = search.search();

//        this.graph = search.getNewSemIm().getSemPm().getGraph();

        setOriginalSemIm(search.getOriginalSemIm());
        this.newSemIm = search.getNewSemIm();
        fireGraphChange(this.graph);

        if (getSourceGraph() != null) {
            LayoutUtil.arrangeBySourceGraph(this.graph, getSourceGraph());
        } else if (knowledge.isDefaultToKnowledgeLayout()) {
            GraphSearchUtils.arrangeByKnowledgeTiers(this.graph, knowledge);
        } else {
            LayoutUtil.defaultLayout(this.graph);
        }

        setResultGraph(GraphTransforms.cpdagForDag(this.graph));
    }

    public boolean supportsKnowledge() {
        return true;
    }

    public MeekRules getMeekRules() {
        MeekRules rules = new MeekRules();
        rules.setKnowledge((Knowledge) this.params.get("knowledge", new Knowledge()));
        return rules;
    }

    @Override
    public String getAlgorithmName() {
        return "BFF";
    }

    private boolean isMeekPreventCycles() {
        return this.params.getBoolean("MeekPreventCycles", false);
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        if (!getListeners().contains(l)) getListeners().add(l);
    }

    private void fireGraphChange(Graph graph) {
        for (PropertyChangeListener l : getListeners()) {
            l.propertyChange(new PropertyChangeEvent(this, "graph", null, graph));
        }
    }

    public Graph getGraph() {
        return getResultGraph();
    }

    private void setGraph(EdgeListGraph graph) {
        this.graph = new EdgeListGraph(graph);
        this.externalGraph = new EdgeListGraph(graph);
    }

    /**
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        return new LinkedList<>();
    }

    /**
     * @param node The node that the classifications are for. All triple from adjacencies to this node to adjacencies to
     *             this node through the given node will be considered.
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code> for the given node.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        return new LinkedList<>();
    }

    private List<PropertyChangeListener> getListeners() {
        if (this.listeners == null) {
            this.listeners = new ArrayList<>();
        }
        return this.listeners;
    }

    public DataSet simulateDataCholesky(int sampleSize, Matrix covar, List<Node> variableNodes) {

        List<Node> variables = new LinkedList<>(variableNodes);

        List<Node> newVariables = new ArrayList<>();

        for (Node node : variables) {
            ContinuousVariable continuousVariable = new ContinuousVariable(node.getName());
            continuousVariable.setNodeType(node.getNodeType());
            newVariables.add(continuousVariable);
        }

        DataSet fullDataSet = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, newVariables.size()), newVariables);
        Matrix cholesky = MatrixUtils.cholesky(covar);

        // Simulate the data by repeatedly calling the Cholesky.exogenousData
        // method. Store only the data for the measured variables.
        for (int row = 0; row < sampleSize; row++) {

            // Step 1. Generate normal samples.
            double[] exoData = new double[cholesky.getNumRows()];

            for (int i = 0; i < exoData.length; i++) {
                exoData[i] = RandomUtil.getInstance().nextNormal(0, 1);
                //            exoData[i] = randomUtil.nextUniform(-1, 1);
            }

            // Step 2. Multiply by cholesky to get correct covariance.
            double[] point = new double[exoData.length];

            for (int i = 0; i < exoData.length; i++) {
                double sum = 0.0;

                for (int j = 0; j <= i; j++) {
                    sum += cholesky.get(i, j) * exoData[j];
                }

                point[i] = sum;
            }

            for (int col = 0; col < variables.size(); col++) {
                int index = variableNodes.indexOf(variables.get(col));
                double value = point[index];

                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    throw new IllegalArgumentException("Value out of range: " + value);
                }

                fullDataSet.setDouble(row, col, value);
            }
        }

        return DataTransforms.restrictToMeasured(fullDataSet);
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.params2 == null) {
            this.params2 = new Parameters();
        }
    }

    public SemIm getOriginalSemIm() {
        return this.originalSemIm;
    }

    private void setOriginalSemIm(SemIm originalSemIm) {
        if (this.originalSemIm == null) {
            this.originalSemIm = originalSemIm;
        }
    }

    public SemIm getNewSemIm() {
        return this.newSemIm;
    }

    public void setNewSemIm(SemIm newSemIm) {
        this.newSemIm = newSemIm;
    }

    public enum AlgorithmType {
        BEAM, FGES
    }
}



