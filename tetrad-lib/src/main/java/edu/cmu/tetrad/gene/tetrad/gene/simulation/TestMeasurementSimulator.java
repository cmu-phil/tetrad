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

package edu.cmu.tetrad.gene.tetrad.gene.simulation;

import cern.colt.list.DoubleArrayList;
import cern.jet.stat.Descriptive;
import edu.cmu.tetrad.gene.tetrad.gene.history.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Tests the MeasurementSimulator class using diagnostics devised by Richard
 * Scheines. The diagnostics are described in the Javadocs, below.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class TestMeasurementSimulator extends TestCase {

    // TODO: It's awkward in the model to hide two dimensions in the
    // last dimension of the data. It doesn't make sense to do it just
    // because in Tetrad the data needs to be of that form. What if we
    // want to use it in the future in some other application that
    // makes even different assumptions than this? It makes much more
    // sense to change the data model in Tetrad to be more
    // flexible. Perhaps data there should be acceessed always through
    // final accessor methods. jdramsey 11/26/01

    /**
     * The simulator whose function is being tested.
     */
    private MeasurementSimulator simulator = null;

    /**
     * The Boolean Glass function being used.
     */
    private BooleanGlassFunction updateFunction;

    /**
     * The history object used to simulate data by the simulator. A reference to
     * it is stored at the class level in case its parameters need to be
     * changed. (Since different history objects have different parameters, it
     * doesn't make sense to edit these parameters through the timulator
     * itself.)
     */
    private GeneHistory history = null;

    /**
     * Standard constructor for JUnit test cases.
     */
    public TestMeasurementSimulator(String name) {
        super(name);
    }

    /**
     * Sets up a GeneHistory object in the format for Richard's diagnostic. The
     * graph consists of three genes--1, 2, 3. In addition to the standard edges
     * from each gene one time step back to itself in the getModel time step,
     * there is an edge from G1:1 to G2:0, from G1:1 to G3:0, and from G2:1 to
     * G3:0. A Glass function is created using this graph and randomized. A
     * Glass history is created using this Glass function. This Glass history
     * will be the object of all tests in this suite. Also note that parameters
     * have default values for the tests in these suites; these default values
     * are set here. Note that gene and time step numbers are 1-indexed in the
     * Javadocs and variable names for this class but 0-indexed in array
     * indices.
     */
    public void setUp() {

        // Make an lag graph.
        LagGraph lagGraph = new BasicLagGraph();

        lagGraph.addFactor("G1");
        lagGraph.addFactor("G2");
        lagGraph.addFactor("G3");

        // Initialize graph.
        GraphInitializer graphInitializer = new PreviousStepOnly();

        graphInitializer.initialize(lagGraph);
        lagGraph.addEdge("G2", new LaggedFactor("G1", 1));
        lagGraph.addEdge("G3", new LaggedFactor("G1", 1));
        lagGraph.addEdge("G3", new LaggedFactor("G2", 1));

        // Create a random Boolean Glass function for this lag graph.
        this.updateFunction = new BooleanGlassFunction(lagGraph);

        // Create a new BasalInitializer.
        BasalInitializer historyInitializer =
                new BasalInitializer(updateFunction, 0.0, 1.0);

        //glassFunction.initialize();

        // Create a GlassHistory for this random Boolean Glass
        // function.
        //this.history = new GlassHistory(glassFunction);

        this.history = new GeneHistory(historyInitializer, updateFunction);

        // Create simulator.
        this.simulator = new MeasurementSimulator(new Parameters());

        RandomUtil.getInstance().setSeed(-1349902993443L);
    }

    /**
     * This set the default parameters that are specified by Scheines.
     */
    private void setDefaultParameters() {

        // Note that the transcription error in the history should
        // uniformly have SD of 0.05, even though that's a bit awkward
        // to set right now. It doesn't matter for the moment, because
        // it's set to that by default anyway. TODO JRamsey 12/01/01
        this.updateFunction.setDecayRate(0.1);
        this.updateFunction.setBooleanInfluenceRate(0.5);
        this.simulator.setDishDishVariability(10.0);
        this.simulator.setNumSamplesPerDish(4);
        this.simulator.setSampleSampleVariability(0.025);
        this.simulator.setChipChipVariability(0.1);
        this.simulator.setPixelDigitalization(0.025);
        this.simulator.setNumDishes(1);
        this.simulator.setNumCellsPerDish(10000);
        this.simulator.setStepsGenerated(4);
        this.simulator.setFirstStepStored(1);
        this.simulator.setInterval(1);
        this.simulator.setRawDataSaved(false);
        this.simulator.setInitSync(true);
    }

    /**
     * Test to make sure the accessor methods are working correctly.
     */
    public void testDefaultParameterSettings() {

        setDefaultParameters();
        assertEquals(10.0, this.simulator.getDishDishVariability(), 0.0001);
        assertEquals(4, this.simulator.getNumSamplesPerDish());
        assertEquals(0.025, this.simulator.getSampleSampleVariability(),
                0.0001);
        assertEquals(0.1, this.simulator.getChipChipVariability(), 0.0001);
        assertEquals(0.025, this.simulator.getPixelDigitalization(), 0.0001);
        assertEquals(1, this.simulator.getNumDishes());
        assertEquals(10000, this.simulator.getNumCellsPerDish());
        assertEquals(4, this.simulator.getStepsGenerated());
        assertEquals(1, this.simulator.getFirstStepStored());
        assertEquals(1, this.simulator.getInterval());
        assertEquals(false, this.simulator.isRawDataSaved());
        assertEquals(true, this.simulator.isInitSync());

        // Make sure the time steps are 1, 2, 3, 4.
        int[] timeSteps = this.simulator.getTimeSteps();

        assertEquals(4, timeSteps.length);

        for (int i = 0; i < timeSteps.length; i++) {
            assertEquals(i + 1, timeSteps[i]);
        }
    }

    /**
     * Save out the raw data using default parameters and make sure that
     * Gene1:t2 has the specified standard deviation. Should be 0.05. (Gene1 has
     * only itself as parent.)
     */
    public void testTranscriptionError() {

        // Raw data is saved for this simulation.
        setDefaultParameters();
        this.simulator.setRawDataSaved(true);
        this.simulator.setMeasuredDataSaved(false);

        // Simulate the data.
        this.simulator.simulate(this.history);

        double[][][] rawData = this.simulator.getRawData();

        // (Test the dimensions.)
        assertEquals(3, rawData.length);              // # variables.
        assertEquals(4, rawData[0].length);           // # time steps.
        assertEquals(10000, rawData[0][0].length);    // # cells / dish

        // The test is to see whether Gene 1 at time step 2 has a
        // standard deviation of 0.05. Of course the gene and time
        // step numbers need to be 0-indexed.
        DoubleArrayList doubleArrayList = new DoubleArrayList(rawData[0][1]);
        double sum = Descriptive.sum(doubleArrayList);
        double sumOfSquares = Descriptive.sumOfSquares(doubleArrayList);
        double stdev = Descriptive.standardDeviation(
                Descriptive.variance(rawData[0][1].length, sum, sumOfSquares));

        assertEquals(0.05, stdev, 0.01);
    }

    /**
     * Turn on dish-to-dish variability error, turn off all other sources of
     * error, simulate 100 dishes of data with 1 sample per dish, and look to
     * see whether in the aggregated data Gene2:t1 and Gene3:t1 have standard
     * deviations that are 10% of their respective means.
     */
    public void testDishToDishVariability() {

        setDefaultParameters();

        // The following parameters are set to non-default values for
        // this test.
        this.simulator.setNumDishes(100);
        this.simulator.setStepsGenerated(2);
        this.simulator.setNumSamplesPerDish(1);
        this.simulator.setSampleSampleVariability(0.0001);
        this.simulator.setChipChipVariability(0.0001);
        this.simulator.setPixelDigitalization(0.0001);
        this.simulator.setNumCellsPerDish(100);

        // Simulate the data.
        this.simulator.simulate(this.history);

        double[][][] measuredData = this.simulator.getMeasuredData();

        // Do the test.
        DoubleArrayList doubleArrayList =
                new DoubleArrayList(measuredData[1][0]);
        double sum = Descriptive.sum(doubleArrayList);
        double sumOfSquares = Descriptive.sumOfSquares(doubleArrayList);
        double gene2time1sd = Descriptive.standardDeviation(
                Descriptive.variance(measuredData[1][0].length, sum,
                        sumOfSquares));
        DoubleArrayList doubleArrayList1 =
                new DoubleArrayList(measuredData[2][0]);
        double sum1 = Descriptive.sum(doubleArrayList1);
        double sumOfSquares1 = Descriptive.sumOfSquares(doubleArrayList1);
        double gene3time1sd = Descriptive.standardDeviation(
                Descriptive.variance(measuredData[2][0].length, sum1,
                        sumOfSquares1));
        double gene2time1mean =
                Descriptive.mean(new DoubleArrayList(measuredData[1][0]));
        double gene3time1mean =
                Descriptive.mean(new DoubleArrayList(measuredData[2][0]));

        assertEquals(Math.abs(0.1 * gene2time1mean), gene2time1sd, 0.03);
        assertEquals(Math.abs(0.1 * gene3time1mean), gene3time1sd, 0.03);
    }

    /**
     * Turn on sample-to-sample error, turn off all other sources of error,
     * simulate 1 dish of data with 1000 samples per dish, and look to see
     * whether in the aggregated data the standard deviations of Gene2:t1 and
     * Gene3:t1 are 0.2.
     */
    public void testSampleToSampleError() {

        setDefaultParameters();

        // The following parameters are set to non-default values for
        // this test.
        this.simulator.setNumSamplesPerDish(1000);
        this.simulator.setSampleSampleVariability(0.2);
        this.simulator.setChipChipVariability(0.0001);
        this.simulator.setPixelDigitalization(0.0001);
        this.simulator.setStepsGenerated(2);
        this.simulator.setNumCellsPerDish(100);

        // Simulate the data.
        this.simulator.simulate(this.history);

        double[][][] measuredData = this.simulator.getMeasuredData();

        // Do the test.
        DoubleArrayList doubleArrayList =
                new DoubleArrayList(measuredData[1][0]);
        double sum = Descriptive.sum(doubleArrayList);
        double sumOfSquares = Descriptive.sumOfSquares(doubleArrayList);
        double gene2time1sd = Descriptive.standardDeviation(
                Descriptive.variance(measuredData[1][0].length, sum,
                        sumOfSquares));
        DoubleArrayList doubleArrayList1 =
                new DoubleArrayList(measuredData[2][0]);
        double sum1 = Descriptive.sum(doubleArrayList1);
        double sumOfSquares1 = Descriptive.sumOfSquares(doubleArrayList1);
        double gene3time1sd = Descriptive.standardDeviation(
                Descriptive.variance(measuredData[2][0].length, sum1,
                        sumOfSquares1));

        assertEquals(0.2, gene2time1sd, 0.02);
        assertEquals(0.2, gene3time1sd, 0.02);
    }

    /**
     * Turn on chip to chip error, turn off all other sources of error, simulate
     * 1 dish of data with 1000 samples per dish and look to see whether in the
     * aggregated data the standard deviations for Gene2:t1, Gene3:t1, and
     * Gene1:t2 are 0.3.
     */
    public void testChipToChipError() {

        setDefaultParameters();

        // The following parameters are set to non-default values for
        // this test.
        this.simulator.setDishDishVariability(0.0001);
        this.simulator.setNumSamplesPerDish(1000);
        this.simulator.setSampleSampleVariability(0.0001);
        this.simulator.setChipChipVariability(0.3);
        this.simulator.setPixelDigitalization(0.0001);
        this.simulator.setStepsGenerated(2);
        this.simulator.setNumCellsPerDish(100);

        // Simulate the data.
        this.simulator.simulate(this.history);

        double[][][] measuredData = this.simulator.getMeasuredData();

        // Do the test.
        DoubleArrayList doubleArrayList =
                new DoubleArrayList(measuredData[1][0]);
        double sum = Descriptive.sum(doubleArrayList);
        double sumOfSquares = Descriptive.sumOfSquares(doubleArrayList);
        double gene2time1sd = Descriptive.standardDeviation(
                Descriptive.variance(measuredData[1][0].length, sum,
                        sumOfSquares));
        DoubleArrayList doubleArrayList1 =
                new DoubleArrayList(measuredData[2][0]);
        double sum1 = Descriptive.sum(doubleArrayList1);
        double sumOfSquares1 = Descriptive.sumOfSquares(doubleArrayList1);
        double gene3time1sd = Descriptive.standardDeviation(
                Descriptive.variance(measuredData[2][0].length, sum1,
                        sumOfSquares1));
        DoubleArrayList doubleArrayList2 =
                new DoubleArrayList(measuredData[1][1]);
        double sum2 = Descriptive.sum(doubleArrayList2);
        double sumOfSquares2 = Descriptive.sumOfSquares(doubleArrayList2);
        double gene1time2sd = Descriptive.standardDeviation(
                Descriptive.variance(measuredData[1][1].length, sum2,
                        sumOfSquares2));

        assertEquals(0.3, gene2time1sd, 0.02);
        assertEquals(0.3, gene3time1sd, 0.02);
        assertEquals(0.3, gene1time2sd, 0.02);
    }

    /**
     * Turn on pixel digitalization error, turn off all other sources of error,
     * simulate 1 dish of data with 1000 samples per dish and look to see
     * whether in the aggregated data the standard deviations for Gene2:t1,
     * Gene3:t1 and Gene1:t2 are 0.3.
     */
    public void testPixelError() {

        setDefaultParameters();

        // The following parameters are set to non-default values for
        // this test.
        this.simulator.setDishDishVariability(0.0001);
        this.simulator.setNumSamplesPerDish(1000);
        this.simulator.setSampleSampleVariability(0.0001);
        this.simulator.setChipChipVariability(0.0001);
        this.simulator.setPixelDigitalization(0.3);
        this.simulator.setStepsGenerated(2);
        this.simulator.setNumCellsPerDish(100);

        // Simulate the data.
        this.simulator.simulate(this.history);

        double[][][] measuredData = this.simulator.getMeasuredData();

        // Do the test.
        DoubleArrayList doubleArrayList =
                new DoubleArrayList(measuredData[1][0]);
        double sum = Descriptive.sum(doubleArrayList);
        double sumOfSquares = Descriptive.sumOfSquares(doubleArrayList);
        double gene2time1sd = Descriptive.standardDeviation(
                Descriptive.variance(measuredData[1][0].length, sum,
                        sumOfSquares));
        DoubleArrayList doubleArrayList1 =
                new DoubleArrayList(measuredData[2][0]);
        double sum1 = Descriptive.sum(doubleArrayList1);
        double sumOfSquares1 = Descriptive.sumOfSquares(doubleArrayList1);
        double gene3time1sd = Descriptive.standardDeviation(
                Descriptive.variance(measuredData[2][0].length, sum1,
                        sumOfSquares1));
        DoubleArrayList doubleArrayList2 =
                new DoubleArrayList(measuredData[1][1]);
        double sum2 = Descriptive.sum(doubleArrayList2);
        double sumOfSquares2 = Descriptive.sumOfSquares(doubleArrayList2);
        double gene1time2sd = Descriptive.standardDeviation(
                Descriptive.variance(measuredData[1][1].length, sum2,
                        sumOfSquares2));

        assertEquals(0.3, gene2time1sd, 0.1);
        assertEquals(0.3, gene3time1sd, 0.1);
        assertEquals(0.3, gene1time2sd, 0.1);
    }

    /**
     * This method uses reflection to collect up all of the test methods from
     * this class and return them to the test runner.
     */
    public static Test suite() {

        // Edit the name of the class in the parens to match the name
        // of this class.
        return new TestSuite(TestMeasurementSimulator.class);
    }
}





