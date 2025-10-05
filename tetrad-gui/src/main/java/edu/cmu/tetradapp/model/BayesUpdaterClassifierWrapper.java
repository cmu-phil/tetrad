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
import edu.cmu.tetrad.classify.ClassifierBayesUpdaterDiscrete;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/**
 * Wraps a DirichletEstimator.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BayesUpdaterClassifierWrapper implements SessionModel {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The Bayes Updater.
     */
    private final ClassifierBayesUpdaterDiscrete classifier;

    /**
     * The name of the model.
     */
    private String name;

    //==============================CONSTRUCTORS===========================//

    /**
     * <p>Constructor for BayesUpdaterClassifierWrapper.</p>
     *
     * @param bayesImWrapper a {@link edu.cmu.tetradapp.model.BayesImWrapper} object
     * @param dataWrapper    a {@link edu.cmu.tetradapp.model.DataWrapper} object
     */
    public BayesUpdaterClassifierWrapper(BayesImWrapper bayesImWrapper,
                                         DataWrapper dataWrapper) {
        if (bayesImWrapper == null) {
            throw new NullPointerException();
        }

        if (dataWrapper == null) {
            throw new NullPointerException();
        }

        DataSet dataSet =
                (DataSet) dataWrapper.getSelectedDataModel();
        BayesIm bayesIm = bayesImWrapper.getBayesIm();

        this.classifier = new ClassifierBayesUpdaterDiscrete(bayesIm, dataSet);
    }

    /**
     * <p>Constructor for BayesUpdaterClassifierWrapper.</p>
     *
     * @param bayesImWrapper a {@link edu.cmu.tetradapp.model.DirichletBayesImWrapper} object
     * @param dataWrapper    a {@link edu.cmu.tetradapp.model.DataWrapper} object
     */
    public BayesUpdaterClassifierWrapper(DirichletBayesImWrapper bayesImWrapper,
                                         DataWrapper dataWrapper) {
        if (bayesImWrapper == null) {
            throw new NullPointerException();
        }

        if (dataWrapper == null) {
            throw new NullPointerException();
        }

        DataSet dataSet =
                (DataSet) dataWrapper.getSelectedDataModel();
        BayesIm bayesIm = bayesImWrapper.getDirichletBayesIm();

        this.classifier = new ClassifierBayesUpdaterDiscrete(bayesIm, dataSet);
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

    //==============================PUBLIC METHODS=======================//

    /**
     * <p>Getter for the field <code>classifier</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.classify.ClassifierBayesUpdaterDiscrete} object
     */
    public ClassifierBayesUpdaterDiscrete getClassifier() {
        return this.classifier;
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
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
}






