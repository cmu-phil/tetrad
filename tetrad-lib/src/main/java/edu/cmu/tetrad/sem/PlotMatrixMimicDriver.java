package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.File;
import java.io.FileWriter;

/**
 * Simple command-line driver for PlotMatrixMimicSimulator.
 * <p>
 * Usage:
 * java PlotMatrixMimicDriver input.txt output.txt nRows novelty seed
 */
public final class PlotMatrixMimicDriver {

    public static void main(String[] args) throws Exception {

        if (args.length != 5) {
            System.err.println("Usage:\n" + "  java PlotMatrixMimicDriver <inputData> <outputData> <nRows> <novelty> <seed>\n\n" + "Example:\n" + "  java PlotMatrixMimicDriver data.txt sim.txt 2000 0.15 12345");
            System.exit(1);
        }

        // ---------------- parse arguments ----------------

        File inputFile = new File(args[0]);
        File outputFile = new File(args[1]);

        int nRows = Integer.parseInt(args[2]);
        double novelty = Double.parseDouble(args[3]);
        long seed = Long.parseLong(args[4]);

        if (!inputFile.exists()) {
            throw new IllegalArgumentException("Input file does not exist: " + inputFile);
        }

        // ---------------- load dataset ----------------

        System.out.println("Loading data from: " + inputFile.getAbsolutePath());

        DataSet data = SimpleDataLoader.loadContinuousData(inputFile, "//", '\"', "*", true, Delimiter.TAB, false);

        System.out.println("Loaded dataset: " + data.getNumRows() + " rows, " + data.getNumColumns() + " variables");

        // ---------------- run simulator ----------------

        PlotMatrixMimicSimulator simulator = new PlotMatrixMimicSimulator(data);

        simulator.setNoveltyStrength(novelty);

        System.out.println("Simulating " + nRows + " rows (novelty=" + novelty + ")...");

        DataSet simulated = simulator.simulate(nRows, seed);

        // ---------------- write output ----------------

        System.out.println("Writing simulated data to: " + outputFile.getAbsolutePath());

        try (FileWriter writer = new FileWriter(outputFile)) {
            writer.write(simulated.toString());
        }

        System.out.println("Done.");
    }
}