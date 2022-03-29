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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.*;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the GES algorithm.
 *
 * @author Ricardo Silva
 */

public class PValueImproverWrapper extends AbstractAlgorithmRunner {
    static final long serialVersionUID = 23L;

    public enum AlgorithmType {
        BEAM, FGES
    }

    private AlgorithmType algorithmType = AlgorithmType.BEAM;

    private String name;
    private Graph externalGraph;
    private Graph graph;
    private transient List<PropertyChangeListener> listeners;
    private final DataWrapper dataWrapper;

    /**
     * @deprecated
     */
    private Parameters params;
    private Parameters params2;
    private SemIm estSem;
    private Graph trueDag;

    /**
     * @deprecated
     */
    private final double alpha = 0.05;
    private SemIm originalSemIm;
    private SemIm newSemIm;

    /**
     * @deprecated
     */
    private SemIm semIm;

    //============================CONSTRUCTORS============================//

    public PValueImproverWrapper(final DataWrapper dataWrapper,
                                 final Parameters params, final KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        this.graph = new EdgeListGraph(dataWrapper.getSelectedDataModel().getVariables());
    }

    public PValueImproverWrapper(final DataWrapper dataWrapper,
                                 final Parameters params) {
        super(dataWrapper, params, null);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(dataWrapper.getSelectedDataModel().getVariables()));
    }

    private void setGraph(final EdgeListGraph graph) {
        this.graph = new EdgeListGraph(graph);
        this.externalGraph = new EdgeListGraph(graph);
    }

    public PValueImproverWrapper(final GraphWrapper graphWrapper,
                                 final DataWrapper dataWrapper,
                                 final Parameters params, final KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(final GraphWrapper graphWrapper,
                                 final DataWrapper dataWrapper,
                                 final Parameters params) {
        super(dataWrapper, params);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(final DagWrapper graphWrapper,
                                 final DataWrapper dataWrapper,
                                 final Parameters params, final KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(final DagWrapper graphWrapper,
                                 final DataWrapper dataWrapper,
                                 final Parameters params) {
        super(dataWrapper, params);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(final SemGraphWrapper graphWrapper,
                                 final DataWrapper dataWrapper,
                                 final Parameters params, final KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(final SemGraphWrapper graphWrapper,
                                 final DataWrapper dataWrapper,
                                 final Parameters params) {
        super(dataWrapper, params);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(final AbstractAlgorithmRunner graphWrapper,
                                 final DataWrapper dataWrapper,
                                 final Parameters params, final KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(final AbstractAlgorithmRunner graphWrapper,
                                 final DataWrapper dataWrapper,
                                 final Parameters params) {
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

    //============================PUBLIC METHODS==========================//


    public AlgorithmType getAlgorithmType() {
        return this.algorithmType;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public boolean isShuffleMoves() {
        final boolean shuffleMoves = false;
        return shuffleMoves;
    }

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be implemented in the extending class.
     */

    public void execute() {
        final DataModel dataModel = getDataModel();

        final IKnowledge knowledge = (IKnowledge) this.params2.get("knowledge", new Knowledge2());

        if (this.externalGraph == null) {
            this.externalGraph = new EdgeListGraph(dataModel.getVariables());
        }

        Graph graph2 = new EdgeListGraph(this.externalGraph);
        graph2 = GraphUtils.replaceNodes(graph2, dataModel.getVariables());

        final Bff search;

        if (dataModel instanceof DataSet) {
            final DataSet dataSet = (DataSet) dataModel;

            if (getAlgorithmType() == AlgorithmType.BEAM) {
                search = new BffBeam(graph2, dataSet, knowledge);
            } else if (getAlgorithmType() == AlgorithmType.FGES) {
                search = new BffGes(graph2, dataSet);
                search.setKnowledge(knowledge);
            } else {
                throw new IllegalStateException();
            }
        } else if (dataModel instanceof CovarianceMatrix) {
            final CovarianceMatrix covarianceMatrix = (CovarianceMatrix) dataModel;

            if (getAlgorithmType() == AlgorithmType.BEAM) {
                search = new BffBeam(graph2, covarianceMatrix, knowledge);
            } else if (getAlgorithmType() == AlgorithmType.FGES) {
                throw new IllegalArgumentException("GES method requires a dataset; a covariance matrix was provided.");
//                search = new BffGes(graph2, covarianceMatrix);
//                search.setKnowledge(knowledge);
            } else {
                throw new IllegalStateException();
            }
        } else {
            throw new IllegalStateException();
        }

        final Parameters params = getParams();

        search.setAlpha(params.getDouble("alpha", 0.001));
        search.setBeamWidth(params.getInt("beamWidth", 5));
        search.setHighPValueAlpha(params.getDouble("zeroEdgeP", 0.05));
        this.graph = search.search();

//        this.graph = search.getNewSemIm().getSemPm().getGraph();

        setOriginalSemIm(search.getOriginalSemIm());
        this.newSemIm = search.getNewSemIm();
        fireGraphChange(this.graph);

        if (getSourceGraph() != null) {
            GraphUtils.arrangeBySourceGraph(this.graph, getSourceGraph());
        } else if (knowledge.isDefaultToKnowledgeLayout()) {
            SearchGraphUtils.arrangeByKnowledgeTiers(this.graph, knowledge);
        } else {
            GraphUtils.circleLayout(this.graph, 200, 200, 150);
        }

        setResultGraph(SearchGraphUtils.cpdagForDag(this.graph));
    }

    public boolean supportsKnowledge() {
        return true;
    }

    public ImpliedOrientation getMeekRules() {
        final MeekRules rules = new MeekRules();
        rules.setKnowledge((IKnowledge) this.params.get("knowledge", new Knowledge2()));
        return rules;
    }

    @Override
    public String getAlgorithmName() {
        return "BFF";
    }

    public void setAlgorithmType(final AlgorithmType algorithmType) {
        this.algorithmType = algorithmType;
    }

    private boolean isAggressivelyPreventCycles() {
        return this.params.getBoolean("aggressivelyPreventCycles", false);
    }

    public void addPropertyChangeListener(final PropertyChangeListener l) {
        if (!getListeners().contains(l)) getListeners().add(l);
    }

    private void fireGraphChange(final Graph graph) {
        for (final PropertyChangeListener l : getListeners()) {
            l.propertyChange(new PropertyChangeEvent(this, "graph", null, graph));
        }
    }

    public Graph getGraph() {
        return getResultGraph();
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
    public List<List<Triple>> getTriplesLists(final Node node) {
        return new LinkedList<>();
    }


    private List<PropertyChangeListener> getListeners() {
        if (this.listeners == null) {
            this.listeners = new ArrayList<>();
        }
        return this.listeners;
    }


    public DataSet simulateDataCholesky(final int sampleSize, final Matrix covar, final List<Node> variableNodes) {
        final List<Node> variables = new LinkedList<>();

        for (final Node node : variableNodes) {
            variables.add(node);
        }

        final List<Node> newVariables = new ArrayList<>();

        for (final Node node : variables) {
            final ContinuousVariable continuousVariable = new ContinuousVariable(node.getName());
            continuousVariable.setNodeType(node.getNodeType());
            newVariables.add(continuousVariable);
        }

        final Matrix impliedCovar = covar;

        final DataSet fullDataSet = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, newVariables.size()), newVariables);
        final Matrix cholesky = MatrixUtils.cholesky(impliedCovar);

        // Simulate the data by repeatedly calling the Cholesky.exogenousData
        // method. Store only the data for the measured variables.
        for (int row = 0; row < sampleSize; row++) {

            // Step 1. Generate normal samples.
            final double[] exoData = new double[cholesky.rows()];

            for (int i = 0; i < exoData.length; i++) {
                exoData[i] = RandomUtil.getInstance().nextNormal(0, 1);
                //            exoData[i] = randomUtil.nextUniform(-1, 1);
            }

            // Step 2. Multiply by cholesky to get correct covariance.
            final double[] point = new double[exoData.length];

            for (int i = 0; i < exoData.length; i++) {
                double sum = 0.0;

                for (int j = 0; j <= i; j++) {
                    sum += cholesky.get(i, j) * exoData[j];
                }

                point[i] = sum;
            }

            final double[] rowData = point;

            for (int col = 0; col < variables.size(); col++) {
                final int index = variableNodes.indexOf(variables.get(col));
                final double value = rowData[index];

                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    throw new IllegalArgumentException("Value out of range: " + value);
                }

                fullDataSet.setDouble(row, col, value);
            }
        }

        return DataUtils.restrictToMeasured(fullDataSet);
    }

    private void setOriginalSemIm(final SemIm originalSemIm) {
        if (this.originalSemIm == null) {
            this.originalSemIm = originalSemIm;
        }
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
    private void readObject(final ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.params2 == null) {
            this.params2 = new Parameters();
        }
    }

    public SemIm getOriginalSemIm() {
        return this.originalSemIm;
    }

    public SemIm getNewSemIm() {
        return this.newSemIm;
    }

    public void setNewSemIm(final SemIm newSemIm) {
        this.newSemIm = newSemIm;
    }
}



