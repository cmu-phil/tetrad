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

import edu.cmu.tetrad.algcomparison.simulation.BayesNetSimulation;
import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author Joseph Ramsey
 */
public class BayesPmWrapper implements SessionModel {
    static final long serialVersionUID = 23L;

    private int numModels = 1;
    private int modelIndex = 0;
    private String modelSourceName = null;

    /**
     * @serial Can be null.
     */
    private String name;

    private List<BayesPm> bayesPms;

    //==============================CONSTRUCTORS=========================//

    /**
     * Creates a new BayesPm from the given DAG and uses it to construct a new
     * BayesPm.
     */
    public BayesPmWrapper(final Graph graph, final Parameters params) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        final int lowerBound;
        final int upperBound;

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

    private void setBayesPm(final Graph graph, final int lowerBound, final int upperBound) {
        final BayesPm b = new BayesPm(graph, lowerBound, upperBound);
        setBayesPm(b);
    }

    private void setBayesPm(final BayesPm b) {
        this.bayesPms = new ArrayList<>();
        this.bayesPms.add(b);
    }

    public BayesPmWrapper(final Simulation simulation) {
        final List<BayesIm> bayesIms;

        if (simulation == null) {
            throw new NullPointerException("The Simulation box does not contain a simulation.");
        }

        final edu.cmu.tetrad.algcomparison.simulation.Simulation _simulation = simulation.getSimulation();

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

        final List<BayesPm> bayesPms = new ArrayList<>();

        for (final BayesIm bayesIm : bayesIms) {
            bayesPms.add(bayesIm.getBayesPm());
        }

        this.bayesPms = bayesPms;

        this.numModels = simulation.getDataModelList().size();
        this.modelIndex = 0;
        this.modelSourceName = simulation.getName();
    }

    public BayesPmWrapper(final Dag graph, final BayesPm bayesPm, final Parameters params) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        if (bayesPm == null) {
            throw new NullPointerException("BayesPm must not be null");
        }

