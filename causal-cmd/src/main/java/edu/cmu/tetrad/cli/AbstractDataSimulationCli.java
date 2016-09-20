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
package edu.cmu.tetrad.cli;

import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.data.DataSet;
import java.nio.file.Path;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Sep 19, 2016 4:17:13 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public abstract class AbstractDataSimulationCli extends AbstractApplicationCli implements SimulationCli {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDataSimulationCli.class);

    protected int numOfVariables;
    protected int numOfCases;
    protected char delimiter;
    protected Path dirOut;
    protected static String outputPrefix;

    public AbstractDataSimulationCli(String[] args) {
        super(args);
        intit();
    }

    public abstract SimulationType getSimulationType();

    public abstract Simulation getSimulation();

    private void intit() {
        setOptions();
    }

    @Override
    public void simulate() {
        SimulationType simulationType = getSimulationType();
        if (needsToShowHelp()) {
            showHelp(simulationType.getCmd());
        }

        parseOptions();

        Simulation simulation = getSimulation();
        simulation.createData(getParameters());
        DataSet dataSet = simulation.getDataSet(0);
        System.out.println(dataSet);
    }

    @Override
    public void setCommonRequiredOptions() {
        Option requiredOption = new Option(null, "var", true, "Number of variables.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);

        requiredOption = new Option(null, "case", true, "Number of cases.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);
    }

    @Override
    public void setCommonOptionalOptions() {
        MAIN_OPTIONS.addOption("d", "delimiter", true, "Data delimiter either comma, semicolon, space, colon, or tab. Default: comma for *.csv, else tab.");
        MAIN_OPTIONS.addOption("o", "out", true, "Output directory.");
        MAIN_OPTIONS.addOption(null, "output-prefix", true, "Prefix name for output files.");
        MAIN_OPTIONS.addOption(null, "help", false, "Show help.");
    }

    @Override
    public void parseCommonRequiredOptions(CommandLine cmd) throws Exception {
        numOfVariables = Args.getIntegerMin(cmd.getOptionValue("var"), 1);
        numOfCases = Args.getIntegerMin(cmd.getOptionValue("case"), 1);
    }

    @Override
    public void parseCommonOptionalOptions(CommandLine cmd) throws Exception {
        delimiter = Args.getDelimiterForName(cmd.getOptionValue("delimiter", "tab"));
        dirOut = Args.getPathDir(cmd.getOptionValue("out", "."), false);
        outputPrefix = cmd.getOptionValue("output-prefix", String.format("sim_data_%dvars_%dcases_%d", numOfVariables, numOfCases, System.currentTimeMillis()));
    }

}
