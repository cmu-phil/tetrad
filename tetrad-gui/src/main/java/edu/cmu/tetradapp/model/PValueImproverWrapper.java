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
import edu.cmu.tetrad.util.MatrixUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradSerializableUtils;

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

public class PValueImproverWrapper extends AbstractAlgorithmRunner implements GraphSource {
    static final long serialVersionUID = 23L;

    public enum AlgorithmType {
        BEAM, GES
    }

    private AlgorithmType algorithmType = AlgorithmType.BEAM;
    private boolean shuffleMoves = false;

    private String name;
    private Graph initialGraph;
    private Graph graph;
    private transient List<PropertyChangeListener> listeners;
    private DataWrapper dataWrapper;

    /**
     * @deprecated
     */
    private GesParams params;
    private PcSearchParams params2;
    private SemIm estSem;
    private Graph trueDag;

    /**
     * @deprecated
     */
    private double alpha = 0.05;
    private SemIm originalSemIm;
    private SemIm newSemIm;

    /**
     * @deprecated
     */
    private SemIm semIm;

    //============================CONSTRUCTORS============================//

    public PValueImproverWrapper(DataWrapper dataWrapper,
                                 PcSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        this.graph = new EdgeListGraph(dataWrapper.getSelectedDataModel().getVariables());
    }

