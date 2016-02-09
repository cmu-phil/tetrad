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

import edu.cmu.tetrad.cli.search.FgsCli;
import edu.cmu.tetrad.cli.util.Args;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

/**
 *
 * Feb 9, 2016 3:23:56 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TetradCliApp {

    private static final Options MAIN_OPTIONS = new Options();

    static {
        Option requiredOption = new Option(null, "algorithm", true, "Algorithm name.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);
    }

    private static String algorithm;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            showHelp();
            return;
        }

        algorithm = Args.getOptionValue(args, "algorithm");
        if (algorithm == null) {
            showHelp();
        } else {
            switch (algorithm) {
                case "fgs":
                    FgsCli.main(Args.removeOption(args, "algorithm"));
                    break;
                default:
                    System.err.printf("Unknow algorithm: %s\n", algorithm);
                    showHelp();
            }
        }
    }

    private static void showHelp() {
        String cmdLineSyntax = "java -jar tetrad-cli.jar";
        String header = "";
        String footer = "algorithm: fgs";
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(cmdLineSyntax, header, MAIN_OPTIONS, footer);
    }

}
