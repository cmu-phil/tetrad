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

import edu.cmu.tetrad.algcomparison.simulation.BayesNetSimulation;
import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BayesPmWrapper implements SessionModel {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The number of models.
     */
    private int numModels = 1;

    /**
     * The index of the model.
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
     * The Bayes Pm.
     */
    private List<BayesPm> bayesPms;

    //==============================CONSTRUCTORS=========================//

    /**
     * Creates a new BayesPm from the given DAG and uses it to construct a new BayesPm.
     *
     * @param graph  a {@link edu.cmu.tetrad.graph.Graph} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public BayesPmWrapper(Graph graph, Parameters params) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        int lowerBound;
        int upperBound;

        if (params.getString("initializationMode", "trinary").equals("trinary")) {
            lowerBound = upperBound = 3;
            setBayesPm(graph, lowerBound, upperBound);
        } else if (params.getString("initializationMode", "trinary").equals("range")) {
            lowerBound = params.getInt("minCategories", 2);
            upperBound = params.getInt("maxCategories", 4);
            setBayesPm(graph, lowerBound, upperBound);
        } else {
            throw new IllegalStateException("Unrecognized type.");
        }
    }

    /**
     * <p>Constructor for BayesPmWrapper.</p>
     *
     * @param simulation a {@link edu.cmu.tetradapp.model.Simulation} object
     */
    public BayesPmWrapper(Simulation simulation) {
        List<BayesIm> bayesIms;

        if (simulation == null) {
            throw new NullPointerException("The Simulation box does not contain a simulation.");
        }

        edu.cmu.tetrad.algcomparison.simulation.Simulation _simulation = simulation.getSimulation();

        if (_simulation == null) {
            throw new NullPointerException("No data sets have been simulated.");
        }

        if (!(_simulation instanceof BayesNetSimulation)) {
            throw new IllegalArgumentException("That was not a discrete Bayes net simulation.");
        }

        bayesIms = ((BayesNetSimulation) _simulation).getBayesIms();

        if (bayesIms == null) {
            throw new NullPointerException("It looks like you have not done a simulation.");
        }

        List<BayesPm> bayesPms = new ArrayList<>();

        for (BayesIm bayesIm : bayesIms) {
            bayesPms.add(bayesIm.getBayesPm());
        }

        this.bayesPms = bayesPms;

        this.numModels = simulation.getDataModelList().size();
        this.modelIndex = 0;
        this.modelSourceName = simulation.getName();
    }

    /**
     * <p>Constructor for BayesPmWrapper.</p>
     *
     * @param graph   a {@link edu.cmu.tetrad.graph.Dag} object
     * @param bayesPm a {@link edu.cmu.tetrad.bayes.BayesPm} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public BayesPmWrapper(Dag graph, BayesPm bayesPm, Parameters params) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        if (bayesPm == null) {
            throw new NullPointerException("BayesPm must not be null");
        }

        int lowerBound;
        int upperBound;

        if (params.getString("initializationMode", "trinary").equals("trinary")) {
            lowerBound = upperBound = 3;
            setBayesPm(new BayesPm(graph, bayesPm, lowerBound, upperBound));
        } else if (params.getString("initializationMode", "trinary").equals("range")) {
            lowerBound = params.getInt("minCategories", 2);
            upperBound = params.getInt("maxCategories", 4);
            setBayesPm(graph, lowerBound, upperBound);
        } else {
            throw new IllegalStateException("Unrecognized type.");
        }
        log(bayesPm);
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a new BayesPm.
     *
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.GraphWrapper} object
     * @param params       a {@link edu.cmu.tetrad.util.Parameters} object
     * @throws java.lang.RuntimeException If the parent graph cannot be converted into a DAG.
     */
    public BayesPmWrapper(GraphWrapper graphWrapper, Parameters params) {
        if (graphWrapper == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        Dag graph;

        try {
            graph = new Dag(graphWrapper.getGraph());
        } catch (Exception e) {
            throw new RuntimeException(
                    "The parent graph cannot be converted to " + "a DAG.");
        }

        int lowerBound;
        int upperBound;

        if (params.getString("bayesPmInitializationMode", "range").equals("trinary")) {
            lowerBound = upperBound = 3;
            setBayesPm(graph, lowerBound, upperBound);
        } else if (params.getString("bayesPmInitializationMode", "range").equals("range")) {
            lowerBound = params.getInt("lowerBoundNumVals", 2);
            upperBound = params.getInt("upperBoundNumVals", 4);
            setBayesPm(graph, lowerBound, upperBound);
        } else {
            throw new IllegalStateException("Unrecognized type.");
        }
    }

    /**
     * <p>Constructor for BayesPmWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesEstimatorWrapper} object
     */
    public BayesPmWrapper(BayesEstimatorWrapper wrapper) {
        setBayesPm(new BayesPm(wrapper.getEstimatedBayesIm().getBayesPm()));
    }

    /**
     * <p>Constructor for BayesPmWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesImWrapper} object
     */
    public BayesPmWrapper(BayesImWrapper wrapper) {
        this.bayesPms = new ArrayList<>();

        for (int i = 0; i < wrapper.getNumModels(); i++) {
            wrapper.setModelIndex(i);
            this.bayesPms.add(wrapper.getBayesIm().getBayesPm());
        }

        this.numModels = wrapper.getNumModels();
    }

    /**
     * <p>Constructor for BayesPmWrapper.</p>
     *
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param dataWrapper  a {@link edu.cmu.tetradapp.model.DataWrapper} object
     */
    public BayesPmWrapper(GraphSource graphWrapper, DataWrapper dataWrapper) {
        this(new Dag(graphWrapper.getGraph()), dataWrapper);
    }

    /**
     * <p>Constructor for BayesPmWrapper.</p>
     *
     * @param graph       a {@link edu.cmu.tetrad.graph.Graph} object
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     */
    public BayesPmWrapper(Graph graph, DataWrapper dataWrapper) {
        DataSet dataSet
                = (DataSet) dataWrapper.getSelectedDataModel();
        List<Node> vars = dataSet.getVariables();

        Map<String, DiscreteVariable> nodesToVars
                = new HashMap<>();
        for (int i = 0; i < dataSet.getNumColumns(); i++) {
            DiscreteVariable var = (DiscreteVariable) vars.get(i);
            String name = var.getName();
            Node node = new GraphNode(name);
            nodesToVars.put(node.getName(), var);
        }

        BayesPm bayesPm = new BayesPm(graph);
        List<Node> nodes = bayesPm.getDag().getNodes();

        for (Node node : nodes) {
            Node var = nodesToVars.get(node.getName());

            if (var != null) {
                DiscreteVariable var2 = nodesToVars.get(node.getName());
                int numCategories = var2.getNumCategories();
                List<String> categories = new ArrayList<>();
                for (int j = 0; j < numCategories; j++) {
                    categories.add(var2.getCategory(j));
                }
                bayesPm.setCategories(node, categories);
            }
        }

        setBayesPm(bayesPm);
    }

    /**
     * <p>Constructor for BayesPmWrapper.</p>
     *
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.GraphWrapper} object
     * @param simulation   a {@link edu.cmu.tetradapp.model.Simulation} object
     */
    public BayesPmWrapper(GraphWrapper graphWrapper,
                          Simulation simulation) {
        this(graphWrapper, (DataWrapper) simulation);
    }

    /**
     * <p>Constructor for BayesPmWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.AlgorithmRunner} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public BayesPmWrapper(AlgorithmRunner wrapper, Parameters params) {
        this(new Dag(wrapper.getGraph()), params);
    }

    /**
     * <p>Constructor for BayesPmWrapper.</p>
     *
     * @param wrapper     a {@link edu.cmu.tetradapp.model.AlgorithmRunner} object
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     */
    public BayesPmWrapper(AlgorithmRunner wrapper, DataWrapper dataWrapper) {
        this(new Dag(wrapper.getGraph()), dataWrapper);
    }

    /**
     * <p>Constructor for BayesPmWrapper.</p>
     *
     * @param wrapper    a {@link edu.cmu.tetradapp.model.AlgorithmRunner} object
     * @param simulation a {@link edu.cmu.tetradapp.model.Simulation} object
     */
    public BayesPmWrapper(AlgorithmRunner wrapper, Simulation simulation) {
        this(new Dag(wrapper.getGraph()), simulation);
    }

    /**
     * <p>Constructor for BayesPmWrapper.</p>
     *
     * @param wrapper    a {@link edu.cmu.tetradapp.model.BayesEstimatorWrapper} object
     * @param simulation a {@link edu.cmu.tetradapp.model.Simulation} object
     */
    public BayesPmWrapper(BayesEstimatorWrapper wrapper, Simulation simulation) {
        this(new Dag(wrapper.getGraph()), simulation);
    }

    /**
     * <p>Constructor for BayesPmWrapper.</p>
     *
     * @param wrapper     a {@link edu.cmu.tetradapp.model.BayesEstimatorWrapper} object
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     */
    public BayesPmWrapper(BayesEstimatorWrapper wrapper,
                          DataWrapper dataWrapper) {
        this(new Dag(wrapper.getGraph()), dataWrapper);
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a new BayesPm.
     *
     * @param dagWrapper a {@link edu.cmu.tetradapp.model.DagWrapper} object
     * @param params     a {@link edu.cmu.tetrad.util.Parameters} object
     * @throws java.lang.RuntimeException If the parent graph cannot be converted into a DAG.
     */
    public BayesPmWrapper(DagWrapper dagWrapper, Parameters params) {
        if (dagWrapper == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        Dag graph;

        try {
            graph = new Dag(dagWrapper.getDag());
        } catch (Exception e) {
            throw new RuntimeException(
                    "The parent graph cannot be converted to " + "a DAG.");
        }

        int lowerBound;
        int upperBound;

        if (params.getString("bayesPmInitializationMode", "trinary").equals("trinary")) {
            lowerBound = upperBound = 3;
        } else if (params.getString("bayesPmInitializationMode", "trinary").equals("range")) {
            lowerBound = params.getInt("minCategories", 2);
            upperBound = params.getInt("maxCategories", 4);
        } else {
            throw new IllegalStateException("Unrecognized type.");
        }

        setBayesPm(graph, lowerBound, upperBound);
    }

    /**
     * <p>Constructor for BayesPmWrapper.</p>
     *
     * @param dagWrapper        a {@link edu.cmu.tetradapp.model.DagWrapper} object
     * @param oldBayesPmWrapper a {@link edu.cmu.tetradapp.model.BayesPmWrapper} object
     * @param params            a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public BayesPmWrapper(DagWrapper dagWrapper,
                          BayesPmWrapper oldBayesPmWrapper, Parameters params) {
        try {
            if (dagWrapper == null) {
                throw new NullPointerException("Graph must not be null.");
            }

            if (oldBayesPmWrapper == null) {
                throw new NullPointerException("BayesPm must not be null");
            }

            Graph graph = dagWrapper.getDag();

            int lowerBound;
            int upperBound;

            String string = params.getString("bayesPmInitializationMode", "trinary");

            if (string.equals("trinary")) {
                lowerBound = upperBound = 3;
                setBayesPm(new BayesPm(graph,
                        oldBayesPmWrapper.getBayesPm(), lowerBound, upperBound));
            } else if (string.equals("range")) {
                lowerBound = params.getInt("minCategories", 2);
                upperBound = params.getInt("maxCategories", 4);
                setBayesPm(graph, lowerBound, upperBound);
            } else {
                throw new IllegalStateException("Unrecognized type.");
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "The parent graph cannot be converted to " + "a DAG.");
        }
    }

    /**
     * <p>Constructor for BayesPmWrapper.</p>
     *
     * @param dagWrapper  a {@link edu.cmu.tetradapp.model.DagWrapper} object
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     */
    public BayesPmWrapper(DagWrapper dagWrapper, DataWrapper dataWrapper) {
        DataSet dataSet
                = (DataSet) dataWrapper.getSelectedDataModel();
        List<Node> vars = dataSet.getVariables();
        Map<String, DiscreteVariable> nodesToVars
                = new HashMap<>();
        for (int i = 0; i < dataSet.getNumColumns(); i++) {
            DiscreteVariable var = (DiscreteVariable) vars.get(i);
            String name = var.getName();
            Node node = new GraphNode(name);
            nodesToVars.put(node.getName(), var);
        }

        Dag graph = new Dag(dagWrapper.getDag());
        BayesPm bayesPm = new BayesPm(graph);
        List<Node> nodes = bayesPm.getDag().getNodes();

        for (Node node : nodes) {
            Node var = nodesToVars.get(node.getName());

            if (var != null) {
                DiscreteVariable var2 = nodesToVars.get(node.getName());
                int numCategories = var2.getNumCategories();
                List<String> categories = new ArrayList<>();
                for (int j = 0; j < numCategories; j++) {
                    categories.add(var2.getCategory(j));
                }
                bayesPm.setCategories(node, categories);
            }
        }

        setBayesPm(bayesPm);
    }

    /**
     * <p>Constructor for BayesPmWrapper.</p>
     *
     * @param dagWrapper  a {@link edu.cmu.tetradapp.model.DagWrapper} object
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.Simulation} object
     */
    public BayesPmWrapper(DagWrapper dagWrapper, Simulation dataWrapper) {
        this(dagWrapper, (DataWrapper) dataWrapper);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.BayesPmWrapper} object
     * @see TetradSerializableUtils
     */
    public static BayesPmWrapper serializableInstance() {
        return new BayesPmWrapper(Dag.serializableInstance(), new Parameters());
    }

    private void setBayesPm(Graph graph, int lowerBound, int upperBound) {
        BayesPm b = new BayesPm(graph, lowerBound, upperBound);
        setBayesPm(b);
    }

    //=============================PUBLIC METHODS========================//

    /**
     * <p>getBayesPm.</p>
     *
     * @return a {@link edu.cmu.tetrad.bayes.BayesPm} object
     */
    public BayesPm getBayesPm() {
        return this.bayesPms.get(getModelIndex());
    }

    private void setBayesPm(BayesPm b) {
        this.bayesPms = new ArrayList<>();
        this.bayesPms.add(b);
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s a {@link java.io.ObjectInputStream} object
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return getBayesPm().getDag();
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

    //================================= Private Methods ==================================//
    private void log(BayesPm pm) {
        TetradLogger.getInstance().forceLogMessage("Bayes Parametric Model (Bayes PM)");
        String message = pm.toString();
        TetradLogger.getInstance().forceLogMessage(message);

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
}
