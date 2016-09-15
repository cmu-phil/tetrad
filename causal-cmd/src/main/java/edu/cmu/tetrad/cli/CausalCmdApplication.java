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

import edu.cmu.tetrad.cli.search.FgscCli;
import edu.cmu.tetrad.cli.search.FgsdCli;
import edu.cmu.tetrad.cli.search.GfcicCli;
import edu.cmu.tetrad.cli.util.AppTool;
import edu.cmu.tetrad.cli.util.Args;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

/**
 *
 * Sep 9, 2016 2:19:49 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class CausalCmdApplication {

    private static final Options MAIN_OPTIONS = new Options();

    private static final Map<String, AlgorithmType> ALGO_TYPES = new HashMap<>();

    static {
        AlgorithmType[] algorithmTypes = AlgorithmType.values();
        StringBuilder algoOpt = new StringBuilder();
        int lastIndex = algorithmTypes.length - 1;
        for (int i = 0; i < lastIndex; i++) {
            algoOpt.append(algorithmTypes[i].getCmd());
            algoOpt.append(", ");
        }
        algoOpt.append(algorithmTypes[lastIndex].getCmd());
        algoOpt.append(".");

        Option requiredOption = new Option(null, "algorithm", true, algoOpt.toString());
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);

        MAIN_OPTIONS.addOption(null, "version", false, "Version.");

        for (AlgorithmType algorithmType : AlgorithmType.values()) {
            ALGO_TYPES.put(algorithmType.getCmd(), algorithmType);
        }
    }

    private static AlgorithmCli getAlgorithmCli(String[] args) {
        String algoSwitch = "algorithm";
        String algorithm = Args.getOptionValue(args, algoSwitch);
        AlgorithmType algorithmType = ALGO_TYPES.get(algorithm);
        if (algorithmType == null) {
            return null;
        } else {
            args = Args.removeOption(args, algoSwitch);
            switch (algorithmType) {
                case FGSC:
                    return new FgscCli(args);
                case FGSD:
                    return new FgsdCli(args);
                case GFCIC:
                    return new GfcicCli(args);
                default:
                    return null;
            }
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            AppTool.showHelp(MAIN_OPTIONS);
        } else if (Args.hasLongOption(args, "version")) {
            System.out.println(AppTool.jarVersion());
        } else {
            AlgorithmCli algorithmCli = getAlgorithmCli(args);
            if (algorithmCli == null) {
                AppTool.showHelp(MAIN_OPTIONS);
            } else {
                algorithmCli.run();
            }
        }
    }

}