    public PValueImproverWrapper(DataWrapper dataWrapper,
                                 PcSearchParams params) {
        super(dataWrapper, params, null);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(dataWrapper.getSelectedDataModel().getVariables()));
    }

    private void setGraph(EdgeListGraph graph) {
        this.graph = new EdgeListGraph(graph);
        this.initialGraph = new EdgeListGraph(graph);
    }

    public PValueImproverWrapper(GraphWrapper graphWrapper,
                                 DataWrapper dataWrapper,
                                 PcSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(GraphWrapper graphWrapper,
                                 DataWrapper dataWrapper,
                                 PcSearchParams params) {
        super(dataWrapper, params);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(DagWrapper graphWrapper,
                                 DataWrapper dataWrapper,
                                 PcSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(DagWrapper graphWrapper,
                                 DataWrapper dataWrapper,
                                 PcSearchParams params) {
        super(dataWrapper, params);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(SemGraphWrapper graphWrapper,
                                 DataWrapper dataWrapper,
                                 PcSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
   }

    public PValueImproverWrapper(SemGraphWrapper graphWrapper,
                                 DataWrapper dataWrapper,
                                 PcSearchParams params) {
        super(dataWrapper, params);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(AbstractAlgorithmRunner graphWrapper,
                                 DataWrapper dataWrapper,
                                 PcSearchParams params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.dataWrapper = dataWrapper;
        this.params2 = params;
        setGraph(new EdgeListGraph(graphWrapper.getGraph()));
    }

    public PValueImproverWrapper(AbstractAlgorithmRunner graphWrapper,
                                 DataWrapper dataWrapper,
                                 PcSearchParams params) {
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
    public static PValueImproverWrapper serializableInstance() {
        return new PValueImproverWrapper(GraphWrapper.serializableInstance(),
                DataWrapper.serializableInstance(),
                PcSearchParams.serializableInstance(), KnowledgeBoxModel.serializableInstance());
    }

    //============================PUBLIC METHODS==========================//


    public AlgorithmType getAlgorithmType() {
        return algorithmType;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public boolean isShuffleMoves() {
        return this.shuffleMoves;
    }

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be implemented in the extending class.
     */

    public void execute() {
        DataModel dataModel = getDataModel();

        IKnowledge knowledge = params2.getKnowledge();

        if (initialGraph == null) {
            initialGraph = new EdgeListGraph(dataModel.getVariables());
        }

        Graph graph2 = new EdgeListGraph(initialGraph);
        graph2 = GraphUtils.replaceNodes(graph2, dataModel.getVariables());

        Bff search;

        if (dataModel instanceof DataSet) {
            DataSet dataSet = (DataSet) dataModel;

            if (getAlgorithmType() == AlgorithmType.BEAM) {
                search = new BffBeam(graph2, dataSet, knowledge);
            } else if (getAlgorithmType() == AlgorithmType.GES) {
                search = new BffGes(graph2, dataSet);
                search.setKnowledge(knowledge);
            } else {
                throw new IllegalStateException();
            }
        }
        else if (dataModel instanceof CovarianceMatrix) {
            CovarianceMatrix covarianceMatrix = (CovarianceMatrix) dataModel;

            if (getAlgorithmType() == AlgorithmType.BEAM) {
                search = new BffBeam(graph2, covarianceMatrix, knowledge);
            } else if (getAlgorithmType() == AlgorithmType.GES) {
                throw new IllegalArgumentException("GES method requires a dataset; a covariance matrix was provided.");
//                search = new BffGes(graph2, covarianceMatrix);
//                search.setKnowledge(knowledge);
            } else {
                throw new IllegalStateException();
            }
        }
        else {
            throw new IllegalStateException();
        }

        PcIndTestParams indTestParams = (PcIndTestParams) getParams().getIndTestParams();

        search.setAlpha(indTestParams.getAlpha());
        search.setBeamWidth(indTestParams.getBeamWidth());
        search.setHighPValueAlpha(indTestParams.getZeroEdgeP());
        this.graph = search.search();

//        this.graph = search.getNewSemIm().getSemPm().getGraph();

        setOriginalSemIm(search.getOriginalSemIm());
        this.newSemIm = search.getNewSemIm();
        fireGraphChange(graph);

        if (getSourceGraph() != null) {
            GraphUtils.arrangeBySourceGraph(graph, getSourceGraph());
        } else if (knowledge.isDefaultToKnowledgeLayout()) {
            SearchGraphUtils.arrangeByKnowledgeTiers(graph, knowledge);
        } else {
            GraphUtils.circleLayout(graph, 200, 200, 150);
        }

        setResultGraph(SearchGraphUtils.patternForDag(graph, knowledge));
    }

    public boolean supportsKnowledge() {
        return true;
    }

    public ImpliedOrientation getMeekRules() {
        MeekRules rules = new MeekRules();
        rules.setKnowledge(params.getKnowledge());
        return rules;
    }

    public void setAlgorithmType(AlgorithmType algorithmType) {
        this.algorithmType = algorithmType;
    }

    private boolean isAggressivelyPreventCycles() {
        return params.isAggressivelyPreventCycles();
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

    /**
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        return new LinkedList<String>();
    }

    /**
     * @param node The node that the classifications are for. All triple from adjacencies to this node to adjacencies to
     *             this node through the given node will be considered.
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code> for the given node.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        return new LinkedList<List<Triple>>();
    }


    private List<PropertyChangeListener> getListeners() {
        if (listeners == null) {
            listeners = new ArrayList<PropertyChangeListener>();
        }
        return listeners;
    }


    public DataSet simulateDataCholesky(int sampleSize, TetradMatrix covar, List<Node> variableNodes) {
        List<Node> variables = new LinkedList<Node>();

        for (Node node : variableNodes) {
            variables.add(node);
        }

        List<Node> newVariables = new ArrayList<Node>();

        for (Node node : variables) {
            ContinuousVariable continuousVariable = new ContinuousVariable(node.getName());
            continuousVariable.setNodeType(node.getNodeType());
            newVariables.add(continuousVariable);
        }

        TetradMatrix impliedCovar = covar;

        DataSet fullDataSet = new ColtDataSet(sampleSize, newVariables);
        TetradMatrix cholesky = MatrixUtils.choleskyC(impliedCovar);

        // Simulate the data by repeatedly calling the Cholesky.exogenousData
        // method. Store only the data for the measured variables.
        ROW:
        for (int row = 0; row < sampleSize; row++) {

            // Step 1. Generate normal samples.
            double exoData[] = new double[cholesky.rows()];

            for (int i = 0; i < exoData.length; i++) {
                exoData[i] = RandomUtil.getInstance().nextNormal(0, 1);
                //            exoData[i] = randomUtil.nextUniform(-1, 1);
            }

            // Step 2. Multiply by cholesky to get correct covariance.
            double point[] = new double[exoData.length];

            for (int i = 0; i < exoData.length; i++) {
                double sum = 0.0;

                for (int j = 0; j <= i; j++) {
                    sum += cholesky.get(i, j) * exoData[j];
                }

                point[i] = sum;
            }

            double rowData[] = point;

            for (int col = 0; col < variables.size(); col++) {
                int index = variableNodes.indexOf(variables.get(col));
                double value = rowData[index];

                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    throw new IllegalArgumentException("Value out of range: " + value);
                }

                fullDataSet.setDouble(row, col, value);
            }
        }

        return DataUtils.restrictToMeasured(fullDataSet);
    }

    public void setOriginalSemIm(SemIm originalSemIm) {
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
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (params2 == null) {
            params2 = new PcSearchParams();
        }
    }

    public SemIm getOriginalSemIm() {
        return originalSemIm;
    }

    public SemIm getNewSemIm() {
        return newSemIm;
    }

    public void setNewSemIm(SemIm newSemIm) {
        this.newSemIm = newSemIm;
    }
}



