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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.classify.ClassifierBayesUpdaterDiscrete;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
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
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help
     *
     * @param s a {@link java.lang.String} object
     * @throws IOException            if any.
     * @throws ClassNotFoundException if any.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.classifier == null) {
            throw new NullPointerException();
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





