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

package edu.cmu.tetrad.study.Conditions;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.external.*;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fges;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;

/**
 * An example script to load in data sets and graphs from files and analyze them. The
 * files loaded must be in the same format as
 * </p>
 * new Comparison().saveDataSetAndGraphs("comparison/save1", simulation, parameters);
 * </p>
 * saves them. For other formats, specialty data loaders can be written to implement the
 * Simulation interface.
 *
 * @author jdramsey
 */
public class Condition1 {
    public void generateTetradResults() {
        Parameters parameters = new Parameters();

        parameters.set("alpha", 0.001);
        parameters.set("numRuns", 10);
//        parameters.set("penaltyDiscount", 4);
        parameters.set("useMaxPOrientationHeuristic", true);
        parameters.set("maxPOrientationMaxPathLength", 3);

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("numMeasures"));
        statistics.add(new ParameterColumn("avgDegree"));
        statistics.add(new ParameterColumn("sampleSize"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new ElapsedTime());
//        statistics.add(new PercentBidirectedEdges());
//        statistics.add(new NumAmbiguousTriples());

        statistics.setWeight("AP", 1.0);
        statistics.setWeight("AR", 0.5);
        statistics.setWeight("AHP", 1.0);
        statistics.setWeight("AHR", 0.5);

        Algorithms algorithms = new Algorithms();

//        algorithms.add(new Pc(new FisherZ()));
//        algorithms.add(new PcStable(new FisherZ()));
//        algorithms.add(new PcStableMax(new FisherZ(), false));
//        algorithms.add(new Cpc(new FisherZ()));
//        algorithms.add(new CpcStable(new FisherZ()));

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(true);
        comparison.setSaveGraphs(true);

//        comparison.compareFromFiles("/Users/user/comparison-data/condition_1",
//                "/Users/user/causal-comparisons/condition_1",
//                algorithms, statistics, parameters);
//
//        algorithms = new Algorithms();
//
//        parameters.set("alpha", 0.001, 0.0001, 1e-8);
//        parameters.set("faithfulnessAssumed", true);
////
//        algorithms.add(new Fges(new FisherZScore()));
//
//        comparison.compareFromFiles("/Users/user/comparison-data/condition_1",
//                "/Users/user/causal-comparisons/condition_1",
//                algorithms, statistics, parameters);
//
//        algorithms = new Algorithms();

        parameters.set("penaltyDiscount", 4);
//
        algorithms.add(new Fges(new SemBicScore()));

        comparison.compareFromFiles("/Users/user/comparison-data/condition_1",
                "/Users/user/causal-comparisons/condition_1",
                algorithms, statistics, parameters);
    }

