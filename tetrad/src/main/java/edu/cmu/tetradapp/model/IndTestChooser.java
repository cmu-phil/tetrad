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

import java.util.ArrayList;
import java.util.List;

/**
 * Chooses an independence test for a particular data source.
 *
 * @author Joseph Ramsey
 */
public final class IndTestChooser {
    public IndependenceTest getTest(Object dataSource, SearchParams params) {
        return getTest(dataSource, params, IndTestType.DEFAULT);
    }

    /**
     * @return an independence checker appropriate to the given data source.
     * Also sets the IndTestParams on the params to an appropriate type object
     * (using the existing one if it's of the right type).
     */
    public IndependenceTest getTest(Object dataSource, SearchParams params,
            IndTestType testType) {

        if (dataSource == null) {
            throw new NullPointerException();
        }
        if (params == null) {
            throw new NullPointerException();
        }

        IndTestParams indTestParams = params.getIndTestParams();
        if (indTestParams == null) {
            indTestParams = new BasicIndTestParams();
            params.setIndTestParams2(indTestParams);
        }

        if (dataSource instanceof DataModelList) {
            DataModelList datasets = (DataModelList) dataSource;

            List<DataSet> _dataSets = new ArrayList<DataSet>();

            for (DataModel dataModel : datasets) {
                _dataSets.add((DataSet) dataModel);
            }

            return getMultiContinuousTest(_dataSets, params, testType);

//            return new IndTestFisherZConcatenateResiduals(_dataSets, params.getIndTestParams().getAlpha());
        
//            return getMultiContinuousTest(_dataSets, params.getIndTestParams().getAlpha());
        }

        if (dataSource instanceof DataSet) {
            DataSet dataSet = (DataSet) dataSource;

            if (dataSet.isContinuous() || dataSet.getNumColumns() == 0) {
                DataSet dataContinuous =
                        (DataSet) dataSource;

                if (dataContinuous.isMulipliersCollapsed()) {
                    dataContinuous = new CaseExpander().filter(dataSet);
                }

                return getContinuousTest(dataContinuous, params, testType);
            }
            else if (dataSet.isDiscrete()) {
                DataSet dataDiscrete =
                        (DataSet) dataSource;

                if (dataDiscrete.isMulipliersCollapsed()) {
                    dataDiscrete = new CaseExpander().filter(dataSet);
                }

                return getDiscreteTest(dataDiscrete, params, testType);
            }
//            else if (dataSet.isMixed()) {
//                throw new IllegalArgumentException("Mixed data sets are not currently handled.");
//            }
            if (dataSet.isMixed()) {
                DataSet dataMixed = (DataSet) dataSource;
                if (dataMixed.isMulipliersCollapsed()) {
                    dataMixed = new CaseExpander().filter(dataSet);
                }

                return getMixedTest(dataMixed, params, testType);
            }
        }

        if (dataSource instanceof Graph) {
            return getGraphTest((Graph) dataSource, params,
                    IndTestType.D_SEPARATION);
        }
        if (dataSource instanceof ICovarianceMatrix) {
            return getCovMatrixTest((ICovarianceMatrix) dataSource, params,
                    testType);
        }
//        if (dataSource instanceof CorrelationMatrix) {
//            return getCorrMatrixTest((CovarianceMatrix) dataSource, params,
//                    testType);
//        }

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
            SearchParams params, IndTestType testType) {
        IndTestParams indTestParams = params.getIndTestParams();

        if (IndTestType.MULTINOMIAL_LOGISTIC_REGRESSION == testType) {
            return new IndTestMultinomialLogisticRegression(dataSet, indTestParams.getAlpha());
        }
        if (IndTestType.LOGISTIC_REGRESSION == testType) {
            return new IndTestLogisticRegression(dataSet, indTestParams.getAlpha());
        }
        if (IndTestType.LINEAR_REGRESSION == testType) {
            return new IndTestRegression(dataSet,
                    indTestParams.getAlpha());
        }
        else {
            params.setIndTestType(IndTestType.MULTINOMIAL_LOGISTIC_REGRESSION);
            return new IndTestMultinomialLogisticRegression(dataSet, indTestParams.getAlpha());
        }
    }

