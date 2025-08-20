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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.session.DoNotAddOldModel;
import edu.cmu.tetradapp.session.SimulationParamsSource;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds a tetrad dag with all of the constructors necessary for it to serve as a model for the tetrad application.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SemGraphWrapper implements GraphSource,
        KnowledgeBoxInput, SimulationParamsSource, DoNotAddOldModel, MultipleGraphSource {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The number of models in this wrapper.
     */
    private int numModels = 1;

    /**
     * The index of the current model.
     */
    private int modelIndex;

    /**
     * The name of the model source.
     */
    private String modelSourceName;

    /**
     * The name of the wrapper.
     */
    private String name;

    /**
     * The graphs in the wrapper.
     */
    private List<Graph> graphs;

    /**
     * The parameter settings for the wrapper.
     */
    private Map<String, String> allParamSettings;

    /**
     * The parameters for the wrapper.
     */
    private Parameters parameters = new Parameters();

    // =============================CONSTRUCTORS==========================//

    /**
     * <p>Constructor for SemGraphWrapper.</p>
     *
     * @param graphSource a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param parameters  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SemGraphWrapper(GraphSource graphSource, Parameters parameters) {
        if (graphSource instanceof Simulation simulation) {
            List<Graph> graphs = simulation.getGraphs();
            this.graphs = new ArrayList<>();
            for (Graph graph : graphs) {
                this.graphs.add(new SemGraph(graph));
            }

            this.numModels = this.graphs.size();
            this.modelIndex = 0;
            this.modelSourceName = simulation.getName();
        } else {
            setGraph(new SemGraph(graphSource.getGraph()));
        }

        log();
    }

    /**
     * <p>Constructor for SemGraphWrapper.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.SemGraph} object
     */
    public SemGraphWrapper(SemGraph graph) {
        if (graph == null) {
            throw new NullPointerException("MAG must not be null.");
        }
        setSemGraph(graph);
        getSemGraph().setShowErrorTerms(false);
        this.parameters = new Parameters();
        log();
    }

    // Do not, repeat not, get rid of these params. -jdramsey 7/4/2010

    /**
     * <p>Constructor for SemGraphWrapper.</p>
     *
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SemGraphWrapper(Parameters params) {
        if (params.getString("newGraphInitializationMode", "manual").equals("manual")) {
            SemGraph semGraph = new SemGraph();
            semGraph.setShowErrorTerms(false);
            setSemGraph(semGraph);
        } else if (params.getString("newGraphInitializationMode", "manual").equals("random")) {
//            RandomUtil.getInstance().setSeed(new Date().getTime());
            setSemGraph(new SemGraph(edu.cmu.tetradapp.util.GraphUtils.makeRandomGraph(getGraph(), this.parameters)));
        } else {
//            RandomUtil.getInstance().setSeed(new Date().getTime());
            setSemGraph(new SemGraph(edu.cmu.tetradapp.util.GraphUtils.makeRandomGraph(getGraph(), this.parameters)));
        }

        this.parameters = params;
        log();
    }

    /**
     * <p>Constructor for SemGraphWrapper.</p>
     *
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.SemGraphWrapper} object
     * @param params       a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SemGraphWrapper(SemGraphWrapper graphWrapper, Parameters params) {
        this.parameters = params;
        if (params.getString("newGraphInitializationMode", "manual").equals("manual")) {
            try {
                SemGraph semGraph = new SemGraph(graphWrapper.getSemGraph());
                semGraph.setShowErrorTerms(false);
                setSemGraph(semGraph);
            } catch (Exception e) {
                e.printStackTrace();
                SemGraph semGraph = new SemGraph();
                semGraph.setShowErrorTerms(false);
                setSemGraph(semGraph);
            }
        } else if (params.getString("newGraphInitializationMode", "manual").equals("random")) {
//            RandomUtil.getInstance().setSeed(new Date().getTime());
            setSemGraph(new SemGraph(edu.cmu.tetradapp.util.GraphUtils.makeRandomGraph(getGraph(), this.parameters)));
        }
        log();
    }

    /**
     * <p>Constructor for SemGraphWrapper.</p>
     *
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.DagWrapper} object
     * @param params       a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SemGraphWrapper(DagWrapper graphWrapper, Parameters params) {
        this.parameters = params;
        if (params.getString("newGraphInitializationMode", "manual").equals("manual")) {
            SemGraph semGraph = new SemGraph(graphWrapper.getDag());
            semGraph.setShowErrorTerms(false);
            setSemGraph(semGraph);
        } else if (params.getString("newGraphInitializationMode", "manual").equals("random")) {
//            RandomUtil.getInstance().setSeed(new Date().getTime());
            setSemGraph(new SemGraph(edu.cmu.tetradapp.util.GraphUtils.makeRandomGraph(getGraph(), this.parameters)));
        }
        log();
    }

    /**
     * <p>Constructor for SemGraphWrapper.</p>
     *
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.GraphWrapper} object
     * @param params       a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SemGraphWrapper(GraphWrapper graphWrapper, Parameters params) {
        if (params.getString("newGraphInitializationMode", "manual").equals("manual")) {
            SemGraph semGraph = new SemGraph(graphWrapper.getGraph());
            semGraph.setShowErrorTerms(false);
            setSemGraph(semGraph);
        } else if (params.getString("newGraphInitializationMode", "manual").equals("random")) {
//            RandomUtil.getInstance().setSeed(new Date().getTime());
            setSemGraph(new SemGraph(edu.cmu.tetradapp.util.GraphUtils.makeRandomGraph(getGraph(), this.parameters)));
        }
        this.parameters = params;
        log();
    }

    /**
     * <p>Constructor for SemGraphWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.AbstractAlgorithmRunner} object
     */
    public SemGraphWrapper(AbstractAlgorithmRunner wrapper) {
        this(new SemGraph(wrapper.getResultGraph()));
    }

    /**
     * <p>Constructor for SemGraphWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     */
    public SemGraphWrapper(DataWrapper wrapper) {
        if (wrapper instanceof Simulation simulation) {
            this.graphs = new ArrayList<>();

            for (Graph graph : simulation.getGraphs()) {
                SemGraph semGraph = new SemGraph(graph);
                semGraph.setShowErrorTerms(false);
                this.graphs.add(semGraph);
            }

            this.numModels = this.graphs.size();
            this.modelIndex = 0;
            this.modelSourceName = simulation.getName();
        } else {
            setGraph(new EdgeListGraph(wrapper.getVariables()));
        }

        LayoutUtil.defaultLayout(getGraph());
    }

    /**
     * <p>Constructor for SemGraphWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesPmWrapper} object
     */
    public SemGraphWrapper(BayesPmWrapper wrapper) {
        this(new SemGraph(wrapper.getBayesPm().getDag()));
    }

    /**
     * <p>Constructor for SemGraphWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesImWrapper} object
     */
    public SemGraphWrapper(BayesImWrapper wrapper) {
        this(new SemGraph(wrapper.getBayesIm().getBayesPm().getDag()));
    }

    /**
     * <p>Constructor for SemGraphWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesEstimatorWrapper} object
     */
    public SemGraphWrapper(BayesEstimatorWrapper wrapper) {
        this(new SemGraph(wrapper.getEstimatedBayesIm().getBayesPm().getDag()));
    }

    /**
     * <p>Constructor for SemGraphWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.CptInvariantUpdaterWrapper} object
     */
    public SemGraphWrapper(CptInvariantUpdaterWrapper wrapper) {
        this(new SemGraph(wrapper.getBayesUpdater().getManipulatedGraph()));
    }

    /**
     * <p>Constructor for SemGraphWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemPmWrapper} object
     */
    public SemGraphWrapper(SemPmWrapper wrapper) {
        this(new SemGraph(wrapper.getSemPm().getGraph()));
    }

    /**
     * <p>Constructor for SemGraphWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemImWrapper} object
     */
    public SemGraphWrapper(SemImWrapper wrapper) {
        this(new SemGraph(wrapper.getSemIm().getSemPm().getGraph()));
    }

    /**
     * <p>Constructor for SemGraphWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemEstimatorWrapper} object
     */
    public SemGraphWrapper(SemEstimatorWrapper wrapper) {
        this(new SemGraph(wrapper.getSemEstimator().getEstimatedSem()
                .getSemPm().getGraph()));
    }

    /**
     * <p>Constructor for SemGraphWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.RegressionRunner} object
     */
    public SemGraphWrapper(RegressionRunner wrapper) {
        this(new SemGraph(wrapper.getResultGraph()));
    }

    /**
     * <p>Constructor for SemGraphWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BuildPureClustersRunner} object
     */
    public SemGraphWrapper(BuildPureClustersRunner wrapper) {
        this(new SemGraph(wrapper.getResultGraph()));
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.SemGraphWrapper} object
     * @see TetradSerializableUtils
     */
    public static SemGraphWrapper serializableInstance() {
        return new SemGraphWrapper(SemGraph.serializableInstance());
    }

    // ================================PUBLIC METHODS=======================//

    /**
     * <p>getSemGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.SemGraph} object
     */
    public SemGraph getSemGraph() {
        return (SemGraph) getGraph();
    }

    /**
     * <p>setSemGraph.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.SemGraph} object
     */
    public void setSemGraph(SemGraph graph) {
        this.graphs = new ArrayList<>();
        graph.setShowErrorTerms(false);
        this.graphs.add(graph);
        log();
    }

    // ============================PRIVATE METHODS========================//
    private void log() {
        TetradLogger.getInstance().log("Structural Equation Model (SEM) Graph");
        String message = "" + getGraph();
        TetradLogger.getInstance().log(message);
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
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return this.graphs.get(getModelIndex());
    }

    /**
     * <p>setGraph.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void setGraph(Graph graph) {
        this.graphs = new ArrayList<>();

        if (graph instanceof SemGraph) {
            this.graphs.add(graph);
        } else {
            this.graphs.add(new SemGraph(graph));
        }

//        this.graphs.add(new SemGraph(graph));
        log();
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
     * <p>getSourceGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getSourceGraph() {
        return getGraph();
    }

    /**
     * <p>getResultGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getResultGraph() {
        return getGraph();
    }

    /**
     * <p>getVariableNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getVariableNames() {
        return getGraph().getNodeNames();
    }

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariables() {
        return getGraph().getNodes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getParamSettings() {
        Map<String, String> paramSettings = new HashMap<>();
        if (!paramSettings.containsKey("# Vars")) {
            paramSettings.put("# Nodes", Integer.toString(getSemGraph().getNumNodes()));
        }
        paramSettings.put("# Edges", Integer.toString(getSemGraph().getNumEdges()));
        if (getSemGraph().paths().existsDirectedCycle()) {
            paramSettings.put("Cyclic", null);
        }
        return paramSettings;
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
        this.allParamSettings = paramSettings;
    }

    /**
     * <p>Getter for the field <code>parameters</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Parameters getParameters() {
        return this.parameters;
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
     * <p>Setter for the field <code>numModels</code>.</p>
     *
     * @param numModels a int
     */
    public void setNumModels(int numModels) {
        this.numModels = numModels;
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
     * <p>Setter for the field <code>modelSourceName</code>.</p>
     *
     * @param modelSourceName a {@link java.lang.String} object
     */
    public void setModelSourceName(String modelSourceName) {
        this.modelSourceName = modelSourceName;
    }

    /**
     * <p>Getter for the field <code>graphs</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Graph> getGraphs() {
        return this.graphs;
    }
}
