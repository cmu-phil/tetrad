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

import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradSerializableUtils;

/**
 * Wraps a data model so that a random sample will automatically be drawn on
 * construction from a BayesIm.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class DirichletBayesDataWrapper extends DataWrapper
        implements SessionModel, UnlistedSessionModel {
    static final long serialVersionUID = 23L;

    public DirichletBayesDataWrapper(DirichletBayesImWrapper wrapper,
            BayesDataParams params) {
        int sampleSize = params.getSampleSize();
        boolean latentDataSaved = params.isLatentDataSaved();
        setDataModel(wrapper.getDirichletBayesIm().simulateData(sampleSize, latentDataSaved));
        setSourceGraph(wrapper.getDirichletBayesIm().getDag());
        LogDataUtils.logDataModelList("Data simulated from a Dirichlet Bayes net.", getDataModelList());
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        return new DirichletBayesDataWrapper(
                DirichletBayesImWrapper.serializableInstance(),
                BayesDataParams.serializableInstance());
    }
}





