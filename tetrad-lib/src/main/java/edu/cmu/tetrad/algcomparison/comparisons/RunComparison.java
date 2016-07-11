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

package edu.cmu.tetrad.algcomparison.comparisons;

import edu.cmu.tetrad.algcomparison.*;
import edu.cmu.tetrad.algcomparison.continuous.pag.*;
import edu.cmu.tetrad.algcomparison.continuous.pattern.*;
import edu.cmu.tetrad.algcomparison.discrete.pag.*;
import edu.cmu.tetrad.algcomparison.discrete.pattern.*;
import edu.cmu.tetrad.algcomparison.mixed.pag.*;
import edu.cmu.tetrad.algcomparison.mixed.pattern.*;
import edu.cmu.tetrad.algcomparison.simulation.*;
import edu.cmu.tetrad.algcomparison.statistic.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Joseph Ramsey
 */
public class RunComparison {
    public static void main(String... args) {

        Parameters parameters = new Parameters();

        parameters.putInt("numRuns", 1);
//        parameters.putInt("sampleSize", 5180);
//        parameters.putInt("numMeasures", 570);
        parameters.putInt("sampleSize", 1000);
        parameters.putInt("numMeasures", 500);
        parameters.putInt("numEdges", 2 * parameters.getInt("numMeasures"));
        parameters.putInt("numLatents", 0);
        parameters.putDouble("numCategories", 4);

//        parameters.putDouble("alpha", 5e-3);
        parameters.putDouble("alpha", 1e-4);

        parameters.putInt("penaltyDiscount", 4);

        parameters.putInt("fgsDepth", -1);
        parameters.putInt("printGraphs", 0);

        parameters.putDouble("scaleFreeAlpha", .1);
        parameters.putDouble("scaleFreeBeta", .8);
        parameters.putDouble("scaleFreeDeltaIn", 3.0);
        parameters.putDouble("scaleFreeDeltaOut", 3.0);
        parameters.putDouble("samplePrior", 1);
        parameters.putDouble("structurePrior", 1);

        parameters.putDouble("mgmParam1", 0.1);
        parameters.putDouble("mgmParam2", 0.1);
        parameters.putDouble("mgmParam3", 0.1);

        parameters.putDouble("percentDiscreteForMixedSimulation", 50);
//        parameters.putInt("printGraphs", 1);

        List<Statistic> stats = new ArrayList<>();

        stats.add(new AdjacencyPrecisionStat());
        stats.add(new AdjacencyRecallStat());
        stats.add(new ArrowPrecisionStat());
        stats.add(new ArrowRecallStat());
        stats.add(new MathewsCorrAdjStat());
        stats.add(new MathewsCorrArrowStat());
        stats.add(new F1AdjStat());
        stats.add(new F1ArrowStat());
        stats.add(new ShdStat());
        stats.add(new ElapsedTimeStat());

        Map<String, Double> statWeights = new LinkedHashMap<>();
//        statWeights.put("AP", 1.0);
//        statWeights.put("AR", 1.0);
//        statWeights.put("OP", 1.0);
//        statWeights.put("OR", 1.0);
//        statWeights.put("McAdj", 1.0);
//        statWeights.put("McOr", 0.5);
        statWeights.put("F1Adj", 1.0);
        statWeights.put("F1Or", 0.5);
//        statWeights.put("SHD", 1.0);
//        statWeights.put("E", .2);

//        List<Algorithm> algorithms = getFullAlgorithmsList();
        List<Algorithm> algorithms = getSpecialSet();
//
//        Simulation simulation = new MixedLeeHastieSimulation(parameters);
        Simulation simulation = new MixedSemThenDiscretizeHalfSimulation(parameters);

//        new Comparison().testBestAlgorithms(parameters, statWeights, algorithms, stats, simulation,
//                "comparison/Comparison.txt");
        new Comparison().printDataSetAndGraphs(simulation, "comparison/save1", parameters);
    }

    private static List<Algorithm> getSpecialSet() {
        List<Algorithm> algorithms = new ArrayList<>();

//        algorithms.add(new MixedFgs2Sem());
        algorithms.add(new MixedFgs2CG()); //*

//        algorithms.add(new MixedFgs2Bdeu());
//        algorithms.add(new MixedFgs2Bic());
//
//        algorithms.add(new MixedWfgs());

//        algorithms.add(new MixedPcCg());
//        algorithms.add(new MixedCpcCg());

//        algorithms.add(new MixedCpcLrt()); //*
//
//        algorithms.add(new MixedGfciCG());
//
//        algorithms.add(new MixedGpcCg());
//        algorithms.add(new MixedPcCgLrtTest());
        algorithms.add(new MixedCpcCgLrtTest()); //*
//        algorithms.add(new MixedPcsCgLrtTest());
//        algorithms.add(new MixedCpcsCgLrtTest());
//        algorithms.add(new MixedFciCgLrtTest());


//        algorithms.add(new ContinuousCpcFgs());
//        algorithms.add(new ContinuousFgs2());


        return algorithms;
    }

