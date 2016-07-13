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

package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.algcomparison.Parameters;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.*;
import edu.pitt.csb.mgm.IndTestMultinomialLogisticRegressionWald;

import java.util.ArrayList;
import java.util.List;

/**
 * A taxonomy of independence tests available in Tetrad. Algorithms that take independence
 * tests as oracles can use these. The type of test must of course be matched to the type
 * of data; continuous tests can only be used with continuous data, discrete test with
 * discrete data. Mixed tests can be used with any type o data.
 * @author jdramsey
 */
public final class IndTestChooser {

    /**
     * @return an independence checker appropriate to the given data source.
     * Also sets the IndTestParams on the params to an appropriate type object
     * (using the existing one if it's of the right type).
     */
    public IndependenceTest getTest(IndTestType testType, DataModel dataModel, Parameters params) {

        if (dataModel == null) {
            throw new NullPointerException();
        }
        if (params == null) {
            throw new NullPointerException();
        }

        if (dataModel instanceof DataModelList) {
            DataModelList datasets = (DataModelList) dataModel;

            List<DataSet> _dataSets = new ArrayList<>();

            for (DataModel _dataModel : datasets) {
                _dataSets.add((DataSet) _dataModel);
            }

            return getMultiContinuousTest(_dataSets, params, testType);
        }

        if (dataModel instanceof DataSet) {
            DataSet dataSet = (DataSet) dataModel;

            if (dataSet.isContinuous() || dataSet.getNumColumns() == 0) {
                DataSet dataContinuous =
                        (DataSet) dataModel;

                if (dataContinuous.isMulipliersCollapsed()) {
                    dataContinuous = new CaseExpander().filter(dataSet);
                }

                return getContinuousTest(dataContinuous, params, testType);
            } else if (dataSet.isDiscrete()) {
                DataSet dataDiscrete = (DataSet) dataModel;
                if (dataDiscrete.isMulipliersCollapsed()) {
                    dataDiscrete = new CaseExpander().filter(dataSet);
                }

                return getDiscreteTest(dataDiscrete, params, testType);
            } else if (dataSet.isMixed()) {
                DataSet dataMixed = (DataSet) dataModel;
                if (dataMixed.isMulipliersCollapsed()) {
                    dataMixed = new CaseExpander().filter(dataSet);
                }

                return getMixedTest(dataMixed, params, testType);
            }
        }

        if (dataModel instanceof Graph) {
            return getGraphTest((Graph) dataModel, IndTestType.D_SEPARATION);
        } else if (dataModel instanceof ICovarianceMatrix) {
            return getCovMatrixTest((ICovarianceMatrix) dataModel, params);
        } else if (dataModel instanceof IndependenceFacts) {
            return new IndTestIndependenceFacts((IndependenceFacts) dataModel);
        }

        throw new IllegalStateException(
                "Unrecognized data source type: " + dataModel.getClass());
    }

    private IndependenceTest getMixedTest(DataSet dataSet,
                                          Parameters params, IndTestType testType) {
        if (IndTestType.MULTINOMIAL_LOGISTIC_REGRESSION == testType) {
            return new IndTestMultinomialLogisticRegressionWald(dataSet, params.getDouble("alpha"), false);
        } else if (IndTestType.LINEAR_REGRESSION == testType) {
            return new IndTestRegression(dataSet, params.getDouble("alpha"));
        } else {
            return new IndTestMultinomialLogisticRegression(dataSet, params.getDouble("alpha"));
        }
    }

    private IndependenceTest getContinuousTest(DataSet dataSet, Parameters params, IndTestType testType) {
        if (IndTestType.CONDITIONAL_CORRELATION == testType) {
            return new IndTestConditionalCorrelation(dataSet, params.getDouble("alpha"));
        }
        if (IndTestType.FISHER_Z == testType) {
            return new IndTestFisherZ(dataSet, params.getDouble("alpha"));
        }
        if (IndTestType.FISHER_ZD == testType) {
            return new IndTestFisherZGeneralizedInverse(dataSet, params.getDouble("alpha"));
        }
        if (IndTestType.FISHER_Z_BOOTSTRAP == testType) {
            return new IndTestFisherZBootstrap(dataSet, params.getDouble("alpha"), 15, dataSet.getNumRows());
        }
        if (IndTestType.LINEAR_REGRESSION == testType) {
//            return new IndTestBicBump(dataSet, indTestParams.getParameter1() * 100);
            return new IndTestLaggedRegression(dataSet, params.getDouble("alpha"), 1);
        }
        if (IndTestType.BIC_BUMP == testType) {
            return new IndTestScore(new SemBicScore(new CovarianceMatrixOnTheFly(dataSet)),
                    params.getDouble("alpha"));
        }

        {
            return new IndTestFisherZ(dataSet, params.getDouble("alpha"));
        }
    }

    private IndependenceTest getMultiContinuousTest(List<DataSet> dataSets,
                                                    Parameters params, IndTestType testType) {
        if (IndTestType.POOL_RESIDUALS_FISHER_Z == testType) {
            return new IndTestFisherZPercentIndependent(dataSets, params.getDouble("alpha"));
        } else if (IndTestType.TIPPETT == testType) {
            List<IndependenceTest> independenceTests = new ArrayList<>();

            for (DataModel dataModel : dataSets) {
                DataSet dataSet = (DataSet) dataModel;
                independenceTests.add(new IndTestFisherZ(dataSet, params.getDouble("alpha")));
            }

            return new IndTestMulti(independenceTests, ResolveSepsets.Method.tippett);
        } else if (IndTestType.FISHER == testType) {
            return new IndTestFisherZFisherPValue(dataSets, params.getDouble("alpha"));
        } else if (IndTestType.BIC_BUMP == testType) {
            List<DataModel> dataModels = new ArrayList<>();
            for (DataSet dataSet : dataSets) dataModels.add(dataSet);
            return new IndTestScore(new SemBicScoreImages(dataModels), params.getDouble("alpha"));
        }

        return new IndTestFisherZConcatenateResiduals(dataSets, params.getDouble("alpha"));
    }

    private IndependenceTest getDiscreteTest(DataSet dataDiscrete,
                                             Parameters params, IndTestType testType) {
        if (IndTestType.G_SQUARE == testType) {
            return new IndTestGSquare(dataDiscrete, params.getDouble("alpha"));
        } else if (IndTestType.CHI_SQUARE == testType) {
            return new IndTestChiSquare(dataDiscrete, params.getDouble("alpha"));
        } else if (IndTestType.MULTINOMIAL_LOGISTIC_REGRESSION == testType) {
            return new IndTestMultinomialLogisticRegression(dataDiscrete, params.getDouble("alpha"));
        } else {
            return new IndTestChiSquare(dataDiscrete, params.getDouble("alpha"));
        }
    }

    private IndependenceTest getGraphTest(Graph graph, IndTestType testType) {
        if (IndTestType.D_SEPARATION == testType) {
            return new IndTestDSep(graph);
        } else {
            return new IndTestDSep(graph);
        }
    }

    private IndependenceTest getCovMatrixTest(ICovarianceMatrix covMatrix, Parameters params) {
        return new IndTestFisherZ(covMatrix,
                params.getDouble("alpha"));
    }
}





