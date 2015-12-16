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

package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.SemImWrapper;

import java.util.List;

/**
 * Splits continuous data sets by collinear columns.
 *
 * @author Tyler Gibson
 */
public class CovMatrixWrapper extends DataWrapper {
     static final long serialVersionUID = 23L;

    /**
     * Splits the given data set by collinear columns.
     */
    public CovMatrixWrapper(DataWrapper wrapper) {
        if (wrapper == null) {
            throw new NullPointerException("The given data must not be null");
        }

        DataModelList models = wrapper.getDataModelList();
        DataModelList out = new DataModelList();

        for (DataModel model : models) {
            if (model instanceof DataSet) {
                DataSet dataSet = (DataSet) model;

                if (!(dataSet.isContinuous())) {
                    throw new IllegalArgumentException("The data must be continuous");
                }

                ICovarianceMatrix covarianceMatrix = new CovarianceMatrix(dataSet);
                out.add(covarianceMatrix);
            } else if (model instanceof ICovarianceMatrix) {
                ICovarianceMatrix covarianceMatrix = new CovarianceMatrix((CovarianceMatrix)model);
                out.add(covarianceMatrix);
            }
        }

        setDataModel(out);

//        setDataModel(out);
//        setSourceGraph(wrapper.getSourceGraph());
//
//        DataModel model = wrapper.getSelectedDataModel();
//        if (model instanceof DataSet) {
//            DataSet dataSet = (DataSet) model;
//            if (!(dataSet.isContinuous())) {
//               throw new IllegalArgumentException("The data must be continuous");
//            }
//
//            ICovarianceMatrix covarianceMatrix = new CovarianceMatrix(dataSet);
//            setDataModel(covarianceMatrix);
//            setSourceGraph(wrapper.getSourceGraph());
//        } else if (model instanceof ICovarianceMatrix) {
//            ICovarianceMatrix covarianceMatrix = new CovarianceMatrix((CovarianceMatrix)model);
//            setDataModel(covarianceMatrix);
//            setSourceGraph(wrapper.getSourceGraph());
//        } else {
//            throw new IllegalArgumentException("Must be a dataset or a covariance  matrix");
//        }

        LogDataUtils.logDataModelList("Conversion of data to covariance matrix form.", getDataModelList());

    }

    public CovMatrixWrapper(SemImWrapper wrapper) {
        if (wrapper == null) {
            throw new NullPointerException("The Sem IM must not be null.");
        }

        SemIm semIm = wrapper.getSemIm();

        TetradMatrix matrix = semIm.getImplCovar(true);
        List<Node> variables = semIm.getSemPm().getVariableNodes();

        ICovarianceMatrix covarianceMatrix = new CovarianceMatrix(variables, matrix, semIm.getSampleSize());
        setDataModel(covarianceMatrix);
        setSourceGraph(semIm.getSemPm().getGraph());

        LogDataUtils.logDataModelList("Conversion of data to covariance matrix form.", getDataModelList());
    }


    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        return new CovMatrixWrapper(new DataWrapper(DataUtils.continuousSerializableInstance()));
    }


}




