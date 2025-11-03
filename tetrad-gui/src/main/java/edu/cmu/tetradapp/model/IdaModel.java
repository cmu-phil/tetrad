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

import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IdaCheck;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.Serial;
import java.util.LinkedList;
import java.util.List;

/**
 * A model for the IDA check. This model is used to store the data model, graph, and parameters for the IDA check.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class IdaModel implements SessionModel {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * The data model to check.
     */
    private final DataModel dataModel;
    /**
     * Represents the estimated graph associated with the current instance of IdaModel.
     */
    private final Graph estPdag;
    /**
     * Represents the true SemIm object associated with the class IdaModel. It can be null if the object is not
     * available.
     */
    private final SemIm trueSemIm;
    /**
     * The parameters.
     */
    private final Parameters parameters;
    /**
     * The name of this model.
     */
    private String name = "";
    /**
     * The variables to check.
     */
    private List<String> vars = new LinkedList<>();
    /**
     * Represents the IdaCheck object associated with the estimated PDAG. This variable is used to perform checks on
     * the estimated PDAG. It is set to null if the estimated PDAG is not available.
     * <p>
     * Note: This variable is marked as transient, meaning it will not be serialized.
     */
    private transient IdaCheck idaCheckEst;
    /**
     * Represents the IdaCheck object associated with the true PDAG. This variable is used in the context of the
     * IdaModel class.
     * <p>
     * Note: This variable is marked as transient, meaning it will not be serialized.
     */
    private transient IdaCheck idaCheckTrue;

    /**
     * Constructs a new instance of the IdaModel class.
     *
     * @param dataWrapper the data wrapper for the IdaModel; could be a Simulation.
     * @param graphSource the graph source for the IdaModel.
     * @param parameters  the parameters for the IdaModel.
     * @throws IllegalArgumentException if the data model is not a DataSet.
     */
    public IdaModel(DataWrapper dataWrapper, GraphSource graphSource, Parameters parameters) {

        // Check nullity. SemImWrapper may be null.
        if (dataWrapper == null) {
            throw new NullPointerException("Data model must not be null.");
        }

        if (graphSource == null) {
            throw new NullPointerException("Graph source must not be null.");
        }

        if (parameters == null) {
            throw new NullPointerException("Parameters must not be null.");
        }

        this.dataModel = dataWrapper.getSelectedDataModel();
        this.parameters = parameters;

        // Make sure the data model is a DataSet.
        if (!(this.dataModel instanceof DataSet)) {
            throw new IllegalArgumentException("Expecting a data set.");
        }

        if (!graphSource.getGraph().paths().isLegalPdag()) {
            throw new IllegalArgumentException("Expecting an PDAG. (Could be a CPDAG or a DAG.)");
        }

        this.estPdag = graphSource.getGraph();

        // If the data model is a simulation, get the true SEM IM.
        if (dataWrapper instanceof Simulation simulation) {
            if (simulation.getSimulation() == null) {
                throw new IllegalArgumentException("The simulation was not initialized.");
            }

            if (!(simulation.getSimulation() instanceof SemSimulation)) {
                throw new IllegalArgumentException("The simulation was not a SEM simulation.");
            }

            List<SemIm> ims = ((SemSimulation) (simulation.getSimulation())).getIms();
            this.trueSemIm = ims.getFirst();
        } else {
            this.trueSemIm = null;
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link Knowledge} object
     * @see TetradSerializableUtils
     */
    public static Knowledge serializableInstance() {
        return new Knowledge();
    }

    /**
     * Retrieves the IdaCheck object associated with the estimated PDAG.
     *
     * @return the IdaCheck object associated with the estimated PDAG, or null if it is not available.
     */
    public IdaCheck getIdaCheckEst() {
        if (this.idaCheckEst != null) {
            return this.idaCheckEst;
        }

        this.idaCheckEst = new IdaCheck(this.estPdag, (DataSet) this.dataModel, trueSemIm);
        return this.idaCheckEst;
    }

    /**
     * Retrieves the IdaCheck object with the true DAG.
     *
     * @return the IdaCheck object with the true DAG, or null if the true DAG is not available.
     */
    public IdaCheck getIdaCheckTrue() {
        if (this.idaCheckTrue != null) {
            return this.idaCheckTrue;
        }

        if (this.trueSemIm == null) {
            return null;
        }

        this.idaCheckTrue = new IdaCheck(estPdag, (DataSet) this.dataModel, trueSemIm);
        return this.idaCheckTrue;
    }

    /**
     * Returns the data model.
     *
     * @return the data model.
     */
    public DataModel getDataModel() {
        return dataModel;
    }

    /**
     * Returns the parameters.
     *
     * @return the parameters.
     */
    public Parameters getParameters() {
        return parameters;
    }

    /**
     * Returns the name of the session model.
     *
     * @return the name of the session model
     */
    @Override
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name of the session model.
     *
     * @param name the name of the session model.
     */
    @Override
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the variables to check.
     *
     * @return the variables to check.
     */
    public List<String> getVars() {
        return this.vars;
    }

    /**
     * Sets the variables to check.
     *
     * @param vars the variables to check.
     */
    public void setVars(List<String> vars) {
        this.vars = vars;
    }

    /**
     * Gets the true SemIm object. Could be null.
     *
     * @return the true SemIm object
     */
    public SemIm getTrueSemIm() {
        return trueSemIm;
    }
}




