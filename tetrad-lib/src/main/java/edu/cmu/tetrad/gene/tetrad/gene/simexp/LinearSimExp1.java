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

package edu.cmu.tetrad.gene.tetrad.gene.simexp;

import edu.cmu.tetrad.gene.tetrad.gene.history.*;
import edu.cmu.tetrad.gene.tetrad.gene.simulation.MeasurementSimulator;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.NumberFormat;

/**
 * Implements a particular simulation for experimental purposes.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class LinearSimExp1 {

    /**
     * The measurement simulator.
     */
    private MeasurementSimulator simulator;

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
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new LinearSimExp1(args[0]);
    }

    private UpdateFunction createFunction() {
        String[] factors = new String[]{"G1", "G2", "G3"};

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

        /*
        function.setIntercept(0, 0.0);
        function.setCoefficient(0, 0, 0.5);
        function.setIntercept(1, 0.1);
        function.setCoefficient(1, 0, 1.5);
        function.setCoefficient(1, 1, -1.5);
        function.setCoefficient(1, 2, +1.3);
        function.setIntercept(2, -1.8);
        function.setCoefficient(2, 0, 1.1);
        */

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
        for (int i = 0; i < timeSteps.length; i++) {
            for (int j = 0; j < updateFunction.getNumFactors(); j++) {
                out.print("G" + j + ":t" + timeSteps[i] + "\t");
            }
        }
        out.println();

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        int cellsPerDish = simulator.getNumCellsPerDish();

        for (int i = 0; i < data[0][0].length; i++) {
            out.print((i / cellsPerDish + 1) + "\t");
            out.print((i + 1) + "\t");
            for (int j = 0; j < data[0].length; j++) {
                for (int k = 0; k < data.length; k++) {
                    out.print(nf.format(data[k][j][i]) + "\t");
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

        for (int i = 0; i < timeSteps.length; i++) {
            for (int j = 0; j < updateFunction.getNumFactors(); j++) {
                out.print("G" + j + ":t" + timeSteps[i] + "\t");
            }
        }
        out.println();

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
        int samplesPerDish = simulator.getNumSamplesPerDish();

        for (int i = 0; i < data[0][0].length; i++) {
            out.print((i / samplesPerDish + 1) + "\t");
            out.print((i + 1) + "\t");
            for (int j = 0; j < data[0].length; j++) {
                for (int k = 0; k < data.length; k++) {
                    out.print(nf.format(data[k][j][i]) + "\t");
                }
            }
            out.println();
        }
    }
}