    private static List<Algorithm> getFullAlgorithmsList() {
        List<Algorithm> algorithms = new ArrayList<>();

//        // Pattern
        algorithms.add(new ContinuousPcFz());
        algorithms.add(new ContinuousCpcFz());
        algorithms.add(new ContinuousPcsFz());
        algorithms.add(new ContinuousCpcsFz());

        algorithms.add(new ContinuousFgs());
        algorithms.add(new ContinuousFgs2());

        algorithms.add(new ContinuousPcFgs());
        algorithms.add(new ContinuousPcsFgs());
        algorithms.add(new ContinuousCpcFgs());

        algorithms.add(new ContinuousPcSemBic());
        algorithms.add(new ContinuousCpcSemBic());
        algorithms.add(new ContinuousPcsSemBic());
        algorithms.add(new ContinuousCpcsSemBic());

        algorithms.add(new ContinuousGpc());

        algorithms.add(new DiscretePcChiSquare());
        algorithms.add(new DiscretePcsChiSquare());
        algorithms.add(new DiscreteCpcChiSquare());

        algorithms.add(new DiscretePcGSquare());
        algorithms.add(new DiscretePcsGSquare());
        algorithms.add(new DiscreteCpcGSquare());

        algorithms.add(new DiscreteFgs2Bdeu());
        algorithms.add(new DiscreteFgs2Bic());

        //21
        algorithms.add(new MixedFgs2Sem());
        algorithms.add(new MixedFgs2Bdeu());

        algorithms.add(new MixedWfgs());
        algorithms.add(new MixedWgfci());

        algorithms.add(new MixedPcLrtWfgs());
        algorithms.add(new MixedPcsLrtWfgs());
        algorithms.add(new MixedCpcWfgs());

        algorithms.add(new MixedFgs2CG());
        algorithms.add(new MixedPcCg());
        algorithms.add(new MixedPcsCg());
        algorithms.add(new MixedCpcCg());
        algorithms.add(new MixedPcCgLrtTest());
        algorithms.add(new MixedPcsCgLrtTest());
        algorithms.add(new MixedCpcCgLrtTest());
        algorithms.add(new MixedCpcsCgLrtTest());
        algorithms.add(new MixedFciCgLrtTest());

        algorithms.add(new MixedGpcCg());

        // These take a long time.
////
        algorithms.add(new MixedPcLrtWGfci());
        algorithms.add(new MixedPcsLrtWfgs());
        algorithms.add(new MixedCpcLrtWGfci());
//
        algorithms.add(new MixedPcMgm());
        algorithms.add(new MixedPcsMgm());
        algorithms.add(new MixedCpcMgm());
//
        algorithms.add(new MixedPcMlrw());
        algorithms.add(new MixedCpcMlrw());
        algorithms.add(new MixedPcsMlrw());
//
        algorithms.add(new MixedPcLrt());

        // Best of the slow ones
        algorithms.add(new MixedCpcLrt());
//
//
//        PAG
        algorithms.add(new ContinuousFciFz());
        algorithms.add(new ContinuousfciMaxFz());
        algorithms.add(new ContinuousRfciFz());

        algorithms.add(new ContinuousFciSemBic());
        algorithms.add(new ContinuousRfciSemBic());
//
        algorithms.add(new ContinuousGfci());
////qq
        algorithms.add(new DiscreteFciCs());
        algorithms.add(new DiscreteFciGs());

        algorithms.add(new DiscreteFciCs());
        algorithms.add(new DiscreteRfciGs());

        algorithms.add(new DiscreteGfci());

        algorithms.add(new MixedGfciCG());
        algorithms.add(new MixedFciCG());

        // These take a long time.
//        algorithms.add(new MixedGfciMixedScore());
//        algorithms.add(new MixedFciLrtWfgs());
//        algorithms.add(new MixedFciLrt());
//        algorithms.add(new MixedFciMlrw());

//        Cyclic PAG
//        algorithms.add(new ContinuousCcd());
        return algorithms;
    }

}




