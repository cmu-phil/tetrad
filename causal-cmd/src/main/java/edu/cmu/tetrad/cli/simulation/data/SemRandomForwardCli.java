/*
 * Copyright (C) 2016 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.cli.simulation.data;

import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.cli.SimulationType;
import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.util.Parameters;
import java.util.Collections;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

/**
 *
 * Sep 19, 2016 5:10:21 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class SemRandomForwardCli extends AbstractDataSimulationCli {

    protected int numOfLatentConfounders;
    protected double avgDegree;
    protected int maxDegree;
    protected int maxIndegree;
    protected int maxOutdegree;
    boolean connected;

    public SemRandomForwardCli(String[] args) {
        super(args);
    }

    @Override
    public void printSimulationParameters(Formatter fmt) {
        fmt.format("Number of Latent Confounders: %d%n", numOfLatentConfounders);
        fmt.format("Average Degree: %f%n", avgDegree);
        fmt.format("Maximum Degree: %d%n", maxDegree);
        fmt.format("Maximum Indegree: %d%n", maxIndegree);
        fmt.format("Maximum Outdegree: %d%n", maxOutdegree);
        fmt.format("Connected: %s%n", connected);
    }

    @Override
    public List<Option> getRequiredOptions() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<Option> getOptionalOptions() {
        List<Option> options = new LinkedList<>();
        options.add(new Option(null, "latent", true, "Number of latent confounders."));
        options.add(new Option(null, "avg-degree", true, "Average degree."));
        options.add(new Option(null, "max-degree", true, "Maximum degree."));
        options.add(new Option(null, "max-indegree", true, "Maximum indegree."));
        options.add(new Option(null, "max-outdegree", true, "Maximum outdegree."));
        options.add(new Option(null, "connected", false, "Connected."));

        return options;
    }

    @Override
    public void parseRequiredOptions(CommandLine cmd) throws Exception {
    }

    @Override
    public void parseOptionalOptions(CommandLine cmd) throws Exception {
        numOfLatentConfounders = Args.getIntegerMin(cmd.getOptionValue("latent", "0"), 0);
        avgDegree = Args.getDoubleMin(cmd.getOptionValue("avg-degree", "1.0"), 0);
        maxDegree = Args.getIntegerMin(cmd.getOptionValue("max-degree", "30"), 0);
        maxIndegree = Args.getIntegerMin(cmd.getOptionValue("max-indegree", "15"), 0);
        maxOutdegree = Args.getIntegerMin(cmd.getOptionValue("max-outdegree", "15"), 0);
        connected = cmd.hasOption("connected");
    }

    @Override
    public Simulation getSimulation() {
        return new SemSimulation(new RandomForward());
    }

    @Override
    public Parameters getParameters() {
        Parameters parameters = new Parameters();

        // RandomForward parameters
        parameters.set("numMeasures", numOfVariables);
        parameters.set("numLatents", numOfLatentConfounders);
        parameters.set("avgDegree", avgDegree);
        parameters.set("maxDegree", maxDegree);
        parameters.set("maxIndegree", maxIndegree);
        parameters.set("maxOutdegree", maxOutdegree);
        parameters.set("connected", connected);

        // SemSimulation parameters
        parameters.set("standardize", Boolean.TRUE);
        parameters.set("measurementVariance", 0);
        parameters.set("numRuns", 1);
        parameters.set("differentGraphs", Boolean.FALSE);
        parameters.set("sampleSize", numOfCases);

        return parameters;
    }

    @Override
    public SimulationType getSimulationType() {
        return SimulationType.SEM_RAND_FWD;
    }

}
