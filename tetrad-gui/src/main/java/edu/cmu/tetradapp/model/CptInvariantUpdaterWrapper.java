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

import edu.cmu.tetrad.bayes.*;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetrad.util.Unmarshallable;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/**
 * Wraps a Bayes Updater for use in the Tetrad application.
 *
 * @author William Taysom -- 2003/06/14
 * @version $Id: $Id
 */
public class CptInvariantUpdaterWrapper implements SessionModel, UpdaterWrapper, Unmarshallable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The name of the model.
     */
    private String name;

    /**
     * The Bayes Updater.
     */
    private ManipulatingBayesUpdater bayesUpdater;

    /**
     * The params object, so the GUI can remember stuff for logging.
     */
    private Parameters params;

    //============================CONSTRUCTORS==========================//

    /**
     * <p>Constructor for CptInvariantUpdaterWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesImWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public CptInvariantUpdaterWrapper(BayesImWrapper wrapper, Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        BayesIm bayesIm = wrapper.getBayesIm();
        setup(bayesIm, params);
    }

    /**
     * <p>Constructor for CptInvariantUpdaterWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DirichletBayesImWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public CptInvariantUpdaterWrapper(DirichletBayesImWrapper wrapper, Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        DirichletBayesIm bayesIm = wrapper.getDirichletBayesIm();
        setup(bayesIm, params);
    }

    /**
     * <p>Constructor for CptInvariantUpdaterWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesEstimatorWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public CptInvariantUpdaterWrapper(BayesEstimatorWrapper wrapper, Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        BayesIm bayesIm = wrapper.getEstimatedBayesIm();
        setup(bayesIm, params);
    }

    /**
     * <p>Constructor for CptInvariantUpdaterWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DirichletEstimatorWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public CptInvariantUpdaterWrapper(DirichletEstimatorWrapper wrapper, Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        DirichletBayesIm bayesIm = wrapper.getEstimatedBayesIm();
        setup(bayesIm, params);
    }

    /**
     * <p>Constructor for CptInvariantUpdaterWrapper.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.EmBayesEstimatorWrapper} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public CptInvariantUpdaterWrapper(EmBayesEstimatorWrapper wrapper, Parameters params) {
        if (wrapper == null) {
            throw new NullPointerException();
        }
        BayesIm bayesIm = wrapper.getEstimateBayesIm();
        setup(bayesIm, params);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.CptInvariantUpdaterWrapper} object
     * @see TetradSerializableUtils
     */
    public static CptInvariantUpdaterWrapper serializableInstance() {
        return new CptInvariantUpdaterWrapper(
                BayesImWrapper.serializableInstance(), new Parameters());
    }

    //=============================PUBLIC METHODS==========================//

    /**
     * <p>Getter for the field <code>bayesUpdater</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.bayes.ManipulatingBayesUpdater} object
     */
    public ManipulatingBayesUpdater getBayesUpdater() {
        return this.bayesUpdater;
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

    //============================PRIVATE METHODS=========================//

    private void setup(BayesIm bayesIm, Parameters params) {
        TetradLogger.getInstance().setConfigForClass(this.getClass());
        this.params = params;
        if (params.get("evidence", null) == null || ((Evidence) params.get("evidence", null)).isIncompatibleWith(bayesIm)) {
            this.bayesUpdater = new CptInvariantUpdater(bayesIm);
        } else {
            this.bayesUpdater = new CptInvariantUpdater(bayesIm,
                    (Evidence) params.get("evidence", null));
        }

        TetradLogger.getInstance().reset();
    }

    private String category(Evidence evidence, String nodeName, int i) {
        DiscreteVariable variable = discreteVariable(evidence, nodeName);
        return variable.getCategory(i);
    }

    private DiscreteVariable discreteVariable(Evidence evidence, String nodeName) {
        return evidence.getVariable(nodeName);
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
     * <p>Getter for the field <code>params</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Parameters getParams() {
        return this.params;
    }
}





