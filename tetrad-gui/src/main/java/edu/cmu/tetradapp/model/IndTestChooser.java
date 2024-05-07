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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.score.ImagesScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.*;
import edu.cmu.tetrad.search.utils.ResolveSepsets;
import edu.cmu.tetrad.search.work_in_progress.IndTestFisherZPercentIndependent;
import edu.cmu.tetrad.search.work_in_progress.IndTestMultinomialLogisticRegression;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.util.IndTestType;
import edu.pitt.csb.mgm.IndTestMultinomialLogisticRegressionWald;

import java.util.ArrayList;
import java.util.List;

/**
 * Chooses an independence test for a particular data source.
 *
 * @author josephramsey
 */
final class IndTestChooser {
    private boolean precomputeCovariances = true;

    /**
     * <p>getTest.</p>
     *
     * @param dataSource a {@link java.lang.Object} object
     * @param params     a {@link edu.cmu.tetrad.util.Parameters} object
     * @return a {@link edu.cmu.tetrad.search.IndependenceTest} object
     */
    public IndependenceTest getTest(Object dataSource, Parameters params) {
        return getTest(dataSource, params, IndTestType.DEFAULT);
    }

    /**
     * <p>getTest.</p>
     *
     * @param dataSource a {@link java.lang.Object} object
     * @param params     a {@link edu.cmu.tetrad.util.Parameters} object
     * @param testType   a {@link edu.cmu.tetradapp.util.IndTestType} object
     * @return an independence checker appropriate to the given data source. Also sets the Parameters on the params to
     * an appropriate type object (using the existing one if it's of the right type).
     */
    public IndependenceTest getTest(Object dataSource, Parameters params,
                                    IndTestType testType) {

        if (dataSource == null) {
            throw new NullPointerException();
        }
        if (params == null) {
            throw new NullPointerException();
        }

        if (dataSource instanceof DataModelList datasets) {

            List<DataSet> _dataSets = new ArrayList<>();

            for (DataModel dataModel : datasets) {
                _dataSets.add((DataSet) dataModel);
            }

            return getMultiContinuousTest(_dataSets, params, testType);
        }

        if (dataSource instanceof DataSet dataSet) {

            if (dataSet.isContinuous() || dataSet.getNumColumns() == 0) {
                DataSet dataContinuous =
                        (DataSet) dataSource;

                return getContinuousTest(dataContinuous, params, testType);
            } else if (dataSet.isDiscrete()) {
                DataSet dataDiscrete =
                        (DataSet) dataSource;

                return getDiscreteTest(dataDiscrete, params, testType);
            }
            if (dataSet.isMixed()) {
                DataSet dataMixed = (DataSet) dataSource;

                return getMixedTest(dataMixed, params, testType);
            }
        }

        if (dataSource instanceof Graph) {
            return getGraphTest((Graph) dataSource, params,
                    IndTestType.M_SEPARATION);
        }
        if (dataSource instanceof ICovarianceMatrix) {
            return getCovMatrixTest((ICovarianceMatrix) dataSource, params);
        }

        if (dataSource instanceof IndependenceFacts) {
            return new IndTestIndependenceFacts((IndependenceFacts) dataSource);
        }

        throw new IllegalStateException(
                "Unrecognized data source type: " + dataSource.getClass());
    }

    private IndependenceTest getMixedTest(DataSet dataSet,
                                          Parameters params, IndTestType testType) {

        if (IndTestType.MIXED_MLR == testType) {
            return new IndTestMultinomialLogisticRegressionWald(dataSet, params.getDouble("alpha", 0.001), false);
        } else if (IndTestType.LINEAR_REGRESSION == testType) {
            return new IndTestRegression(dataSet,
                    params.getDouble("alpha", 0.001));
        } else {
            params.set("indTestType", IndTestType.MIXED_MLR);
            return new IndTestMultinomialLogisticRegression(dataSet, params.getDouble("alpha", 0.001));
        }
    }

