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

import edu.cmu.tetrad.bayes.DirichletBayesIm;
import edu.cmu.tetrad.bayes.DirichletEstimator;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
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
public class DirichletEstimatorWrapper implements SessionModel {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The Dirichlet Bayes IM.
     */
    private final DirichletBayesIm dirichletBayesIm;

    /**
     * The name of the model.
     */
    private String name;

    //============================CONSTRUCTORS============================//



    /**
     * <p>Constructor for DirichletEstimatorWrapper.</p>
     *
     * @param dataWrapper           a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param dirichletPriorWrapper a {@link edu.cmu.tetradapp.model.DirichletBayesImWrapper} object
     */
    public DirichletEstimatorWrapper(DataWrapper dataWrapper,
                                     DirichletBayesImWrapper dirichletPriorWrapper) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }

        if (dirichletPriorWrapper == null) {
            throw new NullPointerException();
        }

        DataSet dataSet
                = (DataSet) dataWrapper.getSelectedDataModel();

        if (DataUtils.containsMissingValue(dataSet)) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }

        DirichletBayesIm dirichletBayesIm
                = dirichletPriorWrapper.getDirichletBayesIm();

        try {
            this.dirichletBayesIm
                    = DirichletEstimator.estimate(dirichletBayesIm, dataSet);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                    "Please fully specify the Dirichlet prior first.");
        }
        log(dirichletBayesIm);
    }

    /**
     * <p>Constructor for DirichletEstimatorWrapper.</p>
     *
     * @param dataWrapper    a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param bayesPmWrapper a {@link edu.cmu.tetradapp.model.BayesPmWrapper} object
     * @param params         a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public DirichletEstimatorWrapper(DataWrapper dataWrapper,
                                     BayesPmWrapper bayesPmWrapper, Parameters params) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }

        if (bayesPmWrapper == null) {
            throw new NullPointerException();
        }

        if (params == null) {
            throw new NullPointerException();
        }

        DataSet dataSet
                = (DataSet) dataWrapper.getSelectedDataModel();

        if (DataUtils.containsMissingValue(dataSet)) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }

        DirichletBayesIm dirichletBayesIm
                = DirichletBayesIm.symmetricDirichletIm(
                bayesPmWrapper.getBayesPm(),
                params.getDouble("symmetricAlpha", 1.0));

        if (DataUtils.containsMissingValue(dataSet)) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }

        try {
            this.dirichletBayesIm
                    = DirichletEstimator.estimate(dirichletBayesIm, dataSet);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                    "Please fully specify the Dirichlet prior first.");
        }
        log(dirichletBayesIm);
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

    //===============================PUBLIC METHODS=======================//

    /**
     * <p>getEstimatedBayesIm.</p>
     *
     * @return a {@link edu.cmu.tetrad.bayes.DirichletBayesIm} object
     */
    public DirichletBayesIm getEstimatedBayesIm() {
        return this.dirichletBayesIm;
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
        return this.dirichletBayesIm.getBayesPm().getDag();
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

    private void log(DirichletBayesIm im) {
        TetradLogger.getInstance().log("Estimated Dirichlet Bayes IM");
        TetradLogger.getInstance().log("" + im);
        TetradLogger.getInstance().reset();
    }

}