    private IndependenceTest getContinuousTest(DataSet dataSet,
            SearchParams params, IndTestType testType) {
        IndTestParams indTestParams = params.getIndTestParams();

//        if (IndTestType.CORRELATION_T == testType) {
////            return new ProbabilisticIndependence(dataSet);
//            return new IndTestCorrelationT(dataSet, indTestParams.getAlpha());
//        }
        if (IndTestType.CONDITIONAL_CORRELATION == testType) {
            return new IndTestConditionalCorrelation(dataSet, indTestParams.getAlpha());
        }
        if (IndTestType.FISHER_Z == testType) {
            return new IndTestFisherZ(dataSet, indTestParams.getAlpha());  /// todo revert
//            return new IndTestFisherZ3(dataSet, indTestParams.getAlpha());
        }
        if (IndTestType.FISHER_ZD == testType)  {
            return new IndTestFisherZGeneralizedInverse(dataSet, indTestParams.getAlpha());
        }
        if (IndTestType.FISHER_Z_BOOTSTRAP == testType) {
            return new IndTestFisherZBootstrap(dataSet, indTestParams.getAlpha(), 15, dataSet.getNumRows());
        }
        if (IndTestType.LINEAR_REGRESSION == testType) {
            return new IndTestLaggedRegression(dataSet,
                    indTestParams.getAlpha(), 1);
        }
        else {
            params.setIndTestType(IndTestType.FISHER_Z);
            return new IndTestFisherZ(dataSet, indTestParams.getAlpha());
        }
    }

    private IndependenceTest getMultiContinuousTest(List<DataSet> dataSets,
            SearchParams params, IndTestType testType) {
        if (IndTestType.POOL_RESIDUALS_FISHER_Z == testType) {
//            return new IndTestFisherZConcatenateResiduals(dataSets, params.getIndTestParams().getAlpha());
            return new IndTestFisherZPercentIndependent(dataSets, params.getIndTestParams().getAlpha());
//            return new IndTestFisherZConcatenateResiduals3(dataSets, params.getIndTestParams().getAlpha());
        }
        else if (IndTestType.TIPPETT == testType) {
            List<IndependenceTest> independenceTests = new ArrayList<IndependenceTest>();
            for (DataModel dataModel : dataSets) {
                DataSet dataSet = (DataSet) dataModel;
                independenceTests.add(new IndTestFisherZ(dataSet, params.getIndTestParams().getAlpha()));
            }

            return new IndTestMulti(independenceTests, ResolveSepsets.Method.tippett);
        }
        else if (IndTestType.FISHER == testType) {
//            List<IndependenceTest> independenceTests = new ArrayList<IndependenceTest>();
//            for (DataModel dataModel : dataSets) {
//                DataSet dataSet = (DataSet) dataModel;
//                independenceTests.add(new IndTestFisherZ(dataSet, params.getIndTestParams().getAlpha()));
//            }
//
//            return new IndTestMulti(independenceTests, ResolveSepsets.Method.fisher2);
            return new IndTestFisherZFisherPValue(dataSets, params.getIndTestParams().getAlpha());
        }
        else {
            return new IndTestFisherZConcatenateResiduals(dataSets, params.getIndTestParams().getAlpha());
        }
    }

    private IndependenceTest getDiscreteTest(DataSet dataDiscrete,
            SearchParams params, IndTestType testType) {
        IndTestParams indTestParams = params.getIndTestParams();

        if (IndTestType.G_SQUARE == testType) {
            return new IndTestGSquare(dataDiscrete, indTestParams.getAlpha());
        }
        if (IndTestType.CHI_SQUARE == testType) {
            return new IndTestChiSquare(dataDiscrete, indTestParams.getAlpha());
        }
        if (IndTestType.MULTINOMIAL_LOGISTIC_REGRESSION == testType) {
            return new IndTestMultinomialLogisticRegression(dataDiscrete, indTestParams.getAlpha());
        }
        else {
            params.setIndTestType(IndTestType.CHI_SQUARE);
            return new IndTestChiSquare(dataDiscrete, indTestParams.getAlpha());
        }
    }

    private IndependenceTest getGraphTest(Graph graph, SearchParams params,
            IndTestType testType) {
        if (IndTestType.D_SEPARATION == testType) {
            return new IndTestDSep(graph);
        }
        else {
            params.setIndTestType(IndTestType.D_SEPARATION);
            return new IndTestDSep(graph);
        }
    }

    private IndependenceTest getCovMatrixTest(ICovarianceMatrix covMatrix,
            SearchParams params, IndTestType testType) {
//        if (IndTestType.CORRELATION_T == testType) {
//            return new IndTestCorrelationT(covMatrix,
//                    params.getIndTestParams().getAlpha());
//        }
//        if (IndTestType.FISHER_Z == testType) {
            return new IndTestFisherZ(covMatrix,
                    params.getIndTestParams().getAlpha());
//        }
//        else {
//            params.setIndTestType(IndTestType.CORRELATION_T);
//            return new IndTestCorrelationT(covMatrix, params.getIndTestParams().getAlpha());
//        }
    }

//    private IndependenceTest getCorrMatrixTest(CovarianceMatrix covMatrix,
//            SearchParams params, IndTestType testType) {
//        if (IndTestType.CORRELATION_T == testType) {
//            return new IndTestCramerT(covMatrix,
//                    params.getIndTestParams().getAlpha());
//        }
//        if (IndTestType.FISHER_Z == testType) {
//            return new IndTestFisherZ(covMatrix,
//                    params.getIndTestParams().getAlpha());
//        }
//        else {
//            params.setIndTestType(IndTestType.CORRELATION_T);
//            return new IndTestCramerT(covMatrix,
//                    params.getIndTestParams().getAlpha());
//        }
//    }

