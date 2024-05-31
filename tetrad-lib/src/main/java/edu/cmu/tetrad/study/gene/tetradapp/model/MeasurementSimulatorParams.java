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

package edu.cmu.tetrad.study.gene.tetradapp.model;

import edu.cmu.tetrad.study.gene.tetrad.gene.history.BasalInitializer;
import edu.cmu.tetrad.study.gene.tetrad.gene.history.BooleanGlassFunction;
import edu.cmu.tetrad.study.gene.tetrad.gene.history.GeneHistory;
import edu.cmu.tetrad.study.gene.tetrad.gene.simulation.MeasurementSimulator;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;

/**
 * Wraps MeasurementSimulator so that it may be used as a parameter object.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class MeasurementSimulatorParams implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The wrapped measurement simulator.
     */
    private final MeasurementSimulator simulator;

    /**
     * Whether to include dish and chip variables in the simulation.
     */
    private boolean includeDishAndChipVariables;

    //==============================CONSTRUCTORS=========================//

    /**
     * Constructs a measurement simulator using the given history. The history will be used to do the simulation of each
     * cell.
     *
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     * @throws java.lang.NullPointerException if the history argument is null.
     */
    public MeasurementSimulatorParams(Parameters parameters) {
        this.simulator = new MeasurementSimulator(parameters);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetradapp.model.MeasurementSimulatorParams} object
     */
    public static MeasurementSimulatorParams serializableInstance() {
        MeasurementSimulatorParams params = new MeasurementSimulatorParams(new Parameters());
        params.setHistory(new GeneHistory(
                BasalInitializer.serializableInstance(),
                BooleanGlassFunction.serializableInstance()));
        return params;
    }

    //==============================PUBLIC METHODS======================//

    /**
     * <p>setHistory.</p>
     *
     * @param history a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.GeneHistory} object
     */
    public void setHistory(GeneHistory history) {
        getSimulator().setHistory(history);
    }

    /**
     * <p>getDishDishVariability.</p>
     *
     * @return a double
     */
    public double getDishDishVariability() {
        return getSimulator().getDishDishVariability();
    }

    /**
     * <p>setDishDishVariability.</p>
     *
     * @param value a double
     */
    public void setDishDishVariability(double value) {
        getSimulator().setDishDishVariability(value);
    }

    /**
     * <p>getSampleSampleVariability.</p>
     *
     * @return a double
     */
    public double getSampleSampleVariability() {
        return getSimulator().getSampleSampleVariability();
    }

    /**
     * <p>setSampleSampleVariability.</p>
     *
     * @param value a double
     */
    public void setSampleSampleVariability(double value) {
        getSimulator().setSampleSampleVariability(value);
    }

    /**
     * <p>getChipChipVariability.</p>
     *
     * @return a double
     */
    public double getChipChipVariability() {
        return getSimulator().getChipChipVariability();
    }

    /**
     * <p>setChipChipVariability.</p>
     *
     * @param value a double
     */
    public void setChipChipVariability(double value) {
        getSimulator().setChipChipVariability(value);
    }

    /**
     * <p>getPixelDigitalization.</p>
     *
     * @return a double
     */
    public double getPixelDigitalization() {
        return getSimulator().getPixelDigitalization();
    }

    /**
     * <p>setPixelDigitalization.</p>
     *
     * @param value a double
     */
    public void setPixelDigitalization(double value) {
        getSimulator().setPixelDigitalization(value);
    }

    /**
     * <p>getNumDishes.</p>
     *
     * @return a int
     */
    public int getNumDishes() {
        return getSimulator().getNumDishes();
    }

    /**
     * <p>setNumDishes.</p>
     *
     * @param value a int
     */
    public void setNumDishes(int value) {
        getSimulator().setNumDishes(value);
    }

    /**
     * <p>getNumCellsPerDish.</p>
     *
     * @return a int
     */
    public int getNumCellsPerDish() {
        return getSimulator().getNumCellsPerDish();
    }

    /**
     * <p>setNumCellsPerDish.</p>
     *
     * @param value a int
     */
    public void setNumCellsPerDish(int value) {
        getSimulator().setNumCellsPerDish(value);
    }

    /**
     * <p>getNumSamplesPerDish.</p>
     *
     * @return a int
     */
    public int getNumSamplesPerDish() {
        return getSimulator().getNumSamplesPerDish();
    }

    /**
     * <p>setNumSamplesPerDish.</p>
     *
     * @param value a int
     */
    public void setNumSamplesPerDish(int value) {
        getSimulator().setNumSamplesPerDish(value);
    }

    /**
     * <p>getStepsGenerated.</p>
     *
     * @return a int
     */
    public int getStepsGenerated() {
        return getSimulator().getStepsGenerated();
    }

    /**
     * <p>setStepsGenerated.</p>
     *
     * @param value a int
     */
    public void setStepsGenerated(int value) {
        getSimulator().setStepsGenerated(value);
    }

    /**
     * <p>getFirstStepStored.</p>
     *
     * @return a int
     */
    public int getFirstStepStored() {
        return getSimulator().getFirstStepStored();
    }

    /**
     * <p>setFirstStepStored.</p>
     *
     * @param value a int
     */
    public void setFirstStepStored(int value) {
        getSimulator().setFirstStepStored(value);
    }

    /**
     * <p>getInterval.</p>
     *
     * @return a int
     */
    public int getInterval() {
        return getSimulator().getInterval();
    }

    /**
     * <p>setInterval.</p>
     *
     * @param value a int
     */
    public void setInterval(int value) {
        getSimulator().setInterval(value);
    }

    /**
     * <p>isInitSync.</p>
     *
     * @return a boolean
     */
    public boolean isInitSync() {
        return getSimulator().isInitSync();
    }

    /**
     * <p>setInitSync.</p>
     *
     * @param selected a boolean
     */
    public void setInitSync(boolean selected) {
        getSimulator().setInitSync(selected);
    }

    /**
     * <p>isRawDataSaved.</p>
     *
     * @return a boolean
     */
    public boolean isRawDataSaved() {
        return getSimulator().isRawDataSaved();
    }

    /**
     * <p>setRawDataSaved.</p>
     *
     * @param selected a boolean
     */
    public void setRawDataSaved(boolean selected) {
        getSimulator().setRawDataSaved(selected);
    }

    /**
     * <p>isAntilogCalculated.</p>
     *
     * @return a boolean
     */
    public boolean isAntilogCalculated() {
        return getSimulator().isAntilogCalculated();
    }

    /**
     * <p>setAntilogCalculated.</p>
     *
     * @param selected a boolean
     */
    public void setAntilogCalculated(boolean selected) {
        getSimulator().setAntilogCalculated(selected);
    }

    /**
     * <p>Getter for the field <code>simulator</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.study.gene.tetrad.gene.simulation.MeasurementSimulator} object
     */
    public MeasurementSimulator getSimulator() {
        return this.simulator;
    }

    /**
     * <p>simulate.</p>
     *
     * @param history a {@link edu.cmu.tetrad.study.gene.tetrad.gene.history.GeneHistory} object
     */
    public void simulate(GeneHistory history) {
        getSimulator().simulate(history);
    }

    /**
     * <p>getMeasuredData.</p>
     *
     * @return an array of {@link double} objects
     */
    public double[][][] getMeasuredData() {
        return getSimulator().getMeasuredData();
    }

    /**
     * <p>getTimeSteps.</p>
     *
     * @return an array of {@link int} objects
     */
    public int[] getTimeSteps() {
        return getSimulator().getTimeSteps();
    }

    /**
     * <p>getRawData.</p>
     *
     * @return an array of {@link double} objects
     */
    public double[][][] getRawData() {
        return getSimulator().getRawData();
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to serialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to deserialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * <p>isIncludeDishAndChipVariables.</p>
     *
     * @return a boolean
     */
    public boolean isIncludeDishAndChipVariables() {
        return getSimulator().isIncludeDishAndChipColumns();
    }

    /**
     * <p>Setter for the field <code>includeDishAndChipVariables</code>.</p>
     *
     * @param includeDishAndChipVariables a boolean
     */
    public void setIncludeDishAndChipVariables(boolean includeDishAndChipVariables) {
        getSimulator().setIncludeDishAndChipColumns(includeDishAndChipVariables);
    }
}





