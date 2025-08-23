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

import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.rmi.MarshalledObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SemPmWrapper implements SessionModel {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The wrapped SemPm.
     */
    private int numModels = 1;

    /**
     * The index of the model to display.
     */
    private int modelIndex;

    /**
     * The name of the source of the model.
     */
    private String modelSourceName;

    /**
     * The name of the wrapper.
     */
    private String name;

    /**
     * The wrapped SemPm.
     */
    private List<SemPm> semPms;

    //==============================CONSTRUCTORS==========================//

    /**
     * <p>Constructor for SemPmWrapper.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public SemPmWrapper(Graph graph) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        this.semPms = new ArrayList<>();
        this.semPms.add(new SemPm(graph));

        for (int i = 0; i < this.semPms.size(); i++) {
            log(i, this.semPms.get(i));
        }
    }

    /**
     * Creates a new SemPm from the given workbench and uses it to construct a new BayesPm.
     *
     * @param simulation a {@link edu.cmu.tetradapp.model.Simulation} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SemPmWrapper(Simulation simulation, Parameters parameters) {
        List<SemIm> semIms = null;

        if (simulation == null) {
            throw new NullPointerException("The Simulation box does not contain a simulation.");
        }

        edu.cmu.tetrad.algcomparison.simulation.Simulation _simulation = simulation.getSimulation();

        if (_simulation == null) {
            throw new NullPointerException("No data sets have been simulated.");
        }

        if (_simulation instanceof LinearFisherModel) {
            throw new IllegalArgumentException("Large SEM simulations cannot be represented "
                                               + "using a SEM PM or IM box, sorry.");
        }

        if (!(_simulation instanceof SemSimulation)) {
            throw new IllegalArgumentException("That was not a linear, Gaussian SEM simulation.");
        }

        this.semPms = new ArrayList<>();

        this.numModels = simulation.getDataModelList().size();
        this.modelIndex = 0;
        this.modelSourceName = simulation.getName();
    }

    /**
     * Creates a new SemPm from the given workbench and uses it to construct a new BayesPm.
     *
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param parameters   a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SemPmWrapper(GraphSource graphWrapper, Parameters parameters) {
        this(graphWrapper.getGraph() instanceof TimeLagGraph
                ? new TimeLagGraph((TimeLagGraph) graphWrapper.getGraph())
                : new EdgeListGraph(graphWrapper.getGraph()));
    }

    /**
     * <p>Constructor for SemPmWrapper.</p>
     *
     * @param graphSource a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param parameters  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SemPmWrapper(GraphSource graphSource, DataWrapper dataWrapper, Parameters parameters) {
        this(new EdgeListGraph(graphSource.getGraph()));
    }

    /**
     * <p>Constructor for SemPmWrapper.</p>
     *
     * @param wrapper    a {@link edu.cmu.tetradapp.model.SemEstimatorWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SemPmWrapper(SemEstimatorWrapper wrapper, Parameters parameters) {
        SemPm oldSemPm = wrapper.getSemEstimator().getEstimatedSem()
                .getSemPm();
        setSemPm(oldSemPm);
    }

    /**
     * <p>Constructor for SemPmWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemImWrapper} object
     */
    public SemPmWrapper(SemImWrapper wrapper) {
        SemPm pm = wrapper.getSemIm().getSemPm();
        setSemPm(pm);

    }

    /**
     * <p>Constructor for SemPmWrapper.</p>
     *
     * @param simulation a {@link edu.cmu.tetradapp.model.Simulation} object
     */
    public SemPmWrapper(Simulation simulation) {
        List<Graph> graphs = simulation.getGraphs();

        if (!(graphs.size() == 1)) {
            throw new IllegalArgumentException("Simulation must contain exactly one graph/data pair.");
        }

        setSemPm(new SemPm(graphs.getFirst()));
    }

    /**
     * <p>Constructor for SemPmWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.AlgorithmRunner} object
     */
    public SemPmWrapper(AlgorithmRunner wrapper) {
        this(new EdgeListGraph(wrapper.getGraph()));
    }

    /**
     * <p>Constructor for SemPmWrapper.</p>
     *
     * @param wrapper a {@link DagFromCPDAGWrapper} object
     */
    public SemPmWrapper(DagFromCPDAGWrapper wrapper) {
        this(new EdgeListGraph(wrapper.getGraph()));
    }

    /**
     * <p>Constructor for SemPmWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.ScoredGraphsWrapper} object
     */
    public SemPmWrapper(ScoredGraphsWrapper wrapper) {
        this(new EdgeListGraph(wrapper.getGraph()));
    }

    /**
     * <p>Constructor for SemPmWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.PValueImproverWrapper} object
     */
    public SemPmWrapper(PValueImproverWrapper wrapper) {
        SemPm oldSemPm = wrapper.getNewSemIm().getSemPm();
        log(0, oldSemPm);

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.SemPmWrapper} object
     * @see TetradSerializableUtils
     */
    public static SemPmWrapper serializableInstance() {
        return new SemPmWrapper(Dag.serializableInstance());
    }

    //============================PUBLIC METHODS=========================//

    /**
     * <p>getSemPm.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.SemPm} object
     */
    public SemPm getSemPm() {
        return this.semPms.get(getModelIndex());
    }

    private void setSemPm(SemPm oldSemPm) {
        try {
            SemPm pm = (SemPm) new MarshalledObject(oldSemPm).get();
            this.semPms = Collections.singletonList(pm);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
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
        return this.semPms.get(this.modelIndex).getGraph();
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

    //======================= Private methods ====================//
    private void log(int i, SemPm pm) {
        TetradLogger.getInstance().log("Linear Structural Equation Parametric Model (SEM PM)");
        TetradLogger.getInstance().log("PM # " + (i + 1));
        String message = pm.toString();
        TetradLogger.getInstance().log(message);
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
        return getResultGraph();
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

    /**
     * The wrapped SemPm.
     *
     * @return a {@link java.util.List} object
     * @serial Cannot be null.
     */
    public List<SemPm> getSemPms() {
        return this.semPms;
    }
}
