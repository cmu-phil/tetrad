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
package edu.cmu.tetrad.cli.search;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pattern.Fgs;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.cli.AbstractAlgorithmCli;
import edu.cmu.tetrad.cli.AlgorithmType;
import edu.cmu.tetrad.cli.CmdOptions;
import edu.cmu.tetrad.cli.ParamAttrs;
import edu.cmu.tetrad.cli.validation.DataValidation;
import edu.cmu.tetrad.cli.validation.NonZeroVariance;
import edu.cmu.tetrad.cli.validation.UniqueVariableNames;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.io.DataReader;
import edu.cmu.tetrad.io.TabularContinuousDataReader;
import edu.cmu.tetrad.util.Parameters;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

/**
 *
 * Sep 12, 2016 1:56:30 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class FgscCli extends AbstractAlgorithmCli {

    protected double penaltyDiscount;
    protected int maxDegree;
    protected boolean faithfulnessAssumed;

    protected boolean skipUniqueVarName;
    protected boolean skipZeroVariance;

    public FgscCli(String[] args) {
        super(args);
    }

    @Override
    public void printValidationInfos(Formatter fmt) {
        fmt.format("ensure variable names are unique = %s%n", !skipUniqueVarName);
        fmt.format("ensure variables have non-zero variance = %s%n", !skipZeroVariance);
    }

    @Override
    public void printParameterInfos(Formatter fmt) {
        fmt.format("penalty discount = %f%n", penaltyDiscount);
        fmt.format("max degree = %d%n", maxDegree);
        fmt.format("faithfulness assumed = %s%n", faithfulnessAssumed);
    }

    @Override
    public Parameters getParameters() {
        Parameters parameters = new Parameters();
        parameters.set(ParamAttrs.PENALTY_DISCOUNT, penaltyDiscount);
        parameters.set(ParamAttrs.MAX_DEGREE, maxDegree);
        parameters.set(ParamAttrs.FAITHFULNESS_ASSUMED, faithfulnessAssumed);
        parameters.set(ParamAttrs.VERBOSE, verbose);

        return parameters;
    }

    @Override
    public Algorithm getAlgorithm(IKnowledge knowledge) {
        Fgs fgs = new Fgs(new SemBicScore());
        if (knowledge != null) {
            fgs.setKnowledge(knowledge);
        }

        return fgs;
    }

    @Override
    public DataReader getDataReader(Path dataFile, char delimiter) {
        return new TabularContinuousDataReader(dataFile, delimiter);
    }

    @Override
    public List<DataValidation> getDataValidations(DataSet dataSet, Path dirOut, String filePrefix) {
        List<DataValidation> validations = new LinkedList<>();

        String outputDir = dirOut.toString();
        if (!skipUniqueVarName) {
            if (validationOutput) {
                validations.add(new UniqueVariableNames(dataSet, Paths.get(outputDir, filePrefix + "_duplicate_var_name.txt")));
            } else {
                validations.add(new UniqueVariableNames(dataSet));
            }
        }
        if (!skipZeroVariance) {
            if (validationOutput) {
                validations.add(new NonZeroVariance(dataSet, numOfThreads, Paths.get(outputDir, filePrefix + "_zero_variance.txt")));
            } else {
                validations.add(new NonZeroVariance(dataSet, numOfThreads));
            }
        }

        return validations;
    }

    @Override
    public void parseRequiredOptions(CommandLine cmd) throws Exception {
    }

    @Override
    public void parseOptionalOptions(CommandLine cmd) throws Exception {
        penaltyDiscount = CmdOptions.getDouble(CmdOptions.PENALTY_DISCOUNT, ParamAttrs.PENALTY_DISCOUNT, cmd);
        maxDegree = CmdOptions.getInt(CmdOptions.MAX_DEGREE, ParamAttrs.MAX_DEGREE, cmd);
        faithfulnessAssumed = cmd.hasOption(CmdOptions.FAITHFULNESS_ASSUMED);
        skipUniqueVarName = cmd.hasOption(CmdOptions.SKIP_UNIQUE_VAR_NAME);
        skipZeroVariance = cmd.hasOption(CmdOptions.SKIP_NONZERO_VARIANCE);
    }

    @Override
    public List<Option> getRequiredOptions() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<Option> getOptionalOptions() {
        List<Option> options = new LinkedList<>();
        options.add(new Option(null, CmdOptions.PENALTY_DISCOUNT, true, CmdOptions.getDescription(CmdOptions.PENALTY_DISCOUNT)));
        options.add(new Option(null, CmdOptions.MAX_DEGREE, true, CmdOptions.getDescription(CmdOptions.MAX_DEGREE)));
        options.add(new Option(null, CmdOptions.FAITHFULNESS_ASSUMED, false, CmdOptions.getDescription(CmdOptions.FAITHFULNESS_ASSUMED)));
        options.add(new Option(null, CmdOptions.SKIP_UNIQUE_VAR_NAME, false, CmdOptions.getDescription(CmdOptions.SKIP_UNIQUE_VAR_NAME)));
        options.add(new Option(null, CmdOptions.SKIP_NONZERO_VARIANCE, false, CmdOptions.getDescription(CmdOptions.SKIP_NONZERO_VARIANCE)));

        return options;
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.FGSC;
    }

}
