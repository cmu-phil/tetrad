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

import edu.cmu.tetrad.cli.util.AppTool;
import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.util.ParamDescription;
import edu.cmu.tetrad.util.Parameters;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

/**
 *
 * Sep 19, 2016 12:04:05 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public abstract class AbstractApplicationCli {

    protected final Options MAIN_OPTIONS = new Options();

    protected final String[] args;

    public AbstractApplicationCli(String[] args) {
        this.args = args;
    }

    public abstract List<Option> getRequiredOptions();

    public abstract List<Option> getOptionalOptions();

    public abstract void setCommonRequiredOptions();

    public abstract void setCommonOptionalOptions();

    public abstract void parseCommonRequiredOptions(CommandLine cmd) throws Exception;

    public abstract void parseCommonOptionalOptions(CommandLine cmd) throws Exception;

    public abstract void parseRequiredOptions(CommandLine cmd) throws Exception;

    public abstract void parseOptionalOptions(CommandLine cmd) throws Exception;

    public abstract Parameters getParameters();

    protected String createDescription(ParamDescription paramDescription) {
        return String.format("%s. Default %s.", paramDescription.getDescription(), paramDescription.getDefaultValue());
    }

    protected void setRequiredOptions() {
        List<Option> options = getRequiredOptions();
        if (options != null) {
            for (Option option : options) {
                MAIN_OPTIONS.addOption(option);
            }
        }
    }

    protected void setOptionalOptions() {
        List<Option> options = getOptionalOptions();
        if (options != null) {
            for (Option option : options) {
                MAIN_OPTIONS.addOption(option);
            }
        }
    }

    protected void setOptions() {
        setCommonRequiredOptions();
        setCommonOptionalOptions();
        setRequiredOptions();
        setOptionalOptions();
    }

    protected void parseOptions() {
        try {
            CommandLineParser cmdParser = new DefaultParser();
            CommandLine cmd = cmdParser.parse(MAIN_OPTIONS, args);
            parseCommonRequiredOptions(cmd);
            parseCommonOptionalOptions(cmd);
            parseRequiredOptions(cmd);
            parseOptionalOptions(cmd);
        } catch (Exception exception) {
            System.err.println(exception.getLocalizedMessage());
            System.exit(-127);
        }
    }

    protected boolean needsToShowHelp() {
        return args == null || args.length == 0 || Args.hasLongOption(args, "help");
    }

    protected void showHelp(String cmd) {
        AppTool.showHelp(cmd, MAIN_OPTIONS);
    }

}
