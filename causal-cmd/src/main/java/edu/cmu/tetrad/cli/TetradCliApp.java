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
import edu.cmu.tetrad.cli.search.FgsDiscrete;
import edu.cmu.tetrad.cli.util.Args;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Feb 9, 2016 3:23:56 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TetradCliApp {

    private static final Logger LOGGER = LoggerFactory.getLogger(TetradCliApp.class);

    private static final Options MAIN_OPTIONS = new Options();

    static {
        Option requiredOption = new Option(null, "algorithm", true, "Choose one of the following: fgs or fgs-discrete.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);

        MAIN_OPTIONS.addOption(null, "version", false, "Version.");
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

        if (Args.hasOption(args, "version") || Args.hasOption(args, "v")) {
            showVersion();
        } else {
            algorithm = Args.getOptionValue(args, "algorithm");
            if (algorithm == null) {
                showHelp();
            } else {
                args = Args.removeOption(args, "algorithm");
                switch (algorithm) {
                    case "fgs":
                        FgsCli.main(args);
                        break;
                    case "fgs-discrete":
                        FgsDiscrete.main(args);
                        break;
                    default:
                        System.err.printf("Unknown algorithm: %s\n", algorithm);
                        showHelp();
                }
            }
        }
    }

    private static void showVersion() {
        try {
            JarFile jarFile = new JarFile(TetradCliApp.class.getProtectionDomain().getCodeSource().getLocation().getPath(), true);
            Manifest manifest = jarFile.getManifest();
            Attributes attributes = manifest.getMainAttributes();
            String artifactId = attributes.getValue("Implementation-Title");
            String version = attributes.getValue("Implementation-Version");
            System.out.printf("%s version: %s%n", artifactId, version);
        } catch (IOException exception) {
            String errMsg = "Unable to retrieve version number.";
            System.err.println(errMsg);
            LOGGER.error(errMsg, exception);
        }
    }

    private static void showHelp() {
        String cmdLineSyntax = "java -jar ";
        try {
            JarFile jarFile = new JarFile(TetradCliApp.class.getProtectionDomain().getCodeSource().getLocation().getPath(), true);
            Manifest manifest = jarFile.getManifest();
            Attributes attributes = manifest.getMainAttributes();
            String artifactId = attributes.getValue("Implementation-Title");
            String version = attributes.getValue("Implementation-Version");
            cmdLineSyntax += String.format("%s-%s.jar", artifactId, version);
        } catch (IOException exception) {
            cmdLineSyntax += "causal-cmd.jar";
        }

        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(cmdLineSyntax, MAIN_OPTIONS, true);
    }

}
