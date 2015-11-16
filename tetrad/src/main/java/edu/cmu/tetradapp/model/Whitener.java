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

import cern.colt.list.DoubleArrayList;
import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.Algebra;
import cern.colt.matrix.linalg.SingularValueDecomposition;
import cern.jet.math.Mult;
import cern.jet.stat.Descriptive;
import edu.cmu.tetrad.data.ColtDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradSerializableUtils;

/**
 * Converts a continuous data set to a correlation matrix.
 *
 * @author Joseph Ramsey
 */
public class Whitener extends DataWrapper {
    static final long serialVersionUID = 23L;
    private DataSet dataSet;

    //=============================CONSTRUCTORS==============================//

    public Whitener(DataWrapper wrapper) {
        if (wrapper.getSelectedDataModel() instanceof DataSet) {
            DataSet _dataSet = (DataSet) wrapper.getSelectedDataModel();
            DoubleMatrix2D X = new DenseDoubleMatrix2D(_dataSet.getDoubleData().toArray());

            int n = X.rows();
            int p = X.columns();

            Algebra alg = new Algebra();

            boolean verbose = true;

            DoubleMatrix2D wInit = null;

            int numComponents = Math.min(n, p);

            if (numComponents > Math.min(n, p)) {
                System.out.println("numComponents is too large.");
                System.out.println("numComponents set to " + Math.min(n, p));
                numComponents = Math.min(n, p);
            }

            if (wInit == null) {
                wInit = new DenseDoubleMatrix2D(numComponents, numComponents);
                for (int i = 0; i < wInit.rows(); i++) {
                    for (int j = 0; j < wInit.columns(); j++) {
                        wInit.set(i, j, RandomUtil.getInstance().nextNormal(0, 1));
                    }
                }
            } else if (wInit.rows() != wInit.columns()) {
                throw new IllegalArgumentException("wInit is the wrong size.");
            }

            if (verbose) {
                System.out.println("Centering");
            }

            X = scale(X, false);

            boolean rowNorm = false;

            if (rowNorm) {
                X = scale(X, true).viewDice();
            } else {
                X = X.viewDice();
            }

            if (verbose) {
                System.out.println("Whitening");
            }

            DoubleMatrix2D V = alg.mult(X, X.viewDice());
            V.assign(Mult.div(n));

            SingularValueDecomposition s = new SingularValueDecomposition(V);
            DoubleMatrix2D D = s.getS();

            for (int i = 0; i < D.rows(); i++) {
                D.set(i, i, 1.0 / Math.sqrt(D.get(i, i)));
            }

            DoubleMatrix2D K = alg.mult(D, s.getU().viewDice());
            K = K.assign(Mult.mult(-1)); // This SVD gives -U from R's SVD.
            K = K.viewPart(0, 0, numComponents, p);

            DoubleMatrix2D X1 = alg.mult(K, X);

            dataSet = ColtDataSet.makeContinuousData(_dataSet.getVariables(), new TetradMatrix(X1.viewDice().toArray()));
        }
        else {
            throw new IllegalArgumentException("Expecting a continuous data set or a covariance matrix.");
        }

        setDataModel(dataSet);
        setSourceGraph(wrapper.getSourceGraph());

        LogDataUtils.logDataModelList("Conversion of parent data to correlation matrix form.", getDataModelList());

    }

    private DoubleMatrix2D scale(DoubleMatrix2D x, boolean scale) {
        for (int j = 0; j < x.columns(); j++) {
            DoubleArrayList u = new DoubleArrayList(x.viewColumn(j).toArray());
            double mean = Descriptive.mean(u);

            for (int i = 0; i < x.rows(); i++) {
                x.set(i, j, x.get(i, j) - mean);
            }

            if (scale) {
                double rms = rms(x.viewColumn(j));

                for (int i = 0; i < x.rows(); i++) {
                    x.set(i, j, x.get(i, j) / rms);
                }
            }
        }

        return x;
    }

    private double rms(DoubleMatrix1D w) {
        double ssq = Descriptive.sumOfSquares(new DoubleArrayList(w.toArray()));
        return Math.sqrt(ssq);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        DataWrapper wrapper =
                new DataWrapper(DataUtils.continuousSerializableInstance());
        return new Whitener(wrapper);
    }
}






