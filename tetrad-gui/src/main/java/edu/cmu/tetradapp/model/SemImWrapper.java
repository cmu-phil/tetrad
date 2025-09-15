///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SemImWrapper implements SessionModel {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The wrapped SemIms.
     */
    private List<SemIm> semIms;

    /**
     * The name of the model.
     */
    private String name;

    /**
     * The number of models in the list.
     */
    private int numModels = 1;

    /**
     * The index of the current model.
     */
    private int modelIndex;

    /**
     * The name of the source of the models.
     */
    private String modelSourceName;

    //============================CONSTRUCTORS==========================//

    /**
     * <p>Constructor for SemImWrapper.</p>
     *
     * @param semIm a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    public SemImWrapper(SemIm semIm) {
        setSemIm(semIm);
    }

    /**
     * <p>Constructor for SemImWrapper.</p>
     *
     * @param simulation a {@link edu.cmu.tetradapp.model.Simulation} object
     */
    public SemImWrapper(Simulation simulation) {
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
            throw new IllegalArgumentException("That was not a linear, Gaussian SEM simulation. Sorry.");
        }

        this.semIms = ((SemSimulation) _simulation).getIms();

        if (this.semIms == null) {
            throw new NullPointerException("It looks like you have not done a simulation.");
        }

        this.numModels = simulation.getDataModelList().size();
        this.modelIndex = 0;
        this.modelSourceName = simulation.getName();
    }

    /**
     * <p>Constructor for SemImWrapper.</p>
     *
     * @param semPmWrapper a {@link edu.cmu.tetradapp.model.SemPmWrapper} object
     * @param params       a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SemImWrapper(SemPmWrapper semPmWrapper, Parameters params) {
        if (semPmWrapper == null) {
            throw new NullPointerException("SemPmWrapper must not be null.");
        }

        setSemIm(new SemIm(semPmWrapper.getSemPms().get(semPmWrapper.getModelIndex()), params));
    }

    /**
     * <p>Constructor for SemImWrapper.</p>
     *
     * @param semPmWrapper    a {@link edu.cmu.tetradapp.model.SemPmWrapper} object
     * @param oldSemImWrapper a {@link edu.cmu.tetradapp.model.SemImWrapper} object
     * @param params          a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SemImWrapper(SemPmWrapper semPmWrapper, SemImWrapper oldSemImWrapper,
                        Parameters params) {
        if (semPmWrapper == null) {
            throw new NullPointerException("SemPmWrapper must not be null.");
        }

        if (params == null) {
            throw new NullPointerException("Parameters must not be null.");
        }

        SemPm semPm = new SemPm(semPmWrapper.getSemPm());
        SemIm oldSemIm = oldSemImWrapper.getSemIm();

        if (!params.getBoolean("retainPreviousValues", false)) {
            setSemIm(new SemIm(semPm, params));
        } else {
            setSemIm(new SemIm(semPm, oldSemIm, params));
        }
    }

    /**
     * <p>Constructor for SemImWrapper.</p>
     *
     * @param semUpdaterWrapper a {@link edu.cmu.tetradapp.model.SemUpdaterWrapper} object
     */
    public SemImWrapper(SemUpdaterWrapper semUpdaterWrapper) {
        if (semUpdaterWrapper == null) {
            throw new NullPointerException("SemPmWrapper must not be null.");
        }

        setSemIm(semUpdaterWrapper.getSemUpdater().getUpdatedSemIm());
    }

    /**
     * <p>Constructor for SemImWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.PValueImproverWrapper} object
     */
    public SemImWrapper(PValueImproverWrapper wrapper) {
        SemIm oldSemIm = wrapper.getNewSemIm();
        setSemIm(oldSemIm);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    //===========================PUBLIC METHODS=========================//

    /**
     * <p>getSemIm.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    public SemIm getSemIm() {
        return this.semIms.get(getModelIndex());
    }

    private void setSemIm(SemIm updatedSemIm) {
        this.semIms = new ArrayList<>();
        this.semIms.add(new SemIm(updatedSemIm));

        for (int i = 0; i < this.semIms.size(); i++) {
            log(i, this.semIms.get(i));
        }
    }

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return getSemIm().getSemPm().getGraph();
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

    //======================== Private methods =======================//
    private void log(int i, SemIm pm) {
        TetradLogger.getInstance().log("Linear SEM IM");
        TetradLogger.getInstance().log("IM # " + (i + 1));
        String message = pm.toString();
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

