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

package mycomparisons;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithms.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithms.mixed.pattern.MixedFgs2Bdeu;
import edu.cmu.tetrad.algcomparison.algorithms.mixed.pattern.MixedFgs2Sem;
import edu.cmu.tetrad.algcomparison.algorithms.oracle.pattern.*;
import edu.cmu.tetrad.algcomparison.experimental.ContinuousGpc;
import edu.cmu.tetrad.algcomparison.experimental.MixedWfgs;
import edu.cmu.tetrad.algcomparison.experimental.MixedWgfci;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.score.DiscreteBicScore;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.ContinuousSemThenDiscretizeSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Parameters;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
public class RichardComparison {
    public static void main(String... args) {

        Parameters parameters = new Parameters();

        parameters.put("numRuns", 10);
        parameters.put("sampleSize", 50, 100, 500, 1000, 5000);
        parameters.put("numMeasures", 10, 50);
        parameters.put("edgeFactor", 2, 5);
        parameters.put("numCategories", 2, 4, 6);
        parameters.put("variance", 0.0, 0.01, 0.1, 0.2, 0.3, 0.4, 0.5);
        parameters.put("percentDiscreteForMixedSimulation", 100);

//        parameters.putDouble("alpha", 5e-3);
//        parameters.put("alpha", 1e-4);
//
//        parameters.put("penaltyDiscount", 4);
//
//        parameters.put("fgsDepth", -1);
//        parameters.put("printGraphs", 0);
//
//        parameters.put("scaleFreeAlpha", .1);
//        parameters.put("scaleFreeBeta", .8);
//        parameters.put("scaleFreeDeltaIn", 3.0);
//        parameters.put("scaleFreeDeltaOut", 3.0);
//        parameters.put("samplePrior", 1);
//        parameters.put("structurePrior", 1);
//
//        parameters.put("mgmParam1", 0.1);
//        parameters.put("mgmParam2", 0.1);
//        parameters.put("mgmParam3", 0.1);

//        parameters.put("percentDiscreteForMixedSimulation", 50);
        //        parameters.put("printGraphs", 1);

//        List<Statistic> stats = new ArrayList<>();
//
//        stats.add(new AdjacencyPrecisionStat());
//        stats.add(new AdjacencyRecallStat());
//        stats.add(new ArrowheadPrecisionStat());
//        stats.add(new ArrowheadRecallStat());
//        stats.add(new MathewsCorrAdjStat());
//        stats.add(new MathewsCorrArrowStat());
//        stats.add(new F1AdjStat());
//        stats.add(new F1ArrowStat());
//        stats.add(new ShdStat());
//        stats.add(new ElapsedTimeStat());
//
//        Map<String, Double> statWeights = new LinkedHashMap<>();
//        statWeights.put("AP", 1.0);
//        statWeights.put("AR", 1.0);
//        statWeights.put("OP", 1.0);
//        statWeights.put("OR", 1.0);
//        statWeights.put("McAdj", 1.0);
//        statWeights.put("McOr", 0.5);
//        statWeights.put("F1Adj", 1.0);
//        statWeights.put("F1Or", 0.5);
//        statWeights.put("SHD", 1.0);
//        statWeights.put("E", .2);

//        List<Algorithm> algorithms = getFullAlgorithmsList();
//        List<Algorithm> algorithms = getSpecialSet();
//
//        new Comparison().compareAlgorithms(parameters, statWeights, algorithms, stats, simulation,
//                "comparison/Comparison.txt");
//        new Comparison().saveDataSetAndGraphs("comparison/save1", simulation, parameters);

        Simulation simulation = new ContinuousSemThenDiscretizeSimulation();
        new Comparison().saveDataSetAndGraphs("comparison/saveRichard2", simulation,
                parameters);
    }

    private static List<Algorithm> getSpecialSet() {
        List<Algorithm> algorithms = new ArrayList<>();

//        algorithms.add(new MixedFgs2Sem());

//        algorithms.add(new MixedFgs2Bdeu());
//        algorithms.add(new MixedFgs2Bic());
//
//        algorithms.add(new MixedWfgs());

//        algorithms.add(new Pc(IndTestType.MIXED_CG_LRT));
//        algorithms.add(new Cpc(IndTestType.MIXED_CG_LRT));
////
//        algorithms.add(new Cpc(IndTestType.MIXED_REGR_LRT)); //*
//
////        algorithms.add(new MixedGfciCG());
////
//        algorithms.add(new MixedGpcCg());
//        algorithms.add(new Pc(IndTestType.MIXED_CG_LRT));
//        algorithms.add(new Cpc(IndTestType.MIXED_CG_LRT)); //*
//        algorithms.add(new Pcs(IndTestType.MIXED_CG_LRT));
//        algorithms.add(new Cpc(IndTestType.MIXED_CG_LRT));
//        algorithms.add(new Fci(IndTestType.MIXED_CG_LRT));
//

//        algorithms.add(new ContinuousCpcFgs());
//        algorithms.add(new ContinuousFgs2());


        return algorithms;
    }

    private static List<Algorithm> getFullAlgorithmsList() {
        List<Algorithm> algorithms = new ArrayList<>();

//        // Pattern
        algorithms.add(new Pc(new FisherZ()));
        algorithms.add(new Cpc(new FisherZ()));
        algorithms.add(new Pcs(new FisherZ()));
        algorithms.add(new Cpcs(new FisherZ()));

        algorithms.add(new Fgs(new SemBicScore()));
        algorithms.add(new Fgs2(new SemBicScore()));

        algorithms.add(new ContinuousGpc());

        algorithms.add(new Fgs2(new BdeuScore()));
        algorithms.add(new Fgs2(new DiscreteBicScore()));

        //21
        algorithms.add(new MixedFgs2Sem());
        algorithms.add(new MixedFgs2Bdeu());

        algorithms.add(new MixedWfgs());
        algorithms.add(new MixedWgfci());

//        Cyclic PAG
//        algorithms.add(new ContinuousCcd());
        return algorithms;
    }

}