    private IndependenceTest getContinuousTest(DataSet dataSet,
                                               Parameters params, IndTestType testType) {
        if (IndTestType.CONDITIONAL_CORRELATION == testType) {
            return new IndTestConditionalCorrelation(dataSet, params.getDouble("alpha", 0.001));
        }
        if (IndTestType.FISHER_Z == testType) {
            return new IndTestFisherZ(dataSet, params.getDouble("alpha", 0.001));
        }
//        if (IndTestType.FISHER_ZD == testType) {
//            IndTestFisherZ test = new IndTestFisherZ(dataSet, params.getDouble("alpha", 0.001));
////            test.setUsePseudoinverse(true);
//            return test;
//        }
        if (IndTestType.SEM_BIC == testType) {
            return new ScoreIndTest(new SemBicScore(new CovarianceMatrix(dataSet)));
        }

        {
            params.set("indTestType", IndTestType.FISHER_Z);
            return new IndTestFisherZ(dataSet, params.getDouble("alpha", 0.001));
        }
    }

    private IndependenceTest getMultiContinuousTest(List<DataSet> dataSets,
                                                    Parameters params, IndTestType testType) {
        if (IndTestType.POOL_RESIDUALS_FISHER_Z == testType) {
            return new IndTestFisherZPercentIndependent(dataSets, params.getDouble("alpha", 0.001));
        }

        if (IndTestType.TIPPETT == testType) {
            List<IndependenceTest> independenceTests = new ArrayList<>();
            for (DataSet dataModel : dataSets) {
                independenceTests.add(new IndTestFisherZ(dataModel, params.getDouble("alpha",
                        0.001)));
            }

            return new IndTestMulti(independenceTests, ResolveSepsets.Method.tippett);
        }

        if (IndTestType.FISHER == testType) {
            return new IndTestFisherZFisherPValue(dataSets, params.getDouble("alpha", 0.001));
        }

        if (IndTestType.SEM_BIC == testType) {
            List<Score> scores = new ArrayList<>();
            for (DataSet dataSet : dataSets) {
                SemBicScore _score = new SemBicScore(dataSet, precomputeCovariances);
                scores.add(_score);
            }

            ImagesScore imagesScore = new ImagesScore(scores);
            return new ScoreIndTest(imagesScore);
        }

        {
            return new IndTestFisherZConcatenateResiduals(dataSets, params.getDouble("alpha", 0.001));
        }
    }

    private IndependenceTest getDiscreteTest(DataSet dataDiscrete, Parameters params, IndTestType testType) {
        if (IndTestType.G_SQUARE == testType) {
            return new IndTestGSquare(dataDiscrete, params.getDouble("alpha", 0.001));
        }
        if (IndTestType.CHI_SQUARE == testType) {
            return new IndTestChiSquare(dataDiscrete, params.getDouble("alpha", 0.001));
        }
        if (IndTestType.MIXED_MLR == testType) {
            return new IndTestMultinomialLogisticRegression(dataDiscrete, params.getDouble("alpha", 0.001));
        } else {
            params.set("indTestType", IndTestType.CHI_SQUARE);
            return new IndTestChiSquare(dataDiscrete, params.getDouble("alpha", 0.001));
        }
    }

    private IndependenceTest getGraphTest(Graph graph, Parameters params,
                                          IndTestType testType) {
        if (IndTestType.M_SEPARATION != testType) {
            params.set("indTestType", IndTestType.M_SEPARATION);
        }
        return new MsepTest(graph);
    }

    private IndependenceTest getCovMatrixTest(ICovarianceMatrix covMatrix,
                                              Parameters params) {
        return new IndTestFisherZ(covMatrix,
                params.getDouble("alpha", 0.001));
    }

    /**
     * <p>Setter for the field <code>precomputeCovariances</code>.</p>
     *
     * @param precomputeCovariances a boolean
     */
    public void setPrecomputeCovariances(boolean precomputeCovariances) {
        this.precomputeCovariances = precomputeCovariances;
    }
}