    private IndependenceTest timeSeriesTest(TimeSeriesData data,
            SearchParams params) {
        IndTestParams indTestParams = params.getIndTestParams();
        if (!(indTestParams instanceof LagIndTestParams) || !(
                getOldNumTimePoints(indTestParams) == data.getNumTimePoints()))
        {
            indTestParams = new LagIndTestParams();
            ((LagIndTestParams) indTestParams).setNumTimePoints(
                    data.getData().rows());
            params.setIndTestParams2(indTestParams);
        }
        IndTestTimeSeries test =
                new IndTestTimeSeries(data.getData(), data.getVariables());
        test.setAlpha(indTestParams.getAlpha());
        test.setNumLags(((LagIndTestParams) indTestParams).getNumLags());
        return test;
    }

    /**
     * Finds an independence checker appropriate to the given data source.
     * Also sets the IndTestParams on the params to an appropriate type
     * dataSource (using the existing one if it's of the right type).
     */
    public void adjustIndTestParams(Object dataSource, SearchParams params) {
        if (dataSource instanceof DataSet) {
            DataSet dataSet = (DataSet) dataSource;

            if (dataSet.isContinuous()) {
                IndTestParams indTestParams = params.getIndTestParams();
                if (indTestParams == null) {
                    indTestParams = new BasicIndTestParams();
                    params.setIndTestParams2(indTestParams);
                }
                return;
            }
            else if (dataSet.isDiscrete()) {
                IndTestParams indTestParams = params.getIndTestParams();
                if (indTestParams == null) {
                    indTestParams = new BasicIndTestParams();
                    params.setIndTestParams2(indTestParams);
                }
                return;
            } else if (dataSet.isMixed()) {
                IndTestParams indTestParams = params.getIndTestParams();
                if (indTestParams == null) {
                    indTestParams = new BasicIndTestParams();
                    params.setIndTestParams2(indTestParams);
                }
                return;
            }
            else {
                throw new IllegalStateException("Tabular data must be either " +
                        "continuous or discrete.");
            }
        }

        if (dataSource instanceof CorrelationMatrix) {
            IndTestParams indTestParams = params.getIndTestParams();
            if (indTestParams == null) {
                indTestParams = new BasicIndTestParams();
                params.setIndTestParams2(indTestParams);
            }
            return;
        }

        if (dataSource instanceof ICovarianceMatrix) {
            IndTestParams indTestParams = params.getIndTestParams();
            if (indTestParams == null) {
                indTestParams = new BasicIndTestParams();
                params.setIndTestParams2(indTestParams);
            }
            return;
        }

        if (dataSource instanceof Graph) {
            IndTestParams indTestParams = params.getIndTestParams();
            if (indTestParams == null) {
                indTestParams = new GraphIndTestParams();
                params.setIndTestParams2(indTestParams);
            }
            return;
        }

        if (dataSource instanceof TimeSeriesData) {
            TimeSeriesData data = (TimeSeriesData) dataSource;
            IndTestParams indTestParams = params.getIndTestParams();
            if (indTestParams == null ||
                    !(indTestParams instanceof BasicIndTestParams) || !(
                    getOldNumTimePoints(indTestParams) ==
                            data.getNumTimePoints())) {
                indTestParams = new BasicIndTestParams();
                params.setIndTestParams2(indTestParams);
            }
            return;
        }

        if (dataSource instanceof IndependenceFacts) {
            IndTestParams indTestParams = params.getIndTestParams();
            if (indTestParams == null) {
                indTestParams = new BasicIndTestParams();
                params.setIndTestParams2(indTestParams);
            }
            return;            
        }

        // Assuming it's a list of continuous data sets...
        if (dataSource instanceof DataModelList) {
            IndTestParams indTestParams = params.getIndTestParams();
            if (indTestParams == null) {
                indTestParams = new BasicIndTestParams();
                params.setIndTestParams2(indTestParams);
            }
            return;            
        }

        throw new IllegalStateException("Unrecognized data type.");
    }


    private int getOldNumTimePoints(IndTestParams indTestParams) {
        return ((LagIndTestParams) indTestParams).getNumTimePoints();
    }
}





