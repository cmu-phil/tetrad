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
import edu.cmu.tetrad.algcomparison.simulation.LinearFisherModel;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.cli.CmdOptions;
import edu.cmu.tetrad.cli.ParamAttrs;
import edu.cmu.tetrad.cli.SimulationType;
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
    protected boolean connected;

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
        options.add(new Option(null, CmdOptions.LATENT, true, CmdOptions.createDescription(ParamAttrs.NUM_LATENTS)));
        options.add(new Option(null, CmdOptions.AVG_DEGREE, true, CmdOptions.createDescription(ParamAttrs.AVG_DEGREE)));
        options.add(new Option(null, CmdOptions.MAX_DEGREE, true, CmdOptions.createDescription(ParamAttrs.MAX_DEGREE)));
        options.add(new Option(null, CmdOptions.MAX_INDEGREE, true, CmdOptions.createDescription(ParamAttrs.MAX_INDEGREE)));
        options.add(new Option(null, CmdOptions.MAX_OUTDEGREE, true, CmdOptions.createDescription(ParamAttrs.MAX_OUTDEGREE)));
        options.add(new Option(null, CmdOptions.CONNECTED, false, CmdOptions.createDescription(ParamAttrs.CONNECTED)));

        return options;
    }

    @Override
    public void parseRequiredOptions(CommandLine cmd) throws Exception {
    }

    @Override
    public void parseOptionalOptions(CommandLine cmd) throws Exception {
        numOfLatentConfounders = CmdOptions.getInt(CmdOptions.LATENT, ParamAttrs.NUM_LATENTS, cmd);
        avgDegree = CmdOptions.getDouble(CmdOptions.AVG_DEGREE, ParamAttrs.AVG_DEGREE, cmd);
        maxDegree = CmdOptions.getInt(CmdOptions.MAX_DEGREE, ParamAttrs.MAX_DEGREE, cmd);
        maxIndegree = CmdOptions.getInt(CmdOptions.MAX_INDEGREE, ParamAttrs.MAX_INDEGREE, cmd);
        maxOutdegree = CmdOptions.getInt(CmdOptions.MAX_OUTDEGREE, ParamAttrs.MAX_OUTDEGREE, cmd);
        connected = cmd.hasOption(CmdOptions.CONNECTED);
    }

    @Override
    public Simulation getSimulation() {
        return new LinearFisherModel(new RandomForward());
    }

    @Override
    public Parameters getParameters() {
        Parameters parameters = new Parameters();

        // RandomForward parameters
        parameters.set(ParamAttrs.NUM_LATENTS, numOfLatentConfounders);
        parameters.set(ParamAttrs.AVG_DEGREE, avgDegree);
        parameters.set(ParamAttrs.MAX_DEGREE, maxDegree);
        parameters.set(ParamAttrs.MAX_INDEGREE, maxIndegree);
        parameters.set(ParamAttrs.MAX_OUTDEGREE, maxOutdegree);
        parameters.set(ParamAttrs.CONNECTED, connected);

        // SemSimulation parameters
        parameters.set(ParamAttrs.NUM_MEASURES, numOfVariables);
        parameters.set(ParamAttrs.NUM_RUNS, 1);
        parameters.set(ParamAttrs.DIFFERENT_GRAPHS, Boolean.FALSE);
        parameters.set(ParamAttrs.SAMPLE_SIZE, numOfCases);

        return parameters;
    }

    @Override
    public SimulationType getSimulationType() {
        return SimulationType.SEM_RAND_FWD;
    }

}
