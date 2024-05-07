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

package edu.cmu.tetrad.study.gene.tetrad.gene.simulation;

import edu.cmu.tetrad.study.gene.tetrad.gene.history.DishModel;
import edu.cmu.tetrad.study.gene.tetrad.gene.history.GeneHistory;
import edu.cmu.tetrad.study.gene.tetrad.gene.history.UpdateFunction;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.dist.Distribution;
import edu.cmu.tetrad.util.dist.Normal;
import org.apache.commons.math3.util.FastMath;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Arrays;

/**
 * <p>Simulates measurement genetic data using an underlying GeneHistory object
 * to generate individual cell data. The GeneHistory object must be passed to the MeasurementSimulator in the
 * constructor; all other parameters are given default values at the time of construction but may be set using accessor
 * methods after construction. The <code>simulate</code> method generates two three-dimensional sets of data: (1) a raw
 * data set, consisting of expression levels at specified time steps for all of the factors that the history generates
 * data for and for each individual cell that is simulated, where these cells are organized into various petri dishes,
 * and (2) a measurement data set, in which various types of noise are added to aggregated expression levels for each
 * dish, to produce a specified number of samples for each dish of the raw data. Both raw data and measured data may
 * optionally be saved out; the purpose of allowing these options is to avoid memory overflows for large data sets,
 * especially for the raw data set. A simulation with 1,000,000 cells where each cell has 1000 genes, for instance, can
 * take quite a long time and can easily overflow RAM in Java if all of the raw expression levels are saved out.&gt; 0
 * <p>For examples of how to use the measurement simulator code, see the TestMeasurementSimulator class. This is a
 * JUnit test class that contains several examples of code use.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see TestMeasurementSimulator
 */
