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

package edu.cmu.tetrad.gene.tetradapp.model;

import edu.cmu.tetrad.gene.tetrad.gene.history.BasalInitializer;
import edu.cmu.tetrad.gene.tetrad.gene.history.BooleanGlassFunction;
import edu.cmu.tetrad.gene.tetrad.gene.history.GeneHistory;
import edu.cmu.tetrad.gene.tetrad.gene.simulation.MeasurementSimulator;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Wraps MeasurementSimulator so that it may be used as a parameter object.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class MeasurementSimulatorParams implements TetradSerializable{
    static final long serialVersionUID = 23L;

    /**
     * @serial Cannot be null.
     */
    private MeasurementSimulator simulator;
    private boolean includeDishAndChipVariables;

    //==============================CONSTRUCTORS=========================//

    /**
     * Constructs a measurement simulator using the given history. The history
     * will be used to do the simulation of each cell.
     *
     * @throws NullPointerException if the history argument is null.
     */
    public MeasurementSimulatorParams(Parameters parameters) {
        simulator = new MeasurementSimulator(parameters);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static MeasurementSimulatorParams serializableInstance() {
        MeasurementSimulatorParams params = new MeasurementSimulatorParams(new Parameters());
        params.setHistory(new GeneHistory(
                BasalInitializer.serializableInstance(),
                BooleanGlassFunction.serializableInstance()));
        return params;
    }

    //==============================PUBLIC METHODS======================//

    public void setHistory(GeneHistory history) {
        getSimulator().setHistory(history);
    }

    public double getDishDishVariability() {
        return getSimulator().getDishDishVariability();
    }

    public void setDishDishVariability(double value) {
        getSimulator().setDishDishVariability(value);
    }

    public double getSampleSampleVariability() {
        return getSimulator().getSampleSampleVariability();
    }

    public void setSampleSampleVariability(double value) {
        getSimulator().setSampleSampleVariability(value);
    }

    public double getChipChipVariability() {
        return getSimulator().getChipChipVariability();
    }

    public void setChipChipVariability(double value) {
        getSimulator().setChipChipVariability(value);
    }

    public double getPixelDigitalization() {
        return getSimulator().getPixelDigitalization();
    }

    public void setPixelDigitalization(double value) {
        getSimulator().setPixelDigitalization(value);
    }

    public int getNumDishes() {
        return getSimulator().getNumDishes();
    }

    public void setNumDishes(int value) {
        getSimulator().setNumDishes(value);
    }

    public int getNumCellsPerDish() {
        return getSimulator().getNumCellsPerDish();
    }

    public void setNumCellsPerDish(int value) {
        getSimulator().setNumCellsPerDish(value);
    }

    public int getNumSamplesPerDish() {
        return getSimulator().getNumSamplesPerDish();
    }

    public void setNumSamplesPerDish(int value) {
        getSimulator().setNumSamplesPerDish(value);
    }

    public int getStepsGenerated() {
        return getSimulator().getStepsGenerated();
    }

    public void setStepsGenerated(int value) {
        getSimulator().setStepsGenerated(value);
    }

    public int getFirstStepStored() {
        return getSimulator().getFirstStepStored();
    }

    public void setFirstStepStored(int value) {
        getSimulator().setFirstStepStored(value);
    }

    public int getInterval() {
        return getSimulator().getInterval();
    }

    public void setInterval(int value) {
        getSimulator().setInterval(value);
    }

    public boolean isInitSync() {
        return getSimulator().isInitSync();
    }

    public void setInitSync(boolean selected) {
        getSimulator().setInitSync(selected);
    }

    public boolean isRawDataSaved() {
        return getSimulator().isRawDataSaved();
    }

    public void setRawDataSaved(boolean selected) {
        getSimulator().setRawDataSaved(selected);
    }

    public boolean isAntilogCalculated() {
        return getSimulator().isAntilogCalculated();
    }

    public void setAntilogCalculated(boolean selected) {
        getSimulator().setAntilogCalculated(selected);
    }

    public MeasurementSimulator getSimulator() {
        return simulator;
    }

    public void simulate(GeneHistory history) {
        getSimulator().simulate(history);
    }

    public double[][][] getMeasuredData() {
        return getSimulator().getMeasuredData();
    }

    public int[] getTimeSteps() {
        return getSimulator().getTimeSteps();
    }

    public double[][][] getRawData() {
        return getSimulator().getRawData();
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (simulator == null) {
            throw new NullPointerException();
        }
    }

    public boolean isIncludeDishAndChipVariables() {
        return getSimulator().isIncludeDishAndChipColumns();
    }

    public void setIncludeDishAndChipVariables(boolean includeDishAndChipVariables) {
        getSimulator().setIncludeDishAndChipColumns(includeDishAndChipVariables);
    }
}





