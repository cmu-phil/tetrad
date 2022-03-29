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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.search.BayesUpdaterClassifier;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Wraps a DirichletEstimator.
 *
 * @author Joseph Ramsey
 */
public class BayesUpdaterClassifierWrapper implements SessionModel {
    static final long serialVersionUID = 23L;

    /**
     * @serial Cannot be null.
     */
    private final BayesUpdaterClassifier classifier;

    /**
     * @serial Can be null.
     */
    private String name;

    //==============================CONSTRUCTORS===========================//

    public BayesUpdaterClassifierWrapper(final BayesImWrapper bayesImWrapper,
                                         final DataWrapper dataWrapper) {
        if (bayesImWrapper == null) {
            throw new NullPointerException();
        }

        if (dataWrapper == null) {
            throw new NullPointerException();
        }

        final DataSet dataSet =
                (DataSet) dataWrapper.getSelectedDataModel();
        final BayesIm bayesIm = bayesImWrapper.getBayesIm();

        this.classifier = new BayesUpdaterClassifier(bayesIm, dataSet);
    }

    public BayesUpdaterClassifierWrapper(final DirichletBayesImWrapper bayesImWrapper,
                                         final DataWrapper dataWrapper) {
        if (bayesImWrapper == null) {
            throw new NullPointerException();
        }

        if (dataWrapper == null) {
            throw new NullPointerException();
        }

        final DataSet dataSet =
                (DataSet) dataWrapper.getSelectedDataModel();
        final BayesIm bayesIm = bayesImWrapper.getDirichletBayesIm();

        this.classifier = new BayesUpdaterClassifier(bayesIm, dataSet);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    //==============================PUBLIC METHODS=======================//

    public BayesUpdaterClassifier getClassifier() {
        return this.classifier;
    }

    private void readObject(final ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.classifier == null) {
            throw new NullPointerException();
        }
    }

    public String getName() {
        return this.name;
    }

    public void setName(final String name) {
        this.name = name;
    }
}





