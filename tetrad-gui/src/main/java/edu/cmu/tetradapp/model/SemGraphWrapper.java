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

import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.session.DoNotAddOldModel;
import edu.cmu.tetrad.session.SimulationParamsSource;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * Holds a tetrad dag with all of the constructors necessary for it to serve as
 * a model for the tetrad application.
 *
 * @author Joseph Ramsey
 */
public class SemGraphWrapper implements GraphSource,
        KnowledgeBoxInput, SimulationParamsSource, DoNotAddOldModel, MultipleGraphSource {

    static final long serialVersionUID = 23L;
    private int numModels = 1;
    private int modelIndex;
    private String modelSourceName;

    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * @serial Cannot be null.
     */
    private List<Graph> graphs;
    private Map<String, String> allParamSettings;
    private Parameters parameters = new Parameters();

    // =============================CONSTRUCTORS==========================//
    public SemGraphWrapper(GraphSource graphSource, Parameters parameters) {
        if (graphSource instanceof Simulation) {
            Simulation simulation = (Simulation) graphSource;
            List<Graph> graphs = simulation.getGraphs();
            this.graphs = new ArrayList<>();
            for (Graph graph : graphs) {
                this.graphs.add(new SemGraph(graph));
            }

            numModels = this.graphs.size();
            modelIndex = 0;
            modelSourceName = simulation.getName();
        } else {
            this.setGraph(new SemGraph(graphSource.getGraph()));
        }

        this.log();
    }

    public SemGraphWrapper(SemGraph graph) {
        if (graph == null) {
            throw new NullPointerException("MAG must not be null.");
        }
        this.setSemGraph(graph);
        this.getSemGraph().setShowErrorTerms(false);
        parameters = new Parameters();
        this.log();
    }

    // Do not, repeat not, get rid of these params. -jdramsey 7/4/2010
    public SemGraphWrapper(Parameters params) {
        if (params.getString("newGraphInitializationMode", "manual").equals("manual")) {
            SemGraph semGraph = new SemGraph();
            semGraph.setShowErrorTerms(false);
            this.setSemGraph(semGraph);
        } else if (params.getString("newGraphInitializationMode", "manual").equals("random")) {
            RandomUtil.getInstance().setSeed(new Date().getTime());
            this.setSemGraph(new SemGraph(edu.cmu.tetradapp.util.GraphUtils.makeRandomGraph(this.getGraph(), parameters)));
        } else {
            RandomUtil.getInstance().setSeed(new Date().getTime());
            this.setSemGraph(new SemGraph(edu.cmu.tetradapp.util.GraphUtils.makeRandomGraph(this.getGraph(), parameters)));
        }

        parameters = params;
        this.log();
    }

    public SemGraphWrapper(SemGraphWrapper graphWrapper, Parameters params) {
        parameters = params;
        if (params.getString("newGraphInitializationMode", "manual").equals("manual")) {
            try {
                SemGraph semGraph = new SemGraph(graphWrapper.getSemGraph());
                semGraph.setShowErrorTerms(false);
                this.setSemGraph(semGraph);
            } catch (Exception e) {
                e.printStackTrace();
                SemGraph semGraph = new SemGraph();
                semGraph.setShowErrorTerms(false);
                this.setSemGraph(semGraph);
            }
        } else if (params.getString("newGraphInitializationMode", "manual").equals("random")) {
            RandomUtil.getInstance().setSeed(new Date().getTime());
            this.setSemGraph(new SemGraph(edu.cmu.tetradapp.util.GraphUtils.makeRandomGraph(this.getGraph(), parameters)));
        }
        this.log();
    }

    public SemGraphWrapper(DagWrapper graphWrapper, Parameters params) {
        parameters = params;
        if (params.getString("newGraphInitializationMode", "manual").equals("manual")) {
            SemGraph semGraph = new SemGraph(graphWrapper.getDag());
            semGraph.setShowErrorTerms(false);
            this.setSemGraph(semGraph);
        } else if (params.getString("newGraphInitializationMode", "manual").equals("random")) {
            RandomUtil.getInstance().setSeed(new Date().getTime());
            this.setSemGraph(new SemGraph(edu.cmu.tetradapp.util.GraphUtils.makeRandomGraph(this.getGraph(), parameters)));
        }
        this.log();
    }

    public SemGraphWrapper(GraphWrapper graphWrapper, Parameters params) {
        if (params.getString("newGraphInitializationMode", "manual").equals("manual")) {
            SemGraph semGraph = new SemGraph(graphWrapper.getGraph());
            semGraph.setShowErrorTerms(false);
            this.setSemGraph(semGraph);
        } else if (params.getString("newGraphInitializationMode", "manual").equals("random")) {
            RandomUtil.getInstance().setSeed(new Date().getTime());
            this.setSemGraph(new SemGraph(edu.cmu.tetradapp.util.GraphUtils.makeRandomGraph(this.getGraph(), parameters)));
        }
        parameters = params;
        this.log();
    }

    public SemGraphWrapper(AbstractAlgorithmRunner wrapper) {
        this(new SemGraph(wrapper.getResultGraph()));
    }

    public SemGraphWrapper(DataWrapper wrapper) {
        if (wrapper instanceof Simulation) {
            Simulation simulation = (Simulation) wrapper;
            graphs = new ArrayList<>();

            for (Graph graph : simulation.getGraphs()) {
                SemGraph semGraph = new SemGraph(graph);
                semGraph.setShowErrorTerms(false);
                graphs.add(semGraph);
            }

            numModels = graphs.size();
            modelIndex = 0;
            modelSourceName = simulation.getName();
        } else {
            this.setGraph(new EdgeListGraph(wrapper.getVariables()));
        }

        GraphUtils.circleLayout(this.getGraph(), 200, 200, 150);
    }

    public SemGraphWrapper(BayesPmWrapper wrapper) {
        this(new SemGraph(wrapper.getBayesPm().getDag()));
    }

    public SemGraphWrapper(BayesImWrapper wrapper) {
        this(new SemGraph(wrapper.getBayesIm().getBayesPm().getDag()));
    }

    public SemGraphWrapper(BayesEstimatorWrapper wrapper) {
        this(new SemGraph(wrapper.getEstimatedBayesIm().getBayesPm().getDag()));
    }

    public SemGraphWrapper(CptInvariantUpdaterWrapper wrapper) {
        this(new SemGraph(wrapper.getBayesUpdater().getManipulatedGraph()));
    }

    public SemGraphWrapper(SemPmWrapper wrapper) {
        this(new SemGraph(wrapper.getSemPm().getGraph()));
    }

    public SemGraphWrapper(SemImWrapper wrapper) {
        this(new SemGraph(wrapper.getSemIm().getSemPm().getGraph()));
    }

    public SemGraphWrapper(SemEstimatorWrapper wrapper) {
        this(new SemGraph(wrapper.getSemEstimator().getEstimatedSem()
                .getSemPm().getGraph()));
    }

    public SemGraphWrapper(RegressionRunner wrapper) {
        this(new SemGraph(wrapper.getResultGraph()));
    }

    public SemGraphWrapper(BuildPureClustersRunner wrapper) {
        this(new SemGraph(wrapper.getResultGraph()));
    }

    public SemGraphWrapper(MimBuildRunner wrapper) {
        this(new SemGraph(wrapper.getResultGraph()));
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static SemGraphWrapper serializableInstance() {
        return new SemGraphWrapper(SemGraph.serializableInstance());
    }

    // ================================PUBLIC METHODS=======================//
    public SemGraph getSemGraph() {
        return (SemGraph) this.getGraph();
    }

    public void setSemGraph(SemGraph graph) {
        graphs = new ArrayList<>();
        graph.setShowErrorTerms(false);
        graphs.add(graph);
        this.log();
    }

    // ============================PRIVATE METHODS========================//
    private void log() {
        TetradLogger.getInstance().log("info", "Structural Equation Model (SEM) Graph");
        TetradLogger.getInstance().log("graph", "" + this.getGraph());
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
    private void readObject(ObjectInputStream s) throws IOException,
            ClassNotFoundException {
        s.defaultReadObject();
    }

    public Graph getGraph() {
        return graphs.get(this.getModelIndex());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Graph getSourceGraph() {
        return this.getGraph();
    }

    public Graph getResultGraph() {
        return this.getGraph();
    }

    public List<String> getVariableNames() {
        return this.getGraph().getNodeNames();
    }

    public List<Node> getVariables() {
        return this.getGraph().getNodes();
    }

    @Override
    public Map<String, String> getParamSettings() {
        Map<String, String> paramSettings = new HashMap<>();
        if (!paramSettings.containsKey("# Vars")) {
            paramSettings.put("# Nodes", Integer.toString(this.getSemGraph().getNumNodes()));
        }
        paramSettings.put("# Edges", Integer.toString(this.getSemGraph().getNumEdges()));
        if (this.getSemGraph().existsDirectedCycle()) {
            paramSettings.put("Cyclic", null);
        }
        return paramSettings;
    }

    @Override
    public void setAllParamSettings(Map<String, String> paramSettings) {
        allParamSettings = paramSettings;
    }

    @Override
    public Map<String, String> getAllParamSettings() {
        return allParamSettings;
    }

    public Parameters getParameters() {
        return parameters;
    }

    public int getNumModels() {
        return numModels;
    }

    public void setNumModels(int numModels) {
        this.numModels = numModels;
    }

    public int getModelIndex() {
        return modelIndex;
    }

    public void setModelIndex(int modelIndex) {
        this.modelIndex = modelIndex;
    }

    public String getModelSourceName() {
        return modelSourceName;
    }

    public void setModelSourceName(String modelSourceName) {
        this.modelSourceName = modelSourceName;
    }

    public void setGraph(Graph graph) {
        graphs = new ArrayList<>();
        graphs.add(new SemGraph(graph));
        this.log();
    }

    public List<Graph> getGraphs() {
        return graphs;
    }
}