        final int lowerBound;
        final int upperBound;

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
     * Creates a new BayesPm from the given workbench and uses it to construct a
     * new BayesPm.
     *
     * @throws RuntimeException If the parent graph cannot be converted into a
     *                          DAG.
     */
    public BayesPmWrapper(final GraphWrapper graphWrapper, final Parameters params) {
        if (graphWrapper == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        final Dag graph;

        try {
            graph = new Dag(graphWrapper.getGraph());
        } catch (final Exception e) {
            throw new RuntimeException(
                    "The parent graph cannot be converted to " + "a DAG.");
        }

        final int lowerBound;
        final int upperBound;

        if (params.getString("bayesPmInitializationMode", "range").equals("trinary")) {
            lowerBound = upperBound = 3;
            setBayesPm(graph, lowerBound, upperBound);
        } else if (params.getString("bayesPmInitializationMode", "range").equals("range")) {
            lowerBound = params.getInt("minCategories", 2);
            upperBound = params.getInt("maxCategories", 4);
            setBayesPm(graph, lowerBound, upperBound);
        } else {
            throw new IllegalStateException("Unrecognized type.");
        }
    }

    public BayesPmWrapper(final BayesEstimatorWrapper wrapper) {
        setBayesPm(new BayesPm(wrapper.getEstimatedBayesIm().getBayesPm()));
    }

    public BayesPmWrapper(final BayesImWrapper wrapper) {
        this.bayesPms = new ArrayList<>();

        for (int i = 0; i < wrapper.getNumModels(); i++) {
            wrapper.setModelIndex(i);
            this.bayesPms.add(wrapper.getBayesIm().getBayesPm());
        }

        this.numModels = wrapper.getNumModels();
    }

    public BayesPmWrapper(final GraphSource graphWrapper, final DataWrapper dataWrapper) {
        this(new Dag(graphWrapper.getGraph()), dataWrapper);
    }

    public BayesPmWrapper(final Graph graph, final DataWrapper dataWrapper) {
        final DataSet dataSet
                = (DataSet) dataWrapper.getSelectedDataModel();
        final List<Node> vars = dataSet.getVariables();

        final Map<String, DiscreteVariable> nodesToVars
                = new HashMap<>();
        for (int i = 0; i < dataSet.getNumColumns(); i++) {
            final DiscreteVariable var = (DiscreteVariable) vars.get(i);
            final String name = var.getName();
            final Node node = new GraphNode(name);
            nodesToVars.put(node.getName(), var);
        }

        final BayesPm bayesPm = new BayesPm(graph);
        final List<Node> nodes = bayesPm.getDag().getNodes();

        for (final Node node : nodes) {
            final Node var = nodesToVars.get(node.getName());

            if (var != null) {
                final DiscreteVariable var2 = nodesToVars.get(node.getName());
                final int numCategories = var2.getNumCategories();
                final List<String> categories = new ArrayList<>();
                for (int j = 0; j < numCategories; j++) {
                    categories.add(var2.getCategory(j));
                }
                bayesPm.setCategories(node, categories);
            }
        }

        setBayesPm(bayesPm);
    }

    public BayesPmWrapper(final GraphWrapper graphWrapper,
                          final Simulation simulation) {
        this(graphWrapper, (DataWrapper) simulation);
    }

    public BayesPmWrapper(final AlgorithmRunner wrapper, final Parameters params) {
        this(new Dag(wrapper.getGraph()), params);
    }

    public BayesPmWrapper(final AlgorithmRunner wrapper, final DataWrapper dataWrapper) {
        this(new Dag(wrapper.getGraph()), dataWrapper);
    }

    public BayesPmWrapper(final AlgorithmRunner wrapper, final Simulation simulation) {
        this(new Dag(wrapper.getGraph()), simulation);
    }

    public BayesPmWrapper(final BayesEstimatorWrapper wrapper, final Simulation simulation) {
        this(new Dag(wrapper.getGraph()), simulation);
    }

    public BayesPmWrapper(final BayesEstimatorWrapper wrapper,
                          final DataWrapper dataWrapper) {
        this(new Dag(wrapper.getGraph()), dataWrapper);
    }

    /**
     * Creates a new BayesPm from the given workbench and uses it to construct a
     * new BayesPm.
     *
     * @throws RuntimeException If the parent graph cannot be converted into a
     *                          DAG.
     */
    public BayesPmWrapper(final DagWrapper dagWrapper, final Parameters params) {
        if (dagWrapper == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        final Dag graph;

        try {
            graph = new Dag(dagWrapper.getDag());
        } catch (final Exception e) {
            throw new RuntimeException(
                    "The parent graph cannot be converted to " + "a DAG.");
        }

        final int lowerBound;
        final int upperBound;

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

    public BayesPmWrapper(final DagWrapper dagWrapper,
                          final BayesPmWrapper oldBayesPmWrapper, final Parameters params) {
        try {
            if (dagWrapper == null) {
                throw new NullPointerException("Graph must not be null.");
            }

            if (oldBayesPmWrapper == null) {
                throw new NullPointerException("BayesPm must not be null");
            }

            final Graph graph = dagWrapper.getDag();

            final int lowerBound;
            final int upperBound;

            final String string = params.getString("bayesPmInitializationMode", "trinary");

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
        } catch (final Exception e) {
            throw new RuntimeException(
                    "The parent graph cannot be converted to " + "a DAG.");
        }
    }

    public BayesPmWrapper(final DagWrapper dagWrapper, final DataWrapper dataWrapper) {
        final DataSet dataSet
                = (DataSet) dataWrapper.getSelectedDataModel();
        final List<Node> vars = dataSet.getVariables();
        final Map<String, DiscreteVariable> nodesToVars
                = new HashMap<>();
        for (int i = 0; i < dataSet.getNumColumns(); i++) {
            final DiscreteVariable var = (DiscreteVariable) vars.get(i);
            final String name = var.getName();
            final Node node = new GraphNode(name);
            nodesToVars.put(node.getName(), var);
        }

        final Dag graph = new Dag(dagWrapper.getDag());
        final BayesPm bayesPm = new BayesPm(graph);
        final List<Node> nodes = bayesPm.getDag().getNodes();

        for (final Node node : nodes) {
            final Node var = nodesToVars.get(node.getName());

            if (var != null) {
                final DiscreteVariable var2 = nodesToVars.get(node.getName());
                final int numCategories = var2.getNumCategories();
                final List<String> categories = new ArrayList<>();
                for (int j = 0; j < numCategories; j++) {
                    categories.add(var2.getCategory(j));
                }
                bayesPm.setCategories(node, categories);
            }
        }

        setBayesPm(bayesPm);
    }

    public BayesPmWrapper(final DagWrapper dagWrapper, final Simulation dataWrapper) {
        this(dagWrapper, (DataWrapper) dataWrapper);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static BayesPmWrapper serializableInstance() {
        return new BayesPmWrapper(Dag.serializableInstance(), new Parameters());
    }

    //=============================PUBLIC METHODS========================//
    public BayesPm getBayesPm() {
        return this.bayesPms.get(getModelIndex());
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
     */
    private void readObject(final ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    public Graph getGraph() {
        return getBayesPm().getDag();
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    //================================= Private Methods ==================================//
    private void log(final BayesPm pm) {
        TetradLogger.getInstance().log("info", "Bayes Parametric Model (Bayes PM)");
        TetradLogger.getInstance().log("pm", pm.toString());

    }

    public Graph getSourceGraph() {
        return getGraph();
    }

    public Graph getResultGraph() {
        return getGraph();
    }

    public List<String> getVariableNames() {
        return getGraph().getNodeNames();
    }

    public List<Node> getVariables() {
        return getGraph().getNodes();
    }

    public int getNumModels() {
        return this.numModels;
    }

    public int getModelIndex() {
        return this.modelIndex;
    }

    public String getModelSourceName() {
        return this.modelSourceName;
    }

    public void setModelIndex(final int modelIndex) {
        this.modelIndex = modelIndex;
    }
}
