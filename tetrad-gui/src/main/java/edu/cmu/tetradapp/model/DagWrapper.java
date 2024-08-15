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
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
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
 * Holds a tetrad dag with all the constructors necessary for it to serve as a model for the tetrad application.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class DagWrapper implements GraphSource, KnowledgeBoxInput, IndTestProducer,
        SimulationParamsSource, MultipleGraphSource {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The number of models.
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
     * The name of the model.
     */
    private String name;

    /**
     * The graph.
     */
    private List<Dag> dags;

    /**
     * The parameters.
     */
    private Map<String, String> allParamSettings;

    /**
     * The parameters.
     */
    private Parameters parameters;

    //=============================CONSTRUCTORS==========================//

    /**
     * <p>Constructor for DagWrapper.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Dag} object
     */
    public DagWrapper(Dag graph) {
        if (graph == null) {
            throw new NullPointerException("Tetrad dag must not be null.");
        }
        setGraph(graph);
        this.parameters = new Parameters();
        log();
    }

    // Do not, repeat not, get rid of these params. -jdramsey 7/4/2010

    /**
     * <p>Constructor for DagWrapper.</p>
     *
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public DagWrapper(Parameters params) {
        this.parameters = params;
        if (params.getString("newGraphInitializationMode", "manual").equals("manual")) {
            setDag(new Dag());
        } else if (params.getString("newGraphInitializationMode", "manual").equals("random")) {
//            RandomUtil.getInstance().setSeed(new Date().getTime());
            setDag(new Dag(edu.cmu.tetradapp.util.GraphUtils.makeRandomGraph(getGraph(), params)));
        }
        log();
    }

    /**
     * <p>Constructor for DagWrapper.</p>
     *
     * @param graphSource a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param parameters  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public DagWrapper(GraphSource graphSource, Parameters parameters) {
        this.parameters = new Parameters(parameters);

        if (graphSource instanceof Simulation simulation) {
            List<Graph> graphs = simulation.getGraphs();

            this.dags = new ArrayList<>();

            for (Graph graph : graphs) {
                this.dags.add(new Dag(graph));
            }

            this.numModels = this.dags.size();
            this.modelIndex = 0;
            this.modelSourceName = simulation.getName();
        } else {
            setGraph(new EdgeListGraph(graphSource.getGraph()));
        }

        log();
    }

    /**
     * <p>Constructor for DagWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.AbstractAlgorithmRunner} object
     */
    public DagWrapper(AbstractAlgorithmRunner wrapper) {
        this(new Dag(wrapper.getResultGraph()));
    }

    /**
     * <p>Constructor for DagWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     */
    public DagWrapper(DataWrapper wrapper) {
        if (wrapper instanceof Simulation simulation) {
            List<Graph> graphs = simulation.getGraphs();

            this.dags = new ArrayList<>();

            for (Graph graph : graphs) {
                this.dags.add(new Dag(graph));
            }

            this.numModels = this.dags.size();
            this.modelIndex = 0;
            this.modelSourceName = simulation.getName();
        } else {
            setGraph(new EdgeListGraph(wrapper.getVariables()));
        }

        LayoutUtil.defaultLayout(getGraph());
    }

    /**
     * <p>Constructor for DagWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesPmWrapper} object
     */
    public DagWrapper(BayesPmWrapper wrapper) {
        this(new Dag(wrapper.getBayesPm().getDag()));
    }

    /**
     * <p>Constructor for DagWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesImWrapper} object
     */
    public DagWrapper(BayesImWrapper wrapper) {
        this(new Dag(wrapper.getBayesIm().getBayesPm().getDag()));
    }

    /**
     * <p>Constructor for DagWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesEstimatorWrapper} object
     */
    public DagWrapper(BayesEstimatorWrapper wrapper) {
        this(new Dag(wrapper.getEstimatedBayesIm().getBayesPm().getDag()));
    }

    /**
     * <p>Constructor for DagWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.CptInvariantUpdaterWrapper} object
     */
    public DagWrapper(CptInvariantUpdaterWrapper wrapper) {
        this(new Dag(wrapper.getBayesUpdater().getManipulatedGraph()));
    }

    /**
     * <p>Constructor for DagWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemPmWrapper} object
     */
    public DagWrapper(SemPmWrapper wrapper) {
        this(new Dag(wrapper.getSemPm().getGraph()));
    }

    /**
     * <p>Constructor for DagWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemImWrapper} object
     */
    public DagWrapper(SemImWrapper wrapper) {
        this(new Dag(wrapper.getSemIm().getSemPm().getGraph()));
    }

    /**
     * <p>Constructor for DagWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemEstimatorWrapper} object
     */
    public DagWrapper(SemEstimatorWrapper wrapper) {
        this(new Dag(wrapper.getSemEstimator().getEstimatedSem().getSemPm()
                .getGraph()));
    }

    /**
     * <p>Constructor for DagWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.RegressionRunner} object
     */
    public DagWrapper(RegressionRunner wrapper) {
        this(new Dag(wrapper.getResultGraph()));
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.DagWrapper} object
     * @see TetradSerializableUtils
     */
    public static DagWrapper serializableInstance() {
        return new DagWrapper(Dag.serializableInstance());
    }

    //================================PUBLIC METHODS=======================//

    /**
     * <p>getDag.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getDag() {
        return this.dags.get(getModelIndex());
    }

    /**
     * <p>setDag.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Dag} object
     */
    public void setDag(Dag graph) {
        this.dags = new ArrayList<>();
        this.dags.add(graph);
        log();
    }

    //============================PRIVATE METHODS========================//
    private void log() {
        TetradLogger.getInstance().log("Directed Acyclic Graph (DAG)");
        String message = getGraph() + "";
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
        return getDag();
    }

    /**
     * <p>setGraph.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public void setGraph(Graph graph) {
        this.dags = new ArrayList<>();
        this.dags.add(new Dag(graph));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceTest getIndependenceTest() {
        return new MsepTest(getGraph());
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
        paramSettings.put("# Nodes", Integer.toString(getDag().getNumNodes()));
        paramSettings.put("# Edges", Integer.toString(getDag().getNumEdges()));
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
     * <p>getGraphs.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Graph> getGraphs() {
        return new ArrayList<>(this.dags);
    }
}
