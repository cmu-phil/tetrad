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

package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.PcRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Splits continuous data sets by collinear columns.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class CovMatrixAverageWrapper extends DataWrapper {
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for CovMatrixAverageWrapper.</p>
     *
     * @param covs   an array of {@link edu.cmu.tetradapp.model.DataWrapper} objects
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public CovMatrixAverageWrapper(DataWrapper[] covs, Parameters params) {

        List<DataWrapper> matrices = new ArrayList<>(Arrays.asList(covs));

        calcAverage(matrices);
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

    private void calcAverage(List<DataWrapper> wrappers) {
        List<Matrix> cov = new ArrayList<>();

        for (DataWrapper wrapper : wrappers) {
            DataModel selectedDataModel = wrapper.getSelectedDataModel();

            if (!(selectedDataModel instanceof ICovarianceMatrix)) {
                throw new IllegalArgumentException("Sorry, this is an average only over covariance matrices.");
            }

            cov.add(((ICovarianceMatrix) selectedDataModel).getMatrix());
        }

        Matrix cov3 = new Matrix(cov.get(0).getNumRows(), cov.get(0).getNumRows());

        for (int i = 0; i < cov.get(0).getNumRows(); i++) {
            for (int j = 0; j < cov.get(0).getNumRows(); j++) {
                double c = 0.0;

                for (Matrix matrix : cov) {
                    c += matrix.get(i, j);
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


}




