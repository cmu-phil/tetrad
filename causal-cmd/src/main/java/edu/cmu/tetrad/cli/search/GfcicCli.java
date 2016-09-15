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
import edu.cmu.tetrad.cli.AlgorithmType;
import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.util.Parameters;
import java.util.Formatter;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

/**
 *
 * Sep 14, 2016 3:30:52 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class GfcicCli extends FgscCli {

    private double alpha;

    public GfcicCli(String[] args) {
        super(args);
    }

    @Override
    public void printParameterInfos(Formatter fmt) {
        fmt.format("alpha = %f%n", alpha);
        super.printParameterInfos(fmt);
    }

    @Override
    public Parameters getParameters() {
        Parameters parameters = super.getParameters();
        parameters.set("alpha", alpha);

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
    public void parseOptionalOptions(CommandLine cmd) throws Exception {
        super.parseOptionalOptions(cmd);
        alpha = Args.getDouble(cmd.getOptionValue("alpha", "0.01"));
    }

    @Override
    public List<Option> getOptionalOptions() {
        List<Option> options = super.getOptionalOptions();
        options.add(new Option(null, "alpha", true, "Alpha for FisherZ test. Default is 0.01"));

        return options;
    }

    @Override
    public AlgorithmType getAlgorithmType() {
        return AlgorithmType.GFCIC;
    }

}
