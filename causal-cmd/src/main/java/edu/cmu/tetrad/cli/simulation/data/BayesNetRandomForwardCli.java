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
import edu.cmu.tetrad.algcomparison.simulation.BayesNetSimulation;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.cli.SimulationType;
import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.util.ParamDescription;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import java.util.Collections;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import edu.cmu.tetrad.cli.ParamAttrs;

/**
 *
 * Sep 21, 2016 12:46:59 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class BayesNetRandomForwardCli extends AbstractDataSimulationCli {

    protected int numOfLatentConfounders;
    protected double avgDegree;
    protected int maxDegree;
    protected int maxIndegree;
    protected int maxOutdegree;
    protected boolean connected;

    protected int minCategories;
    protected int maxCategories;

    public BayesNetRandomForwardCli(String[] args) {
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
        fmt.format("Minimum Categories: %d%n", minCategories);
        fmt.format("Maximum Categories: %d%n", maxCategories);
    }

    @Override
    public List<Option> getRequiredOptions() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<Option> getOptionalOptions() {
        ParamDescriptions param = ParamDescriptions.instance();

        List<Option> options = new LinkedList<>();
        options.add(new Option(null, "latent", true, createDescription(param.get(ParamAttrs.NUM_LATENTS))));
        options.add(new Option(null, "avg-degree", true, createDescription(param.get(ParamAttrs.AVG_DEGREE))));
        options.add(new Option(null, "max-degree", true, createDescription(param.get(ParamAttrs.MAX_DEGREE))));
        options.add(new Option(null, "max-indegree", true, createDescription(param.get(ParamAttrs.MAX_INDEGREE))));
        options.add(new Option(null, "max-outdegree", true, createDescription(param.get(ParamAttrs.MAX_OUTDEGREE))));
        options.add(new Option(null, "connected", false, createDescription(param.get(ParamAttrs.CONNECTED))));
        options.add(new Option(null, "min-categories", true, createDescription(param.get(ParamAttrs.MIN_CATEGORIES))));
        options.add(new Option(null, "max-categories", true, createDescription(param.get(ParamAttrs.MAX_CATEGORIES))));

        return options;
    }

    @Override
    public void parseRequiredOptions(CommandLine cmd) throws Exception {
    }

    @Override
    public void parseOptionalOptions(CommandLine cmd) throws Exception {
        ParamDescriptions param = ParamDescriptions.instance();
        ParamDescription pd;

        pd = param.get(ParamAttrs.NUM_LATENTS);
        numOfLatentConfounders = Args.getIntegerMin(cmd.getOptionValue("latent", String.valueOf(pd.getDefaultValue())), pd.getLowerBoundInt());

        pd = param.get(ParamAttrs.AVG_DEGREE);
        avgDegree = Args.getDoubleMin(cmd.getOptionValue("avg-degree", String.valueOf(pd.getDefaultValue())), pd.getLowerBoundDouble());

        pd = param.get(ParamAttrs.MAX_DEGREE);
        maxDegree = Args.getIntegerMin(cmd.getOptionValue("max-degree", String.valueOf(pd.getDefaultValue())), pd.getLowerBoundInt());

        pd = param.get(ParamAttrs.MAX_INDEGREE);
        maxIndegree = Args.getIntegerMin(cmd.getOptionValue("max-indegree", String.valueOf(pd.getDefaultValue())), pd.getLowerBoundInt());

        pd = param.get(ParamAttrs.MAX_OUTDEGREE);
        maxOutdegree = Args.getIntegerMin(cmd.getOptionValue("max-outdegree", String.valueOf(pd.getDefaultValue())), pd.getLowerBoundInt());

        connected = cmd.hasOption("connected");

        pd = param.get(ParamAttrs.MIN_CATEGORIES);
        minCategories = Args.getIntegerMin(cmd.getOptionValue("min-categories", String.valueOf(pd.getDefaultValue())), pd.getLowerBoundInt());

        pd = param.get(ParamAttrs.MAX_CATEGORIES);
        maxCategories = Args.getIntegerMin(cmd.getOptionValue("max-categories", String.valueOf(pd.getDefaultValue())), pd.getLowerBoundInt());
    }

    @Override
    public Simulation getSimulation() {
        return new BayesNetSimulation(new RandomForward());
    }

    @Override
    public Parameters getParameters() {
        Parameters parameters = new Parameters();

        // RandomForward
        parameters.set(ParamAttrs.NUM_MEASURES, numOfVariables);
        parameters.set(ParamAttrs.NUM_LATENTS, numOfLatentConfounders);
        parameters.set(ParamAttrs.AVG_DEGREE, avgDegree);
        parameters.set(ParamAttrs.MAX_DEGREE, maxDegree);
        parameters.set(ParamAttrs.MAX_INDEGREE, maxIndegree);
        parameters.set(ParamAttrs.MAX_OUTDEGREE, maxOutdegree);
        parameters.set(ParamAttrs.CONNECTED, connected);

        // BayesPm
        parameters.set(ParamAttrs.MIN_CATEGORIES, minCategories);
        parameters.set(ParamAttrs.MAX_CATEGORIES, maxCategories);

        // BayesNetSimulation
        parameters.set(ParamAttrs.NUM_RUNS, 1);
        parameters.set(ParamAttrs.DIFFERENT_GRAPHS, Boolean.FALSE);
        parameters.set(ParamAttrs.SAMPLE_SIZE, numOfCases);

        return parameters;
    }

    @Override
    public SimulationType getSimulationType() {
        return SimulationType.BAYES_NET_RAND_FWD;
    }

}