public class MeasurementSimulator implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The parameters for this simulation.
     */
    private final Parameters parameters;

    /**
     * The history that will be used to simulate the data.
     */
    private GeneHistory history;

    /**
     * Represents an array of time steps.
     * <p>
     * This variable is used to store an array of integers representing different time steps.
     */
    private int[] timeSteps;

    /**
     * This variable stores raw data in a three-dimensional array.
     * <p>
     * The first dimension represents the rows in the data.
     * <p>
     * The second dimension represents the columns in the data.
     * <p>
     * The third dimension represents additional dimensions for the data, which can be used for organizing
     * multidimensional data.
     * <p>
     * The values stored in this array are of type double.
     * <p>
     * Access to this variable is private, meaning it can only be accessed from within the class it is declared in.
     * <p>
     * Modifying this variable should be done with caution, as it contains sensitive raw data.
     */
    private double[][][] rawData;

    /**
     * Array variable to store measured data.
     *
     * <p>
     * This variable is a 3-dimensional array of type double to store measured data. It is used to store data collected
     * from various measures or experiments. The first dimension represents the measure index, the second dimension
     * represents the sample index within the measure, and the third dimension represents the data value.
     * </p>
     *
     * <p>
     * The structure of the array is as follows:
     * </p>
     * <ul>
     * <li>The first dimension represents the measure index, ranging from 0 to n-1.</li>
     * <li>The second dimension represents the sample index within the measure, ranging from 0 to m-1.</li>
     * <li>The third dimension represents the data value, ranging from 0 to k-1.</li>
     * </ul>
     *
     * <p>
     * The data values in the array are of type double, allowing for the storage of decimal values. The
     * array dimensions and size may vary based on the specific application and requirements.
     * </p>
     *
     * <p>
     * Access to this variable is restricted to the private scope to ensure encapsulation and proper
     * data management.
     * </p>
     *
     * <p>
     * Usage example: measuredData[1][2][3]
     * </p>
     */
    private double[][][] measuredData;

    /**
     * The dishNumber variable stores the number of a dish. It is an integer value and is initially set to -1.
     */
    private int dishNumber = -1;

    /**
     * The cellNumber variable represents the number of a cell. It is a private integer variable with an initial value
     * of -1.
     */
    private int cellNumber = -1;

    //=============================CONSTRUCTORS============================//

    /**
     * Constructs a measurement simulator using the given history. The history will be used to do the simulation of each
     * cell.
     *
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     * @throws java.lang.NullPointerException if the history argument is null.
     */
    public MeasurementSimulator(Parameters parameters) {
        this.parameters = parameters;
        constructTimeSteps();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.simulation.MeasurementSimulator} object
     */
    public static MeasurementSimulator serializableInstance() {
        return new MeasurementSimulator(new Parameters());
    }

    //=============================PUBLIC METHODS=========================//

    /**
     * Returns the history that was set in the constructor.
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.GeneHistory} object
     */
    public GeneHistory getHistory() {
        return this.history;
    }

    /**
     * Sets the history.
     *
     * @param history a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.GeneHistory} object
     */
    public void setHistory(GeneHistory history) {
        if (history == null) {
            throw new NullPointerException();
        }

        this.history = history;
    }

    /**
     * Returns the number of dishes to simulate.
     *
     * @return the number of dishes
     */
    public int getNumDishes() {
        /*
          The number of dishes to simulate.
         */
        int numDishes = 1;
        return this.parameters.getInt("numDishes", numDishes);
    }

    /**
     * Sets the number of dishes that are to be simulated. This value is passed to a dish model that determines how
     * expression levels for genes in cells are bumped up or down depending on which dish the cells are in.
     *
     * @param numDishes an int
     */
    public void setNumDishes(int numDishes) {

        if (numDishes > 0) {
            this.parameters.set("numDishes", numDishes);
//            this.numDishes = numDishes;
        } else {
            throw new IllegalArgumentException(
                    "Number of dishes must be > 0: " + numDishes);
        }
    }

    /**
     * <p>isIncludeDishAndChipColumns.</p>
     *
     * @return a boolean
     */
    public boolean isIncludeDishAndChipColumns() {
        boolean includeDishAndChipColumns = true;
        return this.parameters.getBoolean("includeDishAndChipColumns", includeDishAndChipColumns);
//        return includeDishAndChipColumns;
    }

    /**
     * <p>setIncludeDishAndChipColumns.</p>
     *
     * @param includeDishAndChipColumns a boolean
     */
    public void setIncludeDishAndChipColumns(boolean includeDishAndChipColumns) {
        this.parameters.set("includeDishAndChipColumns", includeDishAndChipColumns);
//        this.includeDishAndChipColumns = includeDishAndChipColumns;
    }

    /**
     * Returns the number of cells per dish.
     *
     * @return an int
     */
    public int getNumCellsPerDish() {
        /*
         * The number of cells per dish.
         */
        int numCellsPerDish = 10000;
        return this.parameters.getInt("numCellsPerDish", numCellsPerDish);
//        return this.numCellsPerDish;
    }

    /**
     * Sets the number of cells per dish. It is assumed that each dish has the same number of cells.
     *
     * @param numCellsPerDish an int
     */
    public void setNumCellsPerDish(int numCellsPerDish) {

        if (numCellsPerDish > 0) {
            this.parameters.set("numCellsPerDish", numCellsPerDish);
//            this.numCellsPerDish = numCellsPerDish;
        } else {
            throw new IllegalArgumentException("Number of cells per dish " +
                                               "must be > 0:" + numCellsPerDish);
        }
    }

    /**
     * Returns the number of steps generated.
     *
     * @return an int
     * @see #setStepsGenerated
     */
    public int getStepsGenerated() {
        /*
         * The number of time steps to generate.
         */
        int stepsGenerated = 4;
        return this.parameters.getInt("stepsGenerated", stepsGenerated);
//        return this.stepsGenerated;
    }

    /**
     * Sets the number of time steps to generate. Note that this is the actual number of time steps generated,
     * regardless of how many time steps are actually stored out. Which time steps are actually stored depends on this
     * parameter together with the parameters <code>firstStepStored</code> and
     * <code>interval</code>.
     *
     * @param stepsGenerated an int
     */
    public void setStepsGenerated(int stepsGenerated) {

        if (stepsGenerated > 0) {
            this.parameters.set("stepsGenerated", stepsGenerated);
//            this.stepsGenerated = stepsGenerated;

            constructTimeSteps();
        } else {
            throw new IllegalArgumentException(
                    "Steps generated must be > 0:" + stepsGenerated);
        }
    }

    /**
     * Returns the index of the first step to actually be stored out.
     *
     * @return an int
     */
    public int getFirstStepStored() {
        /*
         * The index of the first step to actually be stored out.
         */
        int firstStepStored = 1;
        return this.parameters.getInt("firstStepStored", firstStepStored);
    }

    /**
     * Sets the index of the first step to actually be stored out. Any steps prior to this in the model for a particular
     * cell will be computed, but their values will not be saved. Note that if the value of this parameter is greater
     * than the value of 'stepsGenerated', no steps will be saved.
     *
     * @param firstStepStored an int
     */
    public void setFirstStepStored(int firstStepStored) {

        if (firstStepStored > 0) {
            this.parameters.set("firstStepStored", firstStepStored);
//            this.firstStepStored = firstStepStored;

            constructTimeSteps();
        } else {
            throw new IllegalArgumentException(
                    "First step stored must be > 0: " + firstStepStored);
        }
    }

    /**
     * Sets the interval (in time steps) between time steps stored out. For instance, if the first step stored is 5 and
     * the interval is 3, then the series 5, 8, 11, 14, ..., will be stored out; this series will be stopped at the
     * first index in the series that exceeds 'stepsGenerated'.
     *
     * @return an int
     */
    public int getInterval() {
        /*
         * The interval (in time steps) between time steps stored out.
         */
        int interval = 1;
        return this.parameters.getInt("interval", interval);
    }

    /**
     * Returns the interval (int time steps) between time steps stored out.
     *
     * @param interval an int
     * @see #getInterval
     */
    public void setInterval(int interval) {

        if (interval > 0) {
            this.parameters.set("interval", interval);
//            this.interval = interval;

            constructTimeSteps();
        } else {
            throw new IllegalArgumentException(
                    "Interval must be > 0: " + interval);
        }
    }

    /**
     * Returns true if raw data is being saved in the getModel simulation, false if not.
     *
     * @return a boolean
     * @see #setRawDataSaved
     */
    public boolean isRawDataSaved() {
        boolean rawDataSaved = false;
        return this.parameters.getBoolean("rawDataSaved", rawDataSaved);
//        return this.rawDataSaved;
    }

    /**
     * Sets whether the raw data that is generated should be saved beyond what's needed for the getModel cell being
     * simulated. It's usually a good idea to set this to 'false' since for any reasonably sized simulation only the
     * measurement data is needed and the raw data can get to be way big.
     *
     * @param rawDataSaved a boolean
     */
    public void setRawDataSaved(boolean rawDataSaved) {
        this.parameters.set("rawDataSaved", rawDataSaved);
//        this.rawDataSaved = rawDataSaved;
    }

    /**
     * Returns 'true' if measured data is being saved out for the getModel simulation, 'false' if not.
     *
     * @return a boolean
     * @see #setMeasuredDataSaved
     */
    public boolean isMeasuredDataSaved() {
        boolean measuredDataSaved = true;
        return this.parameters.getBoolean("measuredDataSaved", measuredDataSaved);
//        return this.measuredDataSaved;
    }

    /**
     * Sets whether measured data should be saved out for this simulation. The default value is 'true'; should only be
     * set to 'false' if only the raw data is needed for a particular task.
     *
     * @param measuredDataSaved a boolean
     */
    public void setMeasuredDataSaved(boolean measuredDataSaved) {
        this.parameters.set("measuredDataSaved", measuredDataSaved);
//        this.measuredDataSaved = measuredDataSaved;
    }

    /**
     * Returns 'true' if cells in the simulation will be synchronized, 'false' if not.
     *
     * @return a boolean
     * @see #setInitSync
     */
    public boolean isInitSync() {
        boolean initSync = true;
        return this.parameters.getBoolean("initSync", initSync);
//        return this.initSync;
    }

    /**
     * Sets whether the expression levels of cells should be synchronized on initialization. How this synchronization
     * happens specifically is governed by the GeneHistory object. The basic idea though is that in many microarray
     * experiments, it is helpful to coordinate the initial expression levels of cells so that the development of
     * expression levels proceeds approximately in lockstep, at least for the genes of interest. See the specific
     * GeneHistory object used for more information about how this is accomplished in simulation. The GeneHistory object
     * also governs how cells are initialized randomly; see the GeneHistory object for more specific information about
     * this as well.
     *
     * @param initSync true if cells should be initialized synchronously, false if expression levels for cells of each
     *                 gene should be initialized randomly.
     * @see GeneHistory
     */
    public void setInitSync(boolean initSync) {
        this.parameters.set("initSync", initSync);
//        this.initSync = initSync;
    }

    /**
     * Returns true iff the antilog of each expression level should be calculated.
     *
     * @return a boolean
     */
    public boolean isAntilogCalculated() {
        boolean antilogCalculated = false;
        return this.parameters.getBoolean("antilogCalculated", antilogCalculated);
//        return this.antilogCalculated;
    }

    /**
     * Sets whether the antilog of each expression level should be calculated.
     *
     * @param antilogCalculated a boolean
     */
    public void setAntilogCalculated(boolean antilogCalculated) {
        this.parameters.set("antilogCalculated", antilogCalculated);
//        this.antilogCalculated = antilogCalculated;
    }

    /**
     * Returns the standard deviation in percentage of random dish bump values away from 100%.
     *
     * @return a double
     * @see #setDishDishVariability
     */
    public double getDishDishVariability() {
        /*
         * Whether raw data is being saved in the getModel simulation.
         */
        double dishDishVariability = 10.0;
        return this.parameters.getDouble("dishDishVariability", dishDishVariability);
//        return this.dishDishVariability;
    }

    /**
     * Sets the standard deviation sd% (in <i>percent</i>) of the distribution N(100.0, sd%), from which errors will be
     * drawn for the dish model. This will determine how many expression levels will get bumped up or down depending on
     * the dish particular cells are in. See the dish model for details of how this is done.
     *
     * @param dishDishVariability a double
     * @see DishModel
     */
    public void setDishDishVariability(double dishDishVariability) {

        if ((dishDishVariability > 0.0) && (dishDishVariability < 100.0)) {
            this.parameters.set("dishDishVariability", dishDishVariability);
//            this.dishDishVariability = dishDishVariability;
        } else {
            throw new IllegalArgumentException("Dish variability must " +
                                               "be > 0.0 and < 100.0: " + dishDishVariability);
        }
    }

    /**
     * Returns the number of samples generated per dish in the measurement model.
     *
     * @return an int
     * @see #setNumSamplesPerDish
     */
    public int getNumSamplesPerDish() {
        /*
         * The number of samples generated per dish in the measurement model.
         */
        int numSamplesPerDish = 4;
        return this.parameters.getInt("numChipsPerDish", numSamplesPerDish);
//        return this.numSamplesPerDish;
    }

    /**
     * Sets the number of samples that will be generated in the measured data for each dish. This number does not depend
     * in any way on the number of cells simulated in the dish. The idea is that a number of cells from the dish are
     * separated off into another container, ground up so that their DNA mixes together. This ground up mixture is then
     * pipetted onto a number of microarrays. The number of samples per dish is the number of microarrays that this
     * ground up mixture is pipetted onto.
     *
     * @param numSamplesPerDish an int
     */
    public void setNumSamplesPerDish(int numSamplesPerDish) {

        if (numSamplesPerDish > 0) {
            this.parameters.set("numChipsPerDish", numSamplesPerDish);
//            this.numSamplesPerDish = numSamplesPerDish;
        } else {
            throw new IllegalArgumentException("Number of chips per dish " +
                                               "must be > 0: " + numSamplesPerDish);
        }
    }

    /**
     * Returns the sample to sample variability, which is the standard deviation of a normal distribution with mean 0
     * from which errors in measured expression levels due to the microarray being used for measurement are drawn.
     *
     * @return a double
     * @see #setSampleSampleVariability
     */
    public double getSampleSampleVariability() {
        /*
         * The sample to sample variability.
         */
        double sampleSampleVariability = 0.025;
        return this.parameters.getDouble("sampleSampleVariability", sampleSampleVariability);
//        return this.sampleSampleVariability;
    }

    /**
     * Sets the sample to sample variability. It is assumed that when the DNA mixture from a dish is pipetted onto
     * different microarrays, there will be a variability in the measured expression of particular genes due to the
     * particular microarray--in other words, the measured expressions across an entire microarray will be bumped up or
     * down by a set amount. This amount is chosen from a normal distribution with mean 0 and standard deviation of the
     * given value, <code>sampleSampleVariability</code>. This error is added to other measurement errors.
     *
     * @param sampleSampleVariability a double
     */
    public void setSampleSampleVariability(double sampleSampleVariability) {

        if ((sampleSampleVariability > 0.0) && (sampleSampleVariability < 1.0)) {
            this.parameters.set("sampleSampleVariability", sampleSampleVariability);
//            this.sampleSampleVariability = sampleSampleVariability;
        } else {
            throw new IllegalArgumentException("Sample variability must be " +
                                               "> 0.0: " + sampleSampleVariability);
        }
    }

    /**
     * Returns the chip to chip variability.
     *
     * @return a double
     * @see #setChipChipVariability
     */
    public double getChipChipVariability() {
        /*
         * The chip to chip variability.
         */
        double chipChipVariability = 0.1;
        return this.parameters.getDouble("chipChipVariability", chipChipVariability);
//        return this.chipChipVariability;
    }

    /**
     * Sets the chip to chip variability. This is included for future expansion. The idea is that chips are reused (as
     * they typically are), one expects a degradation in quality of measurement with each reuse. In the getModel model,
     * no chip reuse is assumed. We simply pick a value from a normal with mean 0 and standard deviation
     * <code>chipChipVariability</code> and bump all measured expressions on the dish by that amount. This error is
     * added to other measurement errors.
     *
     * @param chipChipVariability a double
     */
    public void setChipChipVariability(double chipChipVariability) {

        if ((chipChipVariability > 0.0) && (chipChipVariability < 1.0)) {
            this.parameters.set("chipChipVariability", chipChipVariability);
//            this.chipChipVariability = chipChipVariability;
        } else {
            throw new IllegalArgumentException("Chip to chip variability " +
                                               "must be > 0.0 and < 1.0: " + chipChipVariability);
        }
    }

    /**
     * Returns the pixel digitalization error.
     *
     * @return a double
     * @see #setPixelDigitalization
     */
    public double getPixelDigitalization() {
        /*
         * The pixel digitalization error.
         */
        double pixelDigitalization = 0.025;
        return this.parameters.getDouble("pixelDigitalization", pixelDigitalization);
    }

    /**
     * Sets the pixel digitalization error. An error value is drawn for each individual expression measurement fron a
     * normal distribution with normal 0 and standard deviation <code>pixelDigitalization</code>. This error is added to
     * the other errors for measurement.
     *
     * @param pixelDigitalization a double
     */
    public void setPixelDigitalization(double pixelDigitalization) {

        if ((pixelDigitalization > 0.0) && (pixelDigitalization < 1.0)) {
            this.parameters.set("pixelDigitalization", pixelDigitalization);

//            this.pixelDigitalization = pixelDigitalization;
        } else {
            throw new IllegalArgumentException("Pixel digitalization " +
                                               "must be > 0.0 and <" + " 1.0: " + pixelDigitalization);
        }
    }

    /**
     * <p>Returns the raw data that is the result of a simulation, in the form
     * of a three-dimensional double array. If rawData = getRawData(), then rawData[i][j][k] is the expression level for
     * the i'th factor in the GeneHistory at the j'th time step stored out for the k'th individual cell. To determine
     * which factor is the i'th factor, look at getHistory().getFactor(). To determine which time step is the j'th time
     * step, look at getTimeSteps(). The k'th individual is in dish (k / numCellsPerDish).&gt; 0 <p>If raw data is not
     * saved out, this method returns null.&gt; 0
     *
     * @return the three-dimensional double array of raw data, if raw data is saved, or null, if raw data is not saved.
     */
    public double[][][] getRawData() {
        return this.rawData;
    }

    /**
     * <p>Returns the measured data that is the result of a simulation, in the
     * form of a three-dimensional double array. If measuredData = getMeasuredData(), then measuredData[i][j][k] is the
     * expression level for the i'th factor in the GeneHistory at the j'th time step stored out for the k'th sample. To
     * determine which factor is the i'th factor, look at getHistory().getFactor(). To determine which time step is the
     * j'th time step, look at getTimeSteps(). The k'th sample is drawn from a dish (k / numSamplesPerDish).&gt; 0 <p>If
     * measured data is not saved out, this method returns null.
     *
     * @return the three-dimensional double array of measured data, if measured data is saved, or null, if measured data
     * is not saved.
     */
    public double[][][] getMeasuredData() {
        return this.measuredData;
    }

    /**
     * Constructs an array of integers that indicate the time steps of the data that will be simulated. Integers in this
     * array must be >= 1 and must be in strictly increasing order--in other words, <code>timeSteps[i] <
     * timeSteps[i+1]</code> for i = 0, ..., timeSteps.length - 1.
     */
    private void constructTimeSteps() {

        // Construct an int array with the list of time steps in
        // increasing order. It's critical that they be in increasing
        // order, since otherwise, a loop later on in this method that
        // uses them will break badly. Note that basicConstraints on the
        // values of the parameters prevent numTimeSteps from being 0.
        int numTimeSteps = (1 + getStepsGenerated() - getFirstStepStored()) / getInterval();

        this.timeSteps = new int[numTimeSteps];

        for (int i = 0; i < this.timeSteps.length; i++) {
            this.timeSteps[i] = getFirstStepStored() + i * getInterval();
        }
    }

    /**
     * Returns the time steps that will be stored in the simulation. These time steps are iterations of the
     * <code>update</code> method of the GeneHistory; it is presumed that each such iteration represents a uniform
     * interval of time, so that the time steps 1, 2, 3, ..., n are equally spaced. The array returned by this method
     * represents a subset of the steps 1, 2, 3, ..., n, for instance, 2, 4, 6, ..., n. All of the time steps are
     * simulated; these are the time steps that are stored out to the raw data array and the measured data array. The
     * parameters of this class allow only subsets of the time steps that are themselves equally spaced. (This is not
     * necessary; it's just how the getModel parameters work--<code>firstStepStored</code>,
     * <code>stepsGenerated</code>,
     * <code>interval</code>. Note that the time steps in this array are &gt;=
     * 1, are in increasing order, and (as explained above) are equally spaced.
     *
     * @return an array of {@link int} objects
     */
    public int[] getTimeSteps() {
        return this.timeSteps;
    }

    /**
     * While the simulate() method is being executed, returns the dish number of the cell currently being simulated,
     * zero indexed. This is exported from the simulate() method to allow progress of the simulation to be reported. For
     * the total number of dishes, call <code>getNumDishes()</code>.
     *
     * @return an int
     * @see #getNumDishes
     */
    public int getDishNumber() {
        return this.dishNumber;
    }

    /**
     * While the simulate() method is being executed, returns the cell number of the cell currently being simulated in a
     * particular dish, zero indexed. This is exported from the simulate() method to allos progress of the simulation to
     * be reported. For the total number of dishes, call
     * <code>getNumCellsPerDish()</code>.
     *
     * @return an int
     * @see #getNumDishes
     */
    public int getCellNumber() {
        return this.cellNumber;
    }

    /**
     * Simulates (optionally) neither, either, or both of two three-dimensionaly data sets, rawData and measuredData.
     * For the form of the first set, see
     * <code>getRawData</code>; for the form of the second set, see
     * <code>getMeasuredData</code>.  The idea of the simulation is that cells
     * are grown in different dishes and periodically sampled. When they are sampled, their DNA is ground up and then
     * pipetted in aggregate onto a number of microarray chips. Each individual cell is simulated separately; the
     * simulated cells are grouped into dishes. Expression levels of cells in each dish are aggregated, and then random
     * noise is added to this aggregate expression level representing various sources of noise: sample to sample
     * variation, chip to chip variation, and pixellation measurement variation. For more information, see the Genetic
     * Simulator spec, which this method implements.
     *
     * @param history a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.GeneHistory} object
     * @see #getRawData
     * @see #getMeasuredData
     */
    public void simulate(GeneHistory history) {

        setHistory(history);

        history.reset();

        // Get the update function.
        UpdateFunction updateFunction = history.getUpdateFunction();
        int numFactors = updateFunction.getNumFactors();

        // Set up the dish model.
        DishModel dishModel =
                new DishModel(getNumDishes(), getDishDishVariability());

        // Set up the history.
        history.setInitSync(isInitSync());
        history.setDishModel(dishModel);

        // We will use two data cubes to store data for display
        // purposes: a measurement cube and a raw data cube. We first
        // construct the measurement cube, with two extra columns for
        // (a) dish number and (b) chip number.
        int numChips = getNumSamplesPerDish();

        if (isMeasuredDataSaved()) {
            int numSteps = this.timeSteps.length;
            int numSamples = getNumDishes() * numChips;

            this.measuredData = new double[numFactors][numSteps][numSamples];
        }

        // We next construct the raw data cube, with two extra columns
        // for (a) individual number and (b) dish number. Raw data is
        // saved out only if the 'rawDataSave' parameter is true. If
        // it's not true, these objects are not constructed. (They can
        // be extremely large.) References to the cube, and the initial
        // columns need to be saved in this scope for future use (but
        // may be null).
        this.rawData = null;

        if (isRawDataSaved()) {
            int numSteps = this.timeSteps.length;
            int numCells = getNumDishes() * getNumCellsPerDish();

            this.rawData = new double[numFactors][numSteps][numCells];
        }

        // There are three sources of error--sample to sample, chip to
        // chip, and pixel digitalization.  A certain number of
        // samples are drawn from each dish, and these are deposited
        // on the same number of chips. There is an error associated
        // with each chip and with each sample. (Pixel digitalization
        // error is drawn later.)
        double sampleSd = getSampleSampleVariability();
        double chipSd = getChipChipVariability();
        double pixelSd = getPixelDigitalization();
        Distribution sampleErrorDist = new Normal(0.0, sampleSd);
        Distribution chipErrorDist = new Normal(0.0, chipSd);
        Distribution pixelErrorDist = new Normal(0.0, pixelSd);
        double[][] chipErrors = new double[getNumDishes()][numChips];
        double[][] sampleErrors = new double[getNumDishes()][numChips];

        for (int d = 0; d < getNumDishes(); d++) {
            for (int ch = 0; ch < numChips; ch++) {
                chipErrors[d][ch] = chipErrorDist.nextRandom();
                sampleErrors[d][ch] = sampleErrorDist.nextRandom();
            }
        }

        // Make two double[] arrays, one to store data generated for a
        // particular cell (in a particular dish) and one to store
        // aggregations of these data.
        double[][] cellData = new double[this.timeSteps.length][numFactors];
        double[][] aggregation =
                new double[cellData.length][cellData[0].length];

        // For each dish and cell, simulate data and store it in in
        // 'cellData'. Across cells in a dish, aggregate this data in
        // 'aggregation'. First, iterate over dishes.
        for (int d = 0; d < getNumDishes(); d++) {
            dishModel.setDishNumber(d);

            // Reset the aggregation.
            for (int sIndex = 0; sIndex < this.timeSteps.length; sIndex++) {
                Arrays.fill(aggregation[sIndex], 0);
            }

            // Next, iterate over the cells in a dish.
            for (int c = 0; c < getNumCellsPerDish(); c++) {

                // (Leave this System.out here for now so that the
                // user can get a sense of how the simulation is
                // proceeding. (Useful for large simulations.))
                // Obviously another method needs to be concocted to
                // do this for the Tetrad interface, since we can't
                // assume the user will see System.out. TODO jdramsey
                // 12/01/01
                if ((c + 1) % 50 == 0) {
                    this.dishNumber = d;
                    this.cellNumber = c;
                }

                // Reset the cell data.
                for (int sIndex = 0; sIndex < this.timeSteps.length; sIndex++) {
                    Arrays.fill(cellData[sIndex], 0);
                }

                // Initialize the history array.
                history.initialize();

                // Generate data for one cell, storing only those time
                // steps that are indicated in the timeSteps
                // array. Note that the timeSteps array is 1-indexed,
                // while the 's' variable is 0-indexed. (Might want to
                // fix this in a future version.)
                int s = -1;
                int sIndex = 0;

                // Iterate over the steps generated, saving data only
                // for the steps in timeSteps[].
                while (++s < getStepsGenerated()) {

                    // Update, but not if s == 0. We need to save the
                    // first step to demonstrate that the cells are
                    // being synchronized.
                    if (s > 0) {
                        history.update();
                    }

                    if (s == this.timeSteps[sIndex] - 1) {
                        double[][] historyArray = history.getHistoryArray();

                        // For the steps in timeSteps[], iterate over
                        // the factors (genes).
                        for (int f = 0; f < numFactors; f++) {

                            // Copy data from the getModel time step in
                            // the history to the cellData[][] array.
                            cellData[sIndex][f] = historyArray[0][f];

                            // Antilog it if necessary.
                            if (isAntilogCalculated()) {
                                cellData[sIndex][f] =
                                        FastMath.exp(cellData[sIndex][f]);
                            }

                            // Optional--save this data to the raw
                            // data cube only if the raw data should
                            // be saved. (Otherwise, rawData ought to
                            // be null.)
                            if (isRawDataSaved()) {
                                int row = d * getNumCellsPerDish() + c;

                                this.rawData[f][sIndex][row] =
                                        cellData[sIndex][f];
                            }
                        }    // END for (int f = 0; ...

                        if (++sIndex >= this.timeSteps.length) {
                            break;
                        }
                    }        // END if (s == timeSteps[sIndex])
                }            // END while(++s <= stepsGenerated - 1)

                // Aggregate data.
                for (int i = 0; i < cellData.length; i++) {
                    for (int j = 0; j < cellData[0].length; j++) {
                        aggregation[i][j] += cellData[i][j];
                    }
                }
            }                // END for (int c = 0; c < numCellsPerDish; c++)

            if (isMeasuredDataSaved()) {

                // Calculate the average for each of these aggregations
                // and from it generate the prescribed number of
                // measurements by adding in the three sources of error
                // for each measurement: sample to sample error, chip to
                // chip error, and pixel digitalization error. Recall that
                // pixel digitalization error is chosen for each
                // sample/variable/time lag, while sample to sample and
                // chip to chip error are chosen only for each
                // sample. (These latter two errors have already been
                // generated.)
                for (int sIndex = 0; sIndex < this.timeSteps.length; sIndex++) {
                    for (int f = 0; f < numFactors; f++) {
                        for (int ch = 0; ch < numChips; ch++) {
                            double average =
                                    aggregation[sIndex][f] / getNumCellsPerDish();
                            double pixelError = pixelErrorDist.nextRandom();
                            double measurement = average + sampleErrors[d][ch] +
                                                 chipErrors[d][ch] + pixelError;

                            this.measuredData[f][sIndex][d * numChips + ch] =
                                    measurement;
                        }    // END for (int ch = 0; ch < numChips; ch++)
                    }        // END for (int f = 0; f < history.getNumFacto...
                }            // END for (int sIndex = 0; sIndex < timeSteps...
            }                // END if (measuredDataSaved)
        }                    // END for (int d = 0; d < numDishes; d++)

    }

    /*
        Notes:

        1. If unequally spaced time steps are ever required, probably the
        best thing to do would be to abstract out a superclass of this
        class with a setTimeSteps(int[] timeSteps) method and retain the
        setStepsGenerated(), setRecordInterval, and setFirstStepStored() methods
        only in the subclass, disallowing the setTimeSteps method in the
        subclass. The problem is that adding a setTimeSteps method to this
        class results in an inconsistent parameter set. jdramsey 12/22/01

        2. Even though initSync is a property of GeneHistory, conceptually
        it's a property of the simulator, and so it's been added to the
        API of this class. jdramsey 12/22/01

        3. If RAM becomes a problem, rawData and measuredData can always
        be converted to float[][][] arrays. This will require changing
        other classes, so perhaps it would be best to wait until it
        becomes an issue. jdramsey 12/22/01
        */

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s a {@link java.io.ObjectInputStream} object
     * @throws IOException            if an error occurs
     * @throws ClassNotFoundException if an error occurs
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.timeSteps == null) {
            throw new NullPointerException();
        }
    }
}





