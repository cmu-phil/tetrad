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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.bayes.MlBayesImObs;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Memorable;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.List;

///////////////////////////////////////////////////////////
// Wraps a Bayes Im (observed variables only) for use
// in the Tetrad application.
//
// @author josephramsey
///////////////////////////////////////////////////////////

/**
 * <p>BayesImWrapperObs class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BayesImWrapperObs implements SessionModel, Memorable {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The name of the model.
     */
    private String name;

    /**
     * The Bayes Im.
     */
    private BayesIm bayesIm;

    //===========================CONSTRUCTORS===========================//
    /////////////////////////////////////////////////////////////////
    // Disregard all other methods of instantiating an IM
    // Only constructed from a PM or from another BayesIm
    //
    // If from a regular BayesIm, the new probability values are
    // the marginalized values of the allowUnfaithfulness probability values in
    // the old BayesIm, stored in a JPD

    /**
     * <p>Constructor for BayesImWrapperObs.</p>
     *
     * @param bayesPmWrapper a {@link edu.cmu.tetradapp.model.BayesPmWrapper} object
     * @param params         a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public BayesImWrapperObs(BayesPmWrapper bayesPmWrapper, Parameters params) {
        if (bayesPmWrapper == null) {
            throw new NullPointerException("BayesPmWrapper must not be null.");
        }

        if (params == null) {
            throw new NullPointerException("Parameters must not be null.");
        }

        BayesPm bayesPm = new BayesPm(bayesPmWrapper.getBayesPm());

        if (params.getString("initializationMode", "manualRetain").equals("manualRetain")) {
            this.bayesIm = new MlBayesImObs(bayesPm);
        } else if (params.getString("initializationMode", "manualRetain").equals("randomRetain")) {
            this.bayesIm = new MlBayesImObs(bayesPm, MlBayesIm.RANDOM);
        } else if (params.getString("initializationMode", "manualRetain").equals("randomOverwrite")) {
            this.bayesIm = new MlBayesImObs(bayesPm, MlBayesIm.RANDOM);
        }

        assert this.bayesIm != null;
        log(this.bayesIm);
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

    //=============================PUBLIC METHODS=========================//

    /**
     * <p>Getter for the field <code>bayesIm</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.bayes.BayesIm} object
     */
    public BayesIm getBayesIm() {
        return this.bayesIm;
    }

    /**
     * <p>Setter for the field <code>bayesIm</code>.</p>
     *
     * @param bayesIm a {@link edu.cmu.tetrad.bayes.BayesIm} object
     */
    public void setBayesIm(BayesIm bayesIm) {
        this.bayesIm = bayesIm;
    }

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return this.bayesIm.getBayesPm().getDag();
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

    //============================== private methods ============================//
    private void log(BayesIm im) {
        TetradLogger.getInstance().log("Maximum likelihood Bayes IM: Observed Variables Only");
        String message = im.toString();
        TetradLogger.getInstance().log(message);
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
}

