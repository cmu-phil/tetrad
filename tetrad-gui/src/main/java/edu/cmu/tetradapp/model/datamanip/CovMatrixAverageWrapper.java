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
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits continuous data sets by collinear columns.
 *
 * @author Tyler Gibson
 */
public class CovMatrixAverageWrapper extends DataWrapper {
    static final long serialVersionUID = 23L;

    public CovMatrixAverageWrapper(DataWrapper cov1) {
        List<DataWrapper> matrices = new ArrayList<>();

        matrices.add(cov1);

        calcAverage(matrices);
    }

    public CovMatrixAverageWrapper(DataWrapper cov1, DataWrapper cov2) {
        List<DataWrapper> matrices = new ArrayList<>();

        matrices.add(cov1);
        matrices.add(cov2);
        calcAverage(matrices);
    }

    public CovMatrixAverageWrapper(DataWrapper cov1, DataWrapper cov2, DataWrapper cov3) {
        List<DataWrapper> matrices = new ArrayList<>();

        matrices.add(cov1);
        matrices.add(cov2);
        matrices.add(cov3);
        calcAverage(matrices);
    }

    public CovMatrixAverageWrapper(DataWrapper cov1, DataWrapper cov2, DataWrapper cov3,
                                   DataWrapper cov4) {
        List<DataWrapper> matrices = new ArrayList<>();

        matrices.add(cov1);
        matrices.add(cov2);
        matrices.add(cov3);
        matrices.add(cov4);
        calcAverage(matrices);
    }

    public CovMatrixAverageWrapper(DataWrapper cov1, DataWrapper cov2, DataWrapper cov3,
                                   DataWrapper cov4, DataWrapper cov5) {
        List<DataWrapper> matrices = new ArrayList<>();

        matrices.add(cov1);
        matrices.add(cov2);
        matrices.add(cov3);
        matrices.add(cov4);
        matrices.add(cov5);
        calcAverage(matrices);
    }

    public CovMatrixAverageWrapper(DataWrapper cov1, DataWrapper cov2, DataWrapper cov3,
                                   DataWrapper cov4, DataWrapper cov5, DataWrapper cov6) {
        List<DataWrapper> matrices = new ArrayList<>();

        matrices.add(cov1);
        matrices.add(cov2);
        matrices.add(cov3);
        matrices.add(cov4);
        matrices.add(cov5);
        matrices.add(cov6);
        calcAverage(matrices);
    }

    public CovMatrixAverageWrapper(DataWrapper cov1, DataWrapper cov2, DataWrapper cov3,
                                   DataWrapper cov4, DataWrapper cov5, DataWrapper cov6,
                                   DataWrapper cov7) {
        List<DataWrapper> matrices = new ArrayList<>();

        matrices.add(cov1);
        matrices.add(cov2);
        matrices.add(cov3);
        matrices.add(cov4);
        matrices.add(cov5);
        matrices.add(cov6);
        matrices.add(cov7);

        calcAverage(matrices);
    }

    public CovMatrixAverageWrapper(DataWrapper cov1, DataWrapper cov2, DataWrapper cov3,
                                   DataWrapper cov4, DataWrapper cov5, DataWrapper cov6,
                                   DataWrapper cov7, DataWrapper cov8) {
        List<DataWrapper> matrices = new ArrayList<>();

        matrices.add(cov1);
        matrices.add(cov2);
        matrices.add(cov3);
        matrices.add(cov4);
        matrices.add(cov5);
        matrices.add(cov6);
        matrices.add(cov7);
        matrices.add(cov8);

        calcAverage(matrices);
    }

    private void calcAverage(List<DataWrapper> wrappers) {
        List<TetradMatrix> cov = new ArrayList<TetradMatrix>();

        for (int i = 0; i < wrappers.size(); i++) {
            DataModel selectedDataModel = wrappers.get(i).getSelectedDataModel();
            cov.add(((ICovarianceMatrix) selectedDataModel).getMatrix());
        }

        TetradMatrix cov3 = new TetradMatrix(cov.get(0).rows(), cov.get(0).rows());

        for (int i = 0; i < cov.get(0).rows(); i++) {
            for (int j = 0; j < cov.get(0).rows(); j++) {
                double c = 0.0;

                for (int k = 0; k < cov.size(); k++) {
                    c += cov.get(k).get(i, j);
                }

                c /= cov.size();

                cov3.set(i, j, c);
                cov3.set(j, i, c);
            }
        }

        DataModel m = wrappers.get(0).getSelectedDataModel();
        ICovarianceMatrix _cov = (ICovarianceMatrix) m;
        List<Node> nodes = _cov.getVariables();
        int n = _cov.getSampleSize();

        ICovarianceMatrix covWrapper = new CovarianceMatrix(nodes, cov3, n);

        setDataModel(covWrapper);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        return null;
    }


}




