/*
 * Copyright (C) 2017 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.AdjacencyPrecision;
import edu.cmu.tetrad.algcomparison.statistic.AdjacencyRecall;
import edu.cmu.tetrad.algcomparison.statistic.ArrowheadPrecision;
import edu.cmu.tetrad.algcomparison.statistic.ArrowheadRecall;
import edu.cmu.tetrad.algcomparison.statistic.ElapsedTime;
import edu.cmu.tetrad.algcomparison.statistic.F1Adj;
import edu.cmu.tetrad.algcomparison.statistic.F1Arrow;
import edu.cmu.tetrad.algcomparison.statistic.MathewsCorrAdj;
import edu.cmu.tetrad.algcomparison.statistic.MathewsCorrArrow;
import edu.cmu.tetrad.algcomparison.statistic.SHD;
import edu.cmu.tetrad.algcomparison.statistic.Statistics;
import edu.cmu.tetrad.util.Parameters;
import java.io.IOException;
import static java.lang.System.out;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 *
 * Nov 14, 2017 5:24:51 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TimeoutComparisonTest {

    @ClassRule
    public static TemporaryFolder tmpDir = new TemporaryFolder();

    public TimeoutComparisonTest() {
    }

    /**
     * Test of setComparisonGraph method, of class TimeoutComparison.
     *
     * @throws IOException
     */
    @Ignore
    @Test
    public void testTimeoutComparison() throws IOException {
        Parameters parameters = getParameters();
        Statistics statistics = getStatistics();
        Algorithms algorithms = getAlgorithms();
        Simulations simulations = getSimulations();

        String resultsPath = tmpDir.newFolder("comparison").toString();

        TimeoutComparison comparisonEngine = getTetradComparisonEngine();
        comparisonEngine.compareFromSimulations(resultsPath, simulations, algorithms, statistics, parameters, 60, TimeUnit.SECONDS);

        System.out.println("================================================================================");
        System.out.println("Output File:");
        System.out.println("================================================================================");
        Path outputFile = Paths.get(resultsPath, "Comparison.txt");
        if (Files.exists(outputFile)) {
            try (Stream<String> stream = Files.lines(outputFile)) {
                stream.forEach(out::println);
            }
        }
        System.out.println("================================================================================");
    }

    private static TimeoutComparison getTetradComparisonEngine() {
        TimeoutComparison comparison = new TimeoutComparison();
        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(true);
        comparison.setShowUtilities(true);
        comparison.setParallelized(true);

        return comparison;
    }

    private static Simulations getSimulations() {
        Simulations simulations = new Simulations();
        simulations.add(new SemSimulation(new RandomForward()));

        return simulations;
    }

    private static Algorithms getAlgorithms() {
        Algorithms algorithms = new Algorithms();
        algorithms.add(new Gfci(new FisherZ(), new SemBicScore()));

        return algorithms;
    }

    private static Statistics getStatistics() {
        Statistics statistics = new Statistics();
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new MathewsCorrAdj());
        statistics.add(new MathewsCorrArrow());
        statistics.add(new F1Adj());
        statistics.add(new F1Arrow());
        statistics.add(new SHD());
        statistics.add(new ElapsedTime());

        return statistics;
    }

    private static Parameters getParameters() {
        Parameters parameters = new Parameters();
        parameters.set("numRuns", 1);
        parameters.set("numMeasures", 100);
        parameters.set("avgDegree", 4, 6);
        parameters.set("sampleSize", 250000);
        parameters.set("alpha", 1e-4, 1e-3, 1e-2);

        return parameters;
    }

}
