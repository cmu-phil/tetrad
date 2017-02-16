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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.csb.mgm.IndTestMultinomialLogisticRegressionWald;

import java.util.ArrayList;
import java.util.List;

/**
 * Chooses an independence test for a particular data source.
 *
 * @author Joseph Ramsey
 */
final class IndTestChooser {
    public IndependenceTest getTest(Object dataSource, Parameters params) {
        return getTest(dataSource, params, IndTestType.DEFAULT);
    }

    /**
     * @return an independence checker appropriate to the given data source.
     * Also sets the Parameters on the params to an appropriate type object
     * (using the existing one if it's of the right type).
     */
    public IndependenceTest getTest(Object dataSource, Parameters params,
                                    IndTestType testType) {

        if (dataSource == null) {
            throw new NullPointerException();
        }
        if (params == null) {
            throw new NullPointerException();
        }

        if (dataSource instanceof DataModelList) {
            DataModelList datasets = (DataModelList) dataSource;

            List<DataSet> _dataSets = new ArrayList<>();

            for (DataModel dataModel : datasets) {
                _dataSets.add((DataSet) dataModel);
            }

            return getMultiContinuousTest(_dataSets, params, testType);
        }

        if (dataSource instanceof DataSet) {
            DataSet dataSet = (DataSet) dataSource;

            if (dataSet.isContinuous() || dataSet.getNumColumns() == 0) {
                DataSet dataContinuous =
                        (DataSet) dataSource;

//                if (dataContinuous.isMulipliersCollapsed()) {
//                    dataContinuous = new CaseExpander().filter(dataSet);
//                }

                return getContinuousTest(dataContinuous, params, testType);
            } else if (dataSet.isDiscrete()) {
                DataSet dataDiscrete =
                        (DataSet) dataSource;

//                if (dataDiscrete.isMulipliersCollapsed()) {
//                    dataDiscrete = new CaseExpander().filter(dataSet);
//                }

                return getDiscreteTest(dataDiscrete, params, testType);
            }
            if (dataSet.isMixed()) {
                DataSet dataMixed = (DataSet) dataSource;
//                if (dataMixed.isMulipliersCollapsed()) {
//                    dataMixed = new CaseExpander().filter(dataSet);
//                }

                return getMixedTest(dataMixed, params, testType);
            }
        }

        if (dataSource instanceof Graph) {
            return getGraphTest((Graph) dataSource, params,
                    IndTestType.D_SEPARATION);
        }
        if (dataSource instanceof ICovarianceMatrix) {
            return getCovMatrixTest((ICovarianceMatrix) dataSource, params);
        }

        if (dataSource instanceof TimeSeriesData) {
            return timeSeriesTest((TimeSeriesData) dataSource, params);
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
        if (IndTestType.FISHER_ZD == testType) {
            return new IndTestFisherZGeneralizedInverse(dataSet, params.getDouble("alpha", 0.001));
        }
        if (IndTestType.FISHER_Z_BOOTSTRAP == testType) {
            return new IndTestFisherZBootstrap(dataSet, params.getDouble("alpha", 0.001), 15, dataSet.getNumRows());
        }
        if (IndTestType.LINEAR_REGRESSION == testType) {
            return new IndTestLaggedRegression(dataSet,
                    params.getDouble("alpha", 0.001), 1);
        }
        if (IndTestType.SEM_BIC == testType) {
            return new IndTestScore(new SemBicScore(new CovarianceMatrixOnTheFly(dataSet)));
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
            for (DataModel dataModel : dataSets) {
                DataSet dataSet = (DataSet) dataModel;
                independenceTests.add(new IndTestFisherZ(dataSet, params.getDouble("alpha", 0.001)));
            }

            return new IndTestMulti(independenceTests, ResolveSepsets.Method.tippett);
        }

        if (IndTestType.FISHER == testType) {
            return new IndTestFisherZFisherPValue(dataSets, params.getDouble("alpha", 0.001));
        }

        if (IndTestType.SEM_BIC == testType) {
            List<DataModel> dataModels = new ArrayList<>();
            for (DataSet dataSet : dataSets) dataModels.add(dataSet);
            return new IndTestScore(new SemBicScoreImages(dataModels));
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
        if (IndTestType.D_SEPARATION == testType) {
            return new IndTestDSep(graph);
        } else {
            params.set("indTestType", IndTestType.D_SEPARATION);
            return new IndTestDSep(graph);
        }
    }

    private IndependenceTest getCovMatrixTest(ICovarianceMatrix covMatrix,
                                              Parameters params) {
        return new IndTestFisherZ(covMatrix,
                params.getDouble("alpha", 0.001));
    }

    private IndependenceTest timeSeriesTest(TimeSeriesData data,
                                            Parameters params) {
        IndTestTimeSeries test = new IndTestTimeSeries(data.getData(), data.getVariables());
        test.setAlpha(params.getDouble("alpha", 0.001));
        test.setNumLags(params.getInt("numLags", 1));
        return test;
    }
}





