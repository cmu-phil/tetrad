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
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.cli.AbstractAlgorithmCli;
import edu.cmu.tetrad.cli.AlgorithmType;
import edu.cmu.tetrad.cli.CmdOptions;
import edu.cmu.tetrad.cli.ParamAttrs;
import edu.cmu.tetrad.cli.validation.DataValidation;
import edu.cmu.tetrad.cli.validation.LimitDiscreteCategory;
import edu.cmu.tetrad.cli.validation.UniqueVariableNames;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.io.DataReader;
import edu.cmu.tetrad.io.VerticalTabularDiscreteDataReader;
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
 * Sep 14, 2016 2:31:24 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class FgsdCli extends AbstractAlgorithmCli {

    public static final int CATEGORY_LIMIT = 10;

    protected double structurePrior;
    protected double samplePrior;
    protected int maxDegree;
    protected boolean faithfulnessAssumed;

    protected boolean skipUniqueVarName;
    protected boolean skipCategoryLimit;

    public FgsdCli(String[] args) {
        super(args);
    }

    @Override
    public void printValidationInfos(Formatter fmt) {
        fmt.format("ensure variable names are unique = %s%n", !skipUniqueVarName);
        fmt.format("limit number of categories (%d) = %s%n", CATEGORY_LIMIT, !skipCategoryLimit);
    }

    @Override
    public void printParameterInfos(Formatter fmt) {
        fmt.format("sample prior = %f%n", samplePrior);
        fmt.format("structure prior = %f%n", structurePrior);
        fmt.format("max degree = %d%n", maxDegree);
        fmt.format("faithfulness assumed = %s%n", faithfulnessAssumed);
    }

    @Override
    public Parameters getParameters() {
        Parameters parameters = new Parameters();
        parameters.set(ParamAttrs.SAMPLE_PRIOR, samplePrior);
        parameters.set(ParamAttrs.STRUCTURE_PRIOR, structurePrior);
        parameters.set(ParamAttrs.MAX_DEGREE, maxDegree);
        parameters.set(ParamAttrs.FAITHFULNESS_ASSUMED, faithfulnessAssumed);
        parameters.set(ParamAttrs.VERBOSE, verbose);

        return parameters;
    }

    @Override
    public Algorithm getAlgorithm(IKnowledge knowledge) {
        Fgs fgs = new Fgs(new BdeuScore());
        if (knowledge != null) {
            fgs.setKnowledge(knowledge);
        }

        return fgs;
    }

    @Override
    public DataReader getDataReader(Path dataFile, char delimiter) {
        return new VerticalTabularDiscreteDataReader(dataFile, delimiter);
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
        if (!skipCategoryLimit) {
            validations.add(new LimitDiscreteCategory(dataSet, CATEGORY_LIMIT));
        }

        return validations;
    }

    @Override
    public void parseRequiredOptions(CommandLine cmd) throws Exception {
    }

    @Override
    public void parseOptionalOptions(CommandLine cmd) throws Exception {
        structurePrior = CmdOptions.getDouble(CmdOptions.STRUCTURE_PRIOR, ParamAttrs.STRUCTURE_PRIOR, cmd);
        samplePrior = CmdOptions.getDouble(CmdOptions.SAMPLE_PRIOR, ParamAttrs.SAMPLE_PRIOR, cmd);
        maxDegree = CmdOptions.getInt(CmdOptions.MAX_DEGREE, ParamAttrs.MAX_DEGREE, cmd);
        faithfulnessAssumed = cmd.hasOption(CmdOptions.FAITHFULNESS_ASSUMED);
        skipUniqueVarName = cmd.hasOption(CmdOptions.SKIP_UNIQUE_VAR_NAME);
        skipCategoryLimit = cmd.hasOption(CmdOptions.SKIP_CATEGORY_LIMIT);
    }

    @Override
    public List<Option> getRequiredOptions() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<Option> getOptionalOptions() {
        List<Option> options = new LinkedList<>();
        options.add(new Option(null, CmdOptions.STRUCTURE_PRIOR, true, CmdOptions.createDescription(ParamAttrs.STRUCTURE_PRIOR)));
        options.add(new Option(null, CmdOptions.SAMPLE_PRIOR, true, CmdOptions.createDescription(ParamAttrs.SAMPLE_PRIOR)));
        options.add(new Option(null, CmdOptions.MAX_DEGREE, true, CmdOptions.createDescription(ParamAttrs.MAX_DEGREE)));
        options.add(new Option(null, CmdOptions.FAITHFULNESS_ASSUMED, false, CmdOptions.getDescription(CmdOptions.FAITHFULNESS_ASSUMED)));
        options.add(new Option(null, CmdOptions.SKIP_UNIQUE_VAR_NAME, false, CmdOptions.getDescription(CmdOptions.SKIP_UNIQUE_VAR_NAME)));
        options.add(new Option(null, CmdOptions.SKIP_CATEGORY_LIMIT, false, CmdOptions.getDescription(CmdOptions.SKIP_CATEGORY_LIMIT)));

        return options;
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.FGSD;
    }

}
