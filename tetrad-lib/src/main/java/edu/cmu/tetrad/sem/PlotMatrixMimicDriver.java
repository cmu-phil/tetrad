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

    /**
     * Private constructor for the PlotMatrixMimicDriver class.
     * This constructor is intentionally defined to prevent instantiation of the class,
     * as it serves as a utility class with a static main method to perform the application's operations.
     */
    private PlotMatrixMimicDriver() {}

    /**
     * Entry point for the PlotMatrixMimicDriver application. This method loads input data,
     * configures a simulator for generating simulated data based on the provided novelty value,
     * and writes the simulated data to the specified output file.
     *
     * @param args Command-line arguments passed to the program:
     *             args[0] - Path to the input data file (required).
     *             args[1] - Path to the output data file where the simulated data will be written (required).
     *             args[2] - Number of rows to simulate (integer, required).
     *             args[3] - Novelty strength for the simulation (double, required).
     *             args[4] - Random seed for simulation reproducibility (long, required).
     * @throws IllegalArgumentException if the input file does not exist or if arguments are malformed.
     * @throws Exception if an error occurs during data loading, simulation, or file writing.
     */
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