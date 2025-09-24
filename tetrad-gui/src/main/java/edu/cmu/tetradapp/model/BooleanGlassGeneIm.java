///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.study.gene.tetrad.gene.graph.DisplayNameHandler;
import edu.cmu.tetrad.study.gene.tetrad.gene.history.*;
import edu.cmu.tetrad.study.gene.tetradapp.model.MeasurementSimulatorParams;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.dist.Distribution;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Wraps the MeasurementSimulator class as an instantiated model.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BooleanGlassGeneIm implements SessionModel {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The name of the session model.
     */
    private String name;

    /**
     * The BooleanGlassGenePm from which the BooleanGlassFunction is extracted.
     */
    private BooleanGlassGenePm genePm;

    /**
     * The BooleanGlassFunction extracted from the BooleanGlassGenePm.
     */
    private BooleanGlassFunction glassFunction;

    /**
     * The BasalInitializer for the BooleanGlassFunction.
     */
    private BasalInitializer initializer;

    /**
     * The GeneHistory for the BooleanGlassFunction.
     */
    private GeneHistory history;

    /**
     * The MeasurementSimulatorParams for the BooleanGlassFunction.
     */
    private MeasurementSimulatorParams simulator;

    //============================CONSTRUCTORS============================//

    /**
     * Obtains a boolean Glass function from the boolean Glass gene PM provided and uses it to create a Glass history
     * and a measurement simulator. Editing this IM consists in editing the wrapped measurement simulator.
     *
     * @param genePm     the BooleanGlassGenePm from which the BooleanGlassFunction is extracted.
     * @param parameters the parameters for the measurement simulator.
     */
    public BooleanGlassGeneIm(BooleanGlassGenePm genePm, Parameters parameters) {
        try {
            this.genePm = genePm;

            // These are the two objects which this IM mainly edits.
            this.glassFunction = new BooleanGlassFunction(genePm.getLagGraph());
            this.initializer = new BasalInitializer(this.glassFunction, 0, 1);
            this.history = new GeneHistory(this.initializer, this.glassFunction);
            this.simulator = new MeasurementSimulatorParams(parameters);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a simple exemplar of this class to test serialization.
     */
    public static BooleanGlassGeneIm serializableInstance() {
        return new BooleanGlassGeneIm(
                BooleanGlassGenePm.serializableInstance(), new Parameters());
    }

    //==========================PUBLIC METHODS============================//

    /**
     * Returns the list of factors in the history.
     *
     * @return the list of factors in the history.
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
     * Returns the list of parents of the given factor as String's formatted in the style used by the Tetrad IV
     * display-- e.g. "V1:L2" for factor "V1" at a lag of 2.
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
     * Returns the MeasurementSimulator. The simulation parameters can be edited directly with this. (Needs to be cast
     * to MeasurementSimulator.)
     *
     * @return the MeasurementSimulator.
     */
    public Object getSimulationParams() {
        return this.simulator;
    }

    /**
     * Returns the number of parents that a given factor has.
     *
     * @param factor the given factor, e.g. "V3", formatted as a String name.
     * @return the number of parents that the given factor has.
     */
    public int getNumParents(int factor) {
        IndexedLagGraph connectivity = getBooleanGlassFunction()
                .getIndexedLagGraph();

        return connectivity.getNumParents(factor);
    }

    /**
     * Returns the value in the given row of the boolean table for the given factor.
     *
     * @param factor the factor.
     * @param row    the row.
     * @return true or false.
     */
    public boolean getRowValueAt(int factor, int row) {
        return getBooleanGlassFunction().getSubFunction(factor).getValue(row);
    }

    /**
     * Sets the value in the given row of the boolean table for the given factor to the given value (true/false).
     *
     * @param factor the factor.
     * @param row    the row.
     * @param value  a boolean
     */
    public void setRowValueAt(int factor, int row, boolean value) {
        getBooleanGlassFunction().getSubFunction(factor).setValue(row, value);
    }

    /**
     * Sets the simulation parameters for the MeasurementSimulator.
     *
     * @param simulator the simulation parameters for the MeasurementSimulator.
     */
    public void setSimulator(MeasurementSimulatorParams simulator) {
        simulator.setHistory(getHistory());
        this.simulator = simulator;
    }

    /**
     * Uses the MeasurementSimulator class to simulate a set of measurement data and optionally a set of raw cell
     * expression data. For details of the measurement simulator, see that class.
     *
     * @return a DataModelList containing either one or two models, depending on whether measurement data alone is saved
     * or whether raw data is additionally saved.
     */
    public DataModelList simulateData() {

        // Simulate the data using the simulator.
        this.simulator.simulate(this.history);

        // This is the object that will be returned; it can store
        // multiple data sets.
        DataModelList dataModelList = new DataModelList();

        List<Node> variables = new LinkedList<>();

        if (this.simulator.isIncludeDishAndChipVariables()) {
            DiscreteVariable dishVar = new DiscreteVariable("Dish");
            DiscreteVariable chipVar = new DiscreteVariable("Chip");

            variables.add(dishVar);
            variables.add(chipVar);
        }

        // Fetch the measured data and convert it.
        double[][][] measuredData = this.simulator.getMeasuredData();
        int[] timeSteps = this.simulator.getTimeSteps();
        List<String> factors =
                new ArrayList<>(this.genePm.getLagGraph().getFactors());

        // Order: G1:t1, G2:t1, G3:t1, G1:t1, G2:t2, G3:t2,...
        for (int i = 0; i < measuredData[0].length; i++) {
            for (int j = 0; j < measuredData.length; j++) {
                String name = factors.get(j) + ":t" + timeSteps[i];
                ContinuousVariable var = new ContinuousVariable(name);
                variables.add(var);
            }
        }

        DataSet measuredDataSet =
                new BoxDataSet(new DoubleDataBox(measuredData[0][0].length, variables.size()), variables);

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

        if (this.simulator.isIncludeDishAndChipVariables()) {
            for (int i = 0; i < measuredData[0][0].length; i++) {
                int samplesPerDish = this.simulator.getNumSamplesPerDish();
                measuredDataSet.setInt(i, 0, i / samplesPerDish + 1);
                measuredDataSet.setInt(i, 1, i + 1);
            }
        }

        // Fetch the measured data and convert it.
        if (this.simulator.isRawDataSaved()) {
            double[][][] rawData = this.simulator.getRawData();
            List<Node> _variables = new LinkedList<>();

            // Order: G0:t1, G1:t1, G2:t1, G0:t1, G1:t2, G2:t2,...
            for (int i = 0; i < rawData[0].length; i++) {
                for (int j = 0; j < rawData.length; j++) {
                    String name = "G" + (j + 1) + ":t" + timeSteps[i];
                    _variables.add(new ContinuousVariable(name));
                }
            }

            DataSet rawDataSet =
                    new BoxDataSet(new DoubleDataBox(rawData[0][0].length, _variables.size()), variables);

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
            int cellsPerDish = this.simulator.getNumCellsPerDish();

            if (this.simulator.isIncludeDishAndChipVariables()) {
                DiscreteVariable dishVar2 =
                        new DiscreteVariable("Dish", n / cellsPerDish + 1);

                rawDataSet.addVariable(0, dishVar2);
            }
            rawDataSet.setName("Raw Data");

            if (this.simulator.isIncludeDishAndChipVariables()) {
                for (int i = 0; i < n; i++) {
                    rawDataSet.setInt(i, 0, i / cellsPerDish + 1);
                }
            }

            dataModelList.add(rawDataSet);
        }

        if (measuredData[0][0].length == 1) {
            dataModelList.add(0, asTimeSeriesData(measuredData, factors));
        }

        return dataModelList;
    }

    private TimeSeriesData asTimeSeriesData(double[][][] cube,
                                            List<String> factors) {
        int numTimeSteps = cube[0].length;
        int numFactors = cube.length;
        double[][] square = new double[numTimeSteps][numFactors];

        for (int timeStep = 0; timeStep < numTimeSteps; timeStep++) {
            for (int factor = 0; factor < numFactors; factor++) {
                square[timeStep][factor] = cube[factor][timeStep][0];
            }
        }

        List<String> varNames = new ArrayList<>();
        for (int i = 0; i < numFactors; i++) {
            varNames.add(factors.get(i));
        }

        return new TimeSeriesData(new Matrix(square), varNames);
    }

    /**
     * Returns the Glass function case to BooleanGlassFunction for convenience.
     *
     * @return the Glass function case to BooleanGlassFunction for convenience.
     */
    public BooleanGlassFunction getBooleanGlassFunction() {
        return this.glassFunction;
    }

    /**
     * Returns the Glass history case to GlassHistory for convenience.
     *
     * @return the Glass history case to GlassHistory for convenience.
     */
    public GeneHistory getHistory() {
        return this.history;
    }

    /**
     * Sets the error distribution for the given factor to the given distribution. Values for the transcription error
     * for this factor will be drawn from this distribution.
     *
     * @param factor       the factor.
     * @param distribution the distribution.
     */
    public void setErrorDistribution(int factor, Distribution distribution) {
        getBooleanGlassFunction().setErrorDistribution(factor, distribution);
    }

    /**
     * Returns the error distribution for the given factor.
     *
     * @param factor the factor.
     * @return the error distribution for the given factor.
     */
    public Distribution getErrorDistribution(int factor) {
        return getBooleanGlassFunction().getErrorDistribution(factor);
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Returns the name of the session model.
     *
     * @return the name of the session model.
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the name of the session model.
     */
    public void setName(String name) {
        this.name = name;
    }
}







