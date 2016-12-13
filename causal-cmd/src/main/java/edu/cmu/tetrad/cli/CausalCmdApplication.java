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

import edu.cmu.tetrad.cli.search.FGEScCli;
import edu.cmu.tetrad.cli.search.FGESdCli;
import edu.cmu.tetrad.cli.search.GFCIcCli;
import edu.cmu.tetrad.cli.simulation.data.BayesNetRandomForwardCli;
import edu.cmu.tetrad.cli.simulation.data.SemRandomForwardCli;
import edu.cmu.tetrad.cli.util.AppTool;
import edu.cmu.tetrad.cli.util.Args;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;

/**
 *
 * Sep 9, 2016 2:19:49 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class CausalCmdApplication {

    private static final Options MAIN_OPTIONS = new Options();

    private static final String ALGO_OPT = "algorithm";
    private static final String SIM_DATA_OPT = "simulate-data";
    private static final String VERSION_OPT = "version";

    private static final Map<String, AlgorithmType> ALGO_TYPES = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private static final Map<String, SimulationType> SIM_TYPES = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    static {
        populateMainOptions();
        populateCmdTypes();
    }

    private static void populateCmdTypes() {
        for (AlgorithmType type : AlgorithmType.values()) {
            ALGO_TYPES.put(type.getCmd(), type);
        }
        for (SimulationType type : SimulationType.values()) {
            SIM_TYPES.put(type.getCmd(), type);
        }
    }

    private static void populateMainOptions() {
        OptionGroup optGrp = new OptionGroup();
        optGrp.addOption(new Option(null, ALGO_OPT, true, algorithmCmd()));
        optGrp.addOption(new Option(null, SIM_DATA_OPT, true, simulationCmd()));
        optGrp.setRequired(true);
        MAIN_OPTIONS.addOptionGroup(optGrp);

        MAIN_OPTIONS.addOption(null, VERSION_OPT, false, "Show software version.");
    }

    private static String algorithmCmd() {
        StringBuilder algoOpt = new StringBuilder();
        AlgorithmType[] types = AlgorithmType.values();
        int lastIndex = types.length - 1;
        for (int i = 0; i < lastIndex; i++) {
            algoOpt.append(types[i].getCmd());
            algoOpt.append(", ");
        }
        algoOpt.append(types[lastIndex].getCmd());

        return algoOpt.toString();
    }

    private static String simulationCmd() {
        StringBuilder algoOpt = new StringBuilder();
        SimulationType[] types = SimulationType.values();
        int lastIndex = types.length - 1;
        for (int i = 0; i < lastIndex; i++) {
            algoOpt.append(types[i].getCmd());
            algoOpt.append(", ");
        }
        algoOpt.append(types[lastIndex].getCmd());

        return algoOpt.toString();
    }

    private static AlgorithmCli getAlgorithmCli(String[] args) {
        String algorithm = Args.getOptionValue(args, ALGO_OPT);
        if (algorithm == null) {
            algorithm = "";
        }

        AlgorithmType algorithmType = ALGO_TYPES.get(algorithm);
        if (algorithmType == null) {
            return null;
        }

        args = Args.removeOption(args, ALGO_OPT);
        switch (algorithmType) {
            case FGESC:
                return new FGEScCli(args);
            case FGESD:
                return new FGESdCli(args);
            case GFCIC:
                return new GFCIcCli(args);
            default:
                return null;
        }
    }

    private static void showHelp() {
        AppTool.showHelp(MAIN_OPTIONS, "Additional parameters are available when using --algorithm <arg> or --simulate-data <arg>.");
    }

    private static void runAlgorithm(String[] args) {
        AlgorithmCli algorithmCli = getAlgorithmCli(args);
        if (algorithmCli == null) {
            showHelp();
        } else {
            algorithmCli.run();
        }
    }

    private static SimulationCli getSimulationCli(String[] args) {
        String simulation = Args.getOptionValue(args, SIM_DATA_OPT);
        if (simulation == null) {
            simulation = "";
        }

        SimulationType simulationType = SIM_TYPES.get(simulation);
        if (simulationType == null) {
            return null;
        }

        args = Args.removeOption(args, SIM_DATA_OPT);
        switch (simulationType) {
            case SEM_RAND_FWD:
                return new SemRandomForwardCli(args);
            case BAYES_NET_RAND_FWD:
                return new BayesNetRandomForwardCli(args);
            default:
                return null;
        }
    }

    private static void runSimulation(String[] args) {
        SimulationCli simulationCli = getSimulationCli(args);
        if (simulationCli == null) {
            showHelp();
        } else {
            simulationCli.simulate();
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (Args.hasLongOption(args, VERSION_OPT)) {
            System.out.println(AppTool.jarTitle() + " version " + AppTool.jarVersion());
        } else {
            boolean algoOpt = Args.hasLongOption(args, ALGO_OPT);
            boolean simDataOpt = Args.hasLongOption(args, SIM_DATA_OPT);
            if (algoOpt ^ simDataOpt) {
                if (algoOpt) {
                    runAlgorithm(args);
                } else {
                    runSimulation(args);
                }
            } else {
                showHelp();
            }
        }
    }

}
