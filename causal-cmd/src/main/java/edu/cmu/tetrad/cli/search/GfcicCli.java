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
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.cli.AbstractAlgorithmCli;
import edu.cmu.tetrad.cli.AlgorithmType;
import edu.cmu.tetrad.cli.ParamAttrs;
import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.cli.validation.DataValidation;
import edu.cmu.tetrad.cli.validation.NonZeroVariance;
import edu.cmu.tetrad.cli.validation.UniqueVariableNames;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.io.DataReader;
import edu.cmu.tetrad.io.TabularContinuousDataReader;
import edu.cmu.tetrad.util.ParamDescriptions;
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
 * Sep 14, 2016 3:30:52 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class GfcicCli extends AbstractAlgorithmCli {

    private double alpha;
    protected double penaltyDiscount;
    protected int maxIndegree;
    protected boolean faithfulnessAssumed;

    protected boolean skipUniqueVarName;
    protected boolean skipZeroVariance;

    public GfcicCli(String[] args) {
        super(args);
    }

    @Override
    public void printValidationInfos(Formatter fmt) {
        fmt.format("ensure variable names are unique = %s%n", !skipUniqueVarName);
        fmt.format("ensure variables have non-zero variance = %s%n", !skipZeroVariance);
    }

    @Override
    public void printParameterInfos(Formatter fmt) {
        fmt.format("alpha = %f%n", alpha);
        fmt.format("penalty discount = %f%n", penaltyDiscount);
        fmt.format("max indegree = %d%n", maxIndegree);
        fmt.format("faithfulness assumed = %s%n", faithfulnessAssumed);
    }

    @Override
    public Parameters getParameters() {
        Parameters parameters = new Parameters();
        parameters.set(ParamAttrs.ALPHA, alpha);
        parameters.set(ParamAttrs.PENALTY_DISCOUNT, penaltyDiscount);
        parameters.set(ParamAttrs.MAX_INDEGREE, maxIndegree);
        parameters.set(ParamAttrs.FAITHFULNESS_ASSUMED, faithfulnessAssumed);
        parameters.set(ParamAttrs.VERBOSE, verbose);

        return parameters;
    }

    @Override
    public Algorithm getAlgorithm(IKnowledge knowledge) {
        Gfci gfci = new Gfci(new FisherZ(), new SemBicScore());
        if (knowledge != null) {
            gfci.setKnowledge(knowledge);
        }

        return gfci;
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
        ParamDescriptions param = ParamDescriptions.instance();
        alpha = Args.getDouble(cmd.getOptionValue("alpha", String.valueOf(param.get(ParamAttrs.ALPHA).getDefaultValue())));
        penaltyDiscount = Args.getDoubleMin(cmd.getOptionValue("penalty-discount", String.valueOf(param.get(ParamAttrs.PENALTY_DISCOUNT).getDefaultValue())), 0);
        maxIndegree = Args.getIntegerMin(cmd.getOptionValue("max-indegree", String.valueOf(param.get(ParamAttrs.MAX_INDEGREE).getDefaultValue())), -1);
        faithfulnessAssumed = !cmd.hasOption("faithfulness-assumed");
        skipUniqueVarName = cmd.hasOption("skip-unique-var-name");
        skipZeroVariance = cmd.hasOption("skip-non-zero-variance");
    }

    @Override
    public List<Option> getRequiredOptions() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<Option> getOptionalOptions() {
        ParamDescriptions param = ParamDescriptions.instance();

        List<Option> options = new LinkedList<>();
        options.add(new Option(null, "alpha", true, createDescription(param.get(ParamAttrs.PENALTY_DISCOUNT))));
        options.add(new Option(null, "penalty-discount", true, createDescription(param.get(ParamAttrs.PENALTY_DISCOUNT))));
        options.add(new Option(null, "max-indegree", true, createDescription(param.get(ParamAttrs.MAX_INDEGREE))));
        options.add(new Option(null, "faithfulness-assumed", true, createDescription(param.get(ParamAttrs.FAITHFULNESS_ASSUMED))));
        options.add(new Option(null, "skip-unique-var-name", false, "Skip check for unique variable names."));
        options.add(new Option(null, "skip-non-zero-variance", false, "Skip check for zero variance variables."));

        return options;
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.GFCIC;
    }

}
