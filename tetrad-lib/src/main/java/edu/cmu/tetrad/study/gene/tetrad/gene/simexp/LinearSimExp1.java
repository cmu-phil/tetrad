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

package edu.cmu.tetrad.study.gene.tetrad.gene.simexp;

import edu.cmu.tetrad.study.gene.tetrad.gene.history.*;
import edu.cmu.tetrad.study.gene.tetrad.gene.simulation.MeasurementSimulator;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.NumberFormat;

/**
 * Implements a particular simulation for experimental purposes.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LinearSimExp1 {

    /**
     * The measurement simulator.
     */
    private final MeasurementSimulator simulator;

    /**
     * <p>Constructor for LinearSimExp1.</p>
     *
     * @param stub a {@link java.lang.String} object
     */
    public LinearSimExp1(String stub) {
        this.simulator = new MeasurementSimulator(new Parameters());
        UpdateFunction function = createFunction();
        GeneHistory history = createHistory(function);
        this.simulator.setRawDataSaved(true);
        this.simulator.setNumDishes(8);
        this.simulator.simulate(history);

        try {
            PrintStream out =
                    new PrintStream(new FileOutputStream(stub + "meas.dat"));
            printMeasuredData(out);
            out.close();

            PrintStream out2 =
                    new PrintStream(new FileOutputStream(stub + "raw.dat"));
            printRawData(out2);
            out2.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        new LinearSimExp1(args[0]);
    }

    private UpdateFunction createFunction() {
        String[] factors = {"G1", "G2", "G3"};

        LagGraph lagGraph = new BasicLagGraph();
        lagGraph.addFactor(factors[0]);
        lagGraph.addFactor(factors[1]);
        lagGraph.addFactor(factors[2]);

        lagGraph.addEdge(factors[0], new LaggedFactor(factors[0], 1));
        lagGraph.addEdge(factors[1], new LaggedFactor(factors[0], 1));
        lagGraph.addEdge(factors[1], new LaggedFactor(factors[1], 1));
        lagGraph.addEdge(factors[1], new LaggedFactor(factors[2], 1));
        lagGraph.addEdge(factors[2], new LaggedFactor(factors[2], 1));

        LinearFunction function = new LinearFunction(lagGraph);

        function.setIntercept("G1", 0.05);
        function.setCoefficient("G1", new LaggedFactor("G1", 1), 0.5);
        function.setIntercept("G2", 0.1);
        function.setCoefficient("G2", new LaggedFactor("G1", 1), 0.6);
        function.setIntercept("G3", 0.2);
        function.setCoefficient("G2", new LaggedFactor("G2", 1), 0.7);
        function.setIntercept("G3", 0.3);
        function.setCoefficient("G2", new LaggedFactor("G3", 1), 0.8);
        function.setIntercept("G3", 0.4);
        function.setCoefficient("G3", new LaggedFactor("G3", 1), 0.9);

        System.out.println(function);

        return function;
    }

    private GeneHistory createHistory(UpdateFunction function) {
        BasalInitializer initializer = new BasalInitializer(function, 0.0, 1.0);
        return new GeneHistory(initializer, function);
    }

    private void printRawData(PrintStream out) {
        double[][][] data = this.simulator.getRawData();
        GeneHistory history = this.simulator.getHistory();
        UpdateFunction updateFunction = history.getUpdateFunction();
        int[] timeSteps = this.simulator.getTimeSteps();

        out.print("Dish\tInd\t");
        for (int timeStep : timeSteps) {
            for (int j = 0; j < updateFunction.getNumFactors(); j++) {
                out.print("G" + j + ":t" + timeStep + "\t");
            }
        }
        out.println();

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        int cellsPerDish = this.simulator.getNumCellsPerDish();

        for (int i = 0; i < data[0][0].length; i++) {
            out.print((i / cellsPerDish + 1) + "\t");
            out.print((i + 1) + "\t");
            for (int j = 0; j < data[0].length; j++) {
                for (double[][] datum : data) {
                    out.print(nf.format(datum[j][i]) + "\t");
                }
            }
            out.println();
        }
    }

    private void printMeasuredData(PrintStream out) {
        double[][][] data = this.simulator.getMeasuredData();
        GeneHistory history = this.simulator.getHistory();
        UpdateFunction updateFunction = history.getUpdateFunction();
        int[] timeSteps = this.simulator.getTimeSteps();

        out.print("Dish\tChip\t");

        for (int timeStep : timeSteps) {
            for (int j = 0; j < updateFunction.getNumFactors(); j++) {
                out.print("G" + j + ":t" + timeStep + "\t");
            }
        }
        out.println();

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        int samplesPerDish = this.simulator.getNumSamplesPerDish();

        for (int i = 0; i < data[0][0].length; i++) {
            out.print((i / samplesPerDish + 1) + "\t");
            out.print((i + 1) + "\t");
            for (int j = 0; j < data[0].length; j++) {
                for (double[][] datum : data) {
                    out.print(nf.format(datum[j][i]) + "\t");
                }
            }
            out.println();
        }
    }
}






