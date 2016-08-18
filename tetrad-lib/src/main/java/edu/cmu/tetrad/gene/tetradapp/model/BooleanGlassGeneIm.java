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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.gene.tetrad.gene.history.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.dist.Distribution;
import edu.cmu.tetrad.gene.tetrad.gene.graph.DisplayNameHandler;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Wraps the MeasurementSimulator class as an instantiated model.
 *
 * @author Joseph Ramsey
 */
public class BooleanGlassGeneIm implements SessionModel {
    static final long serialVersionUID = 23L;

    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * @serial Cannot be null.
     */
    private BooleanGlassGenePm genePm;

    /**
     * @serial Cannot be null.
     */
    private BooleanGlassFunction glassFunction;

    /**
     * @serial Cannot be null.
     */
    private BasalInitializer initializer;

    /**
     * @serial Cannot be null.
     */
    private GeneHistory history;

    /**
     * @serial Cannot be null.
     */
    private MeasurementSimulatorParams simulator;

    //============================CONSTRUCTORS============================//

    /**
     * Obtains a boolean Glass function from the boolean Glass gene PM provided
     * and uses it to create a Glass history and a measurement simulator.
     * Editing this IM consists in editing the wrapped measurement simulator.
     *
     * @param genePm the BooleanGlassGenePm from which the BooleanGlassFunction
     *               is extracted.
     */
    public BooleanGlassGeneIm(BooleanGlassGenePm genePm, Parameters parameters) {
        try {
            this.genePm = genePm;

            // These are the two objects which this IM mainly edits.
            this.glassFunction = new BooleanGlassFunction(genePm.getLagGraph());
            this.initializer = new BasalInitializer(glassFunction, 0, 1);
            this.history = new GeneHistory(initializer, glassFunction);
            this.simulator = new MeasurementSimulatorParams(parameters);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static BooleanGlassGeneIm serializableInstance() {
        return new BooleanGlassGeneIm(
                BooleanGlassGenePm.serializableInstance(), new Parameters());
    }

    //==========================PUBLIC METHODS============================//

    /**
     * Returns the list of factors in the history.
     */
    public List<String> getFactors() {
        List<String> factors = new ArrayList<>();
        IndexedLagGraph connectivity = this.glassFunction.getIndexedLagGraph();

        for (int i = 0; i < connectivity.getNumFactors(); i++) {
            factors.add(connectivity.getFactor(i));
        }

        return factors;
    }

    /**
     * Returns the list of parents of the given factor as String's formatted in
     * the style used by the Tetrad IV display-- e.g. "V1:L2" for factor "V1" at
     * a lag of 2.
     *
     * @param factor the factor, e.g. "V3", for which parents are requested.
     * @return the list of lagged factors which are parents of 'factor'.
     */
    public List<String> getParents(int factor) {
        IndexedLagGraph connectivity = getBooleanGlassFunction()
                .getIndexedLagGraph();
        List<String> displayParents = new ArrayList<>();

        for (int i = 0; i < connectivity.getNumParents(factor); i++) {
            IndexedParent parent = connectivity.getParent(factor, i);
            String name = connectivity.getFactor(parent.getIndex());
            LaggedFactor laggedFactor = new LaggedFactor(name, parent.getLag());
            String displayString =
                    DisplayNameHandler.getDisplayString(laggedFactor);

            displayParents.add(displayString);
        }

        return displayParents;
    }

    /**
     * Returns the MeasurementSimulator. The simulation parameters can be edited
     * directly with this. (Needs to be cast to MeasurementSimulator.)
     */
    public Object getSimulationParams() {
        return this.simulator;
    }

    /**
     * Returns the number of parents that a given factor has.
     *
     * @param factor the given factor, e.g. "V3", formatted as a String name.
     */
    public int getNumParents(int factor) {
        IndexedLagGraph connectivity = getBooleanGlassFunction()
                .getIndexedLagGraph();

        return connectivity.getNumParents(factor);
    }

    /**
     * Returns the value in the given row of the boolean table for the given
     * factor.
     *
     * @return true or false.
     */
    public boolean getRowValueAt(int factor, int row) {
        return getBooleanGlassFunction().getSubFunction(factor).getValue(row);
    }

    /**
     * Sets the value in the given row of the boolean table for the given
     * factor to the given value (true/false).
     */
    public void setRowValueAt(int factor, int row, boolean value) {
        getBooleanGlassFunction().getSubFunction(factor).setValue(row, value);
    }

    public void setSimulator(MeasurementSimulatorParams simulator) {
        simulator.setHistory(getHistory());
        this.simulator = simulator;
    }

    /**
     * Uses the MeasurementSimulator class to simulate a set of measurement data
     * and optionally a set of raw cell expression data. For details of the
     * measurement simulator, see that class.
     *
     * @return a DataModelList containing either one or two models, depending on
     *         whether measurement data alone is saved or whether raw data is
     *         additionally saved.
     */
    public DataModelList simulateData() {

        // Simulate the data using the simulator.
        simulator.simulate(history);

        // This is the object that will be returned; it can store
        // multiple data sets.
        DataModelList dataModelList = new DataModelList();

        List<Node> variables = new LinkedList<>();

        if (simulator.isIncludeDishAndChipVariables()) {
            DiscreteVariable dishVar = new DiscreteVariable("Dish");
            DiscreteVariable chipVar = new DiscreteVariable("Chip");

            variables.add(dishVar);
            variables.add(chipVar);
        }

        // Fetch the measured data and convert it.
        double[][][] measuredData = simulator.getMeasuredData();
        int[] timeSteps = simulator.getTimeSteps();
        List<String> factors =
                new ArrayList<>(genePm.getLagGraph().getFactors());

        // Order: G1:t1, G2:t1, G3:t1, G1:t1, G2:t2, G3:t2,...
        for (int i = 0; i < measuredData[0].length; i++) {
            for (int j = 0; j < measuredData.length; j++) {
                String name = factors.get(j) + ":t" + timeSteps[i];
                ContinuousVariable var = new ContinuousVariable(name);
                variables.add(var);
            }
        }

        DataSet measuredDataSet =
                new ColtDataSet(measuredData[0][0].length, variables);

//        System.out.println(measuredDataSet);

        // Order: G1:t1, G2:t1, G3:t1, G1:t1, G2:t2, G3:t2,...
        for (int i = 0; i < measuredData[0].length; i++) {
            for (int j = 0; j < measuredData.length; j++) {
                double[] _data = measuredData[j][i];
                String name = factors.get(j) + ":t" + timeSteps[i];
                ContinuousVariable var =
                        (ContinuousVariable) measuredDataSet.getVariable(name);
                int col = measuredDataSet.getVariables().indexOf(var);

                for (int i1 = 0; i1 < _data.length; i1++) {
                    measuredDataSet.setDouble(i1, col, _data[i1]);
                }
            }
        }


        measuredDataSet.setName("Measurement Data");
        dataModelList.add(measuredDataSet);

        if (simulator.isIncludeDishAndChipVariables()) {
            for (int i = 0; i < measuredData[0][0].length; i++) {
                int samplesPerDish = simulator.getNumSamplesPerDish();
                measuredDataSet.setInt(i, 0, i / samplesPerDish + 1);
                measuredDataSet.setInt(i, 1, i + 1);
            }
        }

        // Fetch the measured data and convert it.
        if (simulator.isRawDataSaved()) {
            double[][][] rawData = simulator.getRawData();
            List<Node> _variables = new LinkedList<>();

            // Order: G0:t1, G1:t1, G2:t1, G0:t1, G1:t2, G2:t2,...
            for (int i = 0; i < rawData[0].length; i++) {
                for (int j = 0; j < rawData.length; j++) {
                    String name = "G" + (j + 1) + ":t" + timeSteps[i];
                    _variables.add(new ContinuousVariable(name));
                }
            }

            DataSet rawDataSet =
                    new ColtDataSet(rawData[0][0].length, _variables);

            for (int i = 0; i < rawData[0].length; i++) {
                for (int j = 0; j < rawData.length; j++) {
                    double[] _data = rawData[j][i];
                    String name = "G" + (j + 1) + ":t" + timeSteps[i];
                    Node var = rawDataSet.getVariable(name);
                    int col = rawDataSet.getVariables().indexOf(var);

                    for (int i1 = 0; i1 < _data.length; i1++) {
                        rawDataSet.setDouble(i1, col, _data[i1]);
                    }
                }
            }

            int n = rawData[0][0].length;
            int cellsPerDish = simulator.getNumCellsPerDish();

            if (simulator.isIncludeDishAndChipVariables()) {
                DiscreteVariable dishVar2 =
                        new DiscreteVariable("Dish", n / cellsPerDish + 1);

                rawDataSet.addVariable(0, dishVar2);
            }
            rawDataSet.setName("Raw Data");

            if (simulator.isIncludeDishAndChipVariables()) {
                for (int i = 0; i < n; i++) {
                    rawDataSet.setInt(i, 0, i / cellsPerDish + 1);
                }
            }

            dataModelList.add(rawDataSet);
        }

        if (measuredData[0][0].length == 1) {
            dataModelList.add(0, asTimeSeriesData(measuredData, 0, factors));
        }

        return dataModelList;
    }

    private TimeSeriesData asTimeSeriesData(double[][][] cube, int cell,
                                            List<String> factors) {
        int numTimeSteps = cube[0].length;
        int numFactors = cube.length;
        double[][] square = new double[numTimeSteps][numFactors];

        for (int timeStep = 0; timeStep < numTimeSteps; timeStep++) {
            for (int factor = 0; factor < numFactors; factor++) {
                square[timeStep][factor] = cube[factor][timeStep][cell];
            }
        }

        List<String> varNames = new ArrayList<>();
        for (int i = 0; i < numFactors; i++) {
            varNames.add(factors.get(i));
        }

        return new TimeSeriesData(new TetradMatrix(square), varNames);
    }

    /**
     * Returns the Glass function case to BooleanGlassFunction for convenience.
     */
    public BooleanGlassFunction getBooleanGlassFunction() {
        return this.glassFunction;
    }

    /**
     * Returns the Glass history case to GlassHistory for convenience.
     */
    public GeneHistory getHistory() {
        return this.history;
    }

    /**
     * Sets the error distribution for the given factor to the given
     * distribution. Values for the transcription error for this factor will be
     * drawn from this distribution.
     */
    public void setErrorDistribution(int factor, Distribution distribution) {
        getBooleanGlassFunction().setErrorDistribution(factor, distribution);
    }

    /**
     * Returns the error distribution for the given factor.
     *
     * @see #setErrorDistribution
     */
    public Distribution getErrorDistribution(int factor) {
        return getBooleanGlassFunction().getErrorDistribution(factor);
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

        if (genePm == null) {
            throw new NullPointerException();
        }

        if (glassFunction == null) {
            throw new NullPointerException();
        }

        if (initializer == null) {
            throw new NullPointerException();
        }

        if (history == null) {
            throw new NullPointerException();
        }

        if (simulator == null) {
            throw new NullPointerException();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}






