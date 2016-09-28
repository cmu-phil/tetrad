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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * An extension of the CommandLineParser class. It provides additional
 * functionalities that are not avaliable in CommandLineParser class.
 *
 * Jan 5, 2016 12:42:38 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class ExtendedCommandLineParser implements CommandLineParser {

    private final CommandLineParser commandLineParser;

    /**
     * Constructor
     *
     * @param commandLineParser a command-line parser
     */
    public ExtendedCommandLineParser(CommandLineParser commandLineParser) {
        this.commandLineParser = commandLineParser;
    }

    /**
     * Query to see if the option is passed in from command-line.
     *
     * @param option option to search for
     * @param arguments inputs from the command-line
     * @return true if the option is passed in from command-line
     */
    public boolean hasOption(Option option, String[] arguments) {
        if (option == null || arguments == null) {
            return false;
        }

        for (String argument : arguments) {
            if (argument.startsWith("--")) {
                argument = argument.substring(2, argument.length());
                if (argument.equals(option.getLongOpt())) {
                    return true;
                }
            } else if (argument.startsWith("-")) {
                argument = argument.substring(1, argument.length());
                if (argument.equals(option.getOpt())) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Parse the arguments according to the specified options.
     *
     * @param options the specified Options
     * @param arguments the command line arguments
     * @return the list of atomic option and value tokens
     * @throws ParseException whenever there are any problems encountered while
     * parsing the command line tokens
     */
    @Override
    public CommandLine parse(Options options, String[] arguments) throws ParseException {
        return this.commandLineParser.parse(options, arguments);
    }

    /**
     * Parse the arguments according to the specified options.
     *
     * @param options the specified Options
     * @param arguments the command line arguments
     * @param stopAtNonOption if true an unrecognized argument stops the parsing
     * and the remaining arguments are added to the CommandLines args list. If
     * false an unrecognized argument triggers a ParseException.
     * @return the list of atomic option and value tokens
     * @throws ParseException if there are any problems encountered while
     * parsing the command line tokens
     */
    @Override
    public CommandLine parse(Options options, String[] arguments, boolean stopAtNonOption) throws ParseException {
        return this.commandLineParser.parse(options, arguments, stopAtNonOption);
    }

}