    public void compileTable() {
        Parameters parameters = new Parameters();

        parameters.set("numRuns", 10);

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("numMeasures"));
        statistics.add(new ParameterColumn("avgDegree"));
        statistics.add(new ParameterColumn("sampleSize"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new F1Adj());
        statistics.add(new F1Arrow());
        statistics.add(new F1All());
        statistics.add(new ElapsedTime());

        statistics.setWeight("AP", 1.0);
        statistics.setWeight("AR", 0.5);
        statistics.setWeight("AHP", 1.0);
        statistics.setWeight("AHR", 0.5);

        Algorithms algorithms = new Algorithms();
//
//        algorithms.add(new ExternalAlgorithmTetrad("PC_(\"Peter_and_Clark\"),_Priority_Rule,_using_Fisher_Z_test,_alpha_=_0.001"));
//        algorithms.add(new ExternalAlgorithmTetrad("PC-Stable_(\"Peter_and_Clark\"_Stable),_Priority_Rule,_using_Fisher_Z_test,_alpha_=_0.001"));
//        algorithms.add(new ExternalAlgorithmTetrad("PC-Stable-Max_(\"Peter_and_Clark\"),_Priority_Rule,_using_Fisher_Z_test,_alpha_=_0.001"));
//        algorithms.add(new ExternalAlgorithmTetrad("CPC_(Conservative_\"Peter_and_Clark\"),_Priority_Rule,_using_Fisher_Z_test,_alpha_=_0.001"));
//        algorithms.add(new ExternalAlgorithmTetrad("CPC-Stable_(Conservative_\"Peter_and_Clark\"_Stable),_Priority_Rule,_using_Fisher_Z_test,_alpha_=_0.001"));
//
//        algorithms.add(new ExternalAlgorithmTetrad("FGES_(Fast_Greedy_Equivalence_Search)_using_Fisher_Z_Score,_alpha_=_0.001"));
//        algorithms.add(new ExternalAlgorithmTetrad("FGES_(Fast_Greedy_Equivalence_Search)_using_Fisher_Z_Score,_alpha_=_1.0E-4"));
//        algorithms.add(new ExternalAlgorithmTetrad("FGES_(Fast_Greedy_Equivalence_Search)_using_Fisher_Z_Score,_alpha_=_1.0E-8"));

        algorithms.add(new ExternalAlgorithmTetrad("FGES_(Fast_Greedy_Equivalence_Search)_using_Sem_BIC_Score,_penaltyDiscount_=_1"));
        algorithms.add(new ExternalAlgorithmTetrad("FGES_(Fast_Greedy_Equivalence_Search)_using_Sem_BIC_Score,_penaltyDiscount_=_2"));
        algorithms.add(new ExternalAlgorithmTetrad("FGES_(Fast_Greedy_Equivalence_Search)_using_Sem_BIC_Score,_penaltyDiscount_=_4"));

//        algorithms.add(new ExternalAlgorithmBnlearnMmhc("MMPC_alpha_=_0.001"));
//        algorithms.add(new ExternalAlgorithmBnlearnMmhc("GrowShrink_alpha_=_0.001"));
//        algorithms.add(new ExternalAlgorithmBnlearnMmhc("IAMB_alpha_=_0.001"));
//        algorithms.add(new ExternalAlgorithmBnlearnMmhc("Fast.IAMB_alpha_=_0.001"));
//        algorithms.add(new ExternalAlgorithmBnlearnMmhc("Inter.IAMB_alpha_=_0.001"));
//        algorithms.add(new ExternalAlgorithmBnlearnMmhc("si.hiton.pc_alpha_=_0.001"));
//        algorithms.add(new ExternalAlgorithmBnlearnMmhc("MMHC_alpha_=_0.001"));
//
//        algorithms.add(new ExternalAlgorithmBnlearnMmhc("iamb_alpha_=_0.001.test=cor"));
//        algorithms.add(new ExternalAlgorithmBnlearnMmhc("iamb_alpha_=_0.001.test=mc-cor"));
//        algorithms.add(new ExternalAlgorithmBnlearnMmhc("iamb_alpha_=_0.001.test=mc-mi-g"));
//        algorithms.add(new ExternalAlgorithmBnlearnMmhc("iamb_alpha_=_0.001.test=mc-zf"));
//        algorithms.add(new ExternalAlgorithmBnlearnMmhc("iamb_alpha_=_0.001.test=mi-g"));
//        algorithms.add(new ExternalAlgorithmBnlearnMmhc("iamb_alpha_=_0.001.test=mi-g-sh"));
//        algorithms.add(new ExternalAlgorithmBnlearnMmhc("iamb_alpha_=_0.001.test=smc-cor"));
//        algorithms.add(new ExternalAlgorithmBnlearnMmhc("iamb_alpha_=_0.001.test=smc-mi-g"));
//        algorithms.add(new ExternalAlgorithmBnlearnMmhc("iamb_alpha_=_0.001.test=smc-zf"));
//        algorithms.add(new ExternalAlgorithmBnlearnMmhc("iamb_alpha_=_0.001.test=zf"));
//
//        algorithms.add(new ExternalAlgorithmPcalgPc("PC_pcalg_defaults_alpha_=_0.001"));
//        algorithms.add(new ExternalAlgorithmPcalgPc("PC_pcalg_defaults_alpha_=_0.001.parallize.dynamic"));
//
//        algorithms.add(new ExternalAlgorithmPcalgPc("PC-Stable_pcalg_defaults_alpha_=_0.001"));
//        algorithms.add(new ExternalAlgorithmPcalgPc("PC-Stable_pcalg_ncores=4_alpha_=_0.001"));
////        algorithms.add(new ExternalAlgorithmPcalgPc("CPC_pcalg_defaults_alpha_=_0.001"));
//        algorithms.add(new ExternalAlgorithmPcalgPc("CPC_pcalg_majority.rule_defaults_alpha_=_0.001"));
        algorithms.add(new ExternalAlgorithmPcalgGes("GES_pcalg_defaults_1.0*log(nrow(data)"));
        algorithms.add(new ExternalAlgorithmPcalgGes("GES_pcalg_defaults_2.0*log(nrow(data)"));

//        algorithms.add(new ExternalAlgorithmBNTPc("learn_struct_pdag_pc_alpha_=_0.001"));

//        algorithms.add(new ExternalAlgorithmIntersection("Intersection (Tetrad) CPC, PC_Stable-Max, CPC-Stable",
//                new ExternalAlgorithmTetrad("CPC_(Conservative_\"Peter_and_Clark\"),_Priority_Rule,_using_Fisher_Z_test,_alpha_=_0.001"),
//                new ExternalAlgorithmTetrad("PC-Stable-Max_(\"Peter_and_Clark\"),_Priority_Rule,_using_Fisher_Z_test,_alpha_=_0.001"),
//                new ExternalAlgorithmTetrad("CPC-Stable_(Conservative_\"Peter_and_Clark\"_Stable),_Priority_Rule,_using_Fisher_Z_test,_alpha_=_0.001")
//
//        ));
//
//        algorithms.add(new ExternalAlgorithmIntersection("Intersection FGES alpha .001, 1e-8, penalty 2",
//                new ExternalAlgorithmTetrad("FGES_(Fast_Greedy_Equivalence_Search)_using_Fisher_Z_Score,_alpha_=_0.001"),
//                new ExternalAlgorithmTetrad("FGES_(Fast_Greedy_Equivalence_Search)_using_Fisher_Z_Score,_alpha_=_1.0E-8"),
//                new ExternalAlgorithmTetrad("FGES_(Fast_Greedy_Equivalence_Search)_using_Sem_BIC_Score,_penalty_discount_=_2.0")
//        ));
//
//        algorithms.add(new ExternalAlgorithmIntersection("Intersection PC, CPC, CPC majority, pcalg",
//                new ExternalAlgorithmPcalgPc("PC_pcalg_defaults_alpha_=_0.001"),
//                new ExternalAlgorithmPcalgPc("CPC_pcalg_defaults_alpha_=_0.001"),
//                new ExternalAlgorithmPcalgPc("CPC_majority_pcalg_defaults_alpha_=_0.001")
//        ));

        Comparison comparison = new Comparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setSaveGraphs(true);
        comparison.setComparisonGraph(Comparison.ComparisonGraph.Pattern_of_the_true_DAG);

        comparison.generateReportFromExternalAlgorithms("/Users/user/comparison-data/condition_1",
                "/Users/user/causal-comparisons/condition_1",
                algorithms, statistics, parameters);

    }

    public static void main(String... args) {
        new Condition1().compileTable();
    }
}




