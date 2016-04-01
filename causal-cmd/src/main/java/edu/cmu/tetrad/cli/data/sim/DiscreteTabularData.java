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
package edu.cmu.tetrad.cli.data.sim;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.cli.util.Args;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataBox;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.VerticalIntDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * Mar 29, 2016 12:16:56 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class DiscreteTabularData {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscreteTabularData.class);

    private static final Options MAIN_OPTIONS = new Options();

    static {
        // added required option
        Option requiredOption = new Option(null, "variable", true, "Number of variables (columns) to generate.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);

        requiredOption = new Option(null, "case", true, "Number of cases (rows) to generate.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);

        requiredOption = new Option(null, "edge", true, "Edge factor.");
        requiredOption.setRequired(true);
        MAIN_OPTIONS.addOption(requiredOption);
    }

    private static int numOfVars;

    private static int numOfCases;

    private static double edgeFactor;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if (args == null || args.length == 0 || Args.hasLongOption(args, "help")) {
            Args.showHelp("simulate-discrete-data", MAIN_OPTIONS);
            return;
        }

        try {
            CommandLineParser cmdParser = new DefaultParser();
            CommandLine cmd = cmdParser.parse(MAIN_OPTIONS, args);

            numOfVars = Args.getIntegerMin(cmd.getOptionValue("variable"), 0);
            numOfCases = Args.getIntegerMin(cmd.getOptionValue("case"), 0);
            edgeFactor = Args.getDoubleMin(cmd.getOptionValue("edge"), 1.0);
        } catch (ParseException exception) {
            System.err.println(exception.getLocalizedMessage());
            Args.showHelp("simulate-discrete-data", MAIN_OPTIONS);
            System.exit(-127);
        }

        List<Node> vars = new ArrayList<>();
        for (int i = 0; i < numOfVars; i++) {
            vars.add(new ContinuousVariable("X" + i));
        }
        Graph graph = GraphUtils.randomGraphRandomForwardEdges(vars, 0, (int) (numOfVars * edgeFactor), 30, 12, 15, false, true);
        BayesPm pm = new BayesPm(graph, 3, 3);
        BayesIm im = new MlBayesIm(pm, MlBayesIm.RANDOM);
        DataSet data = im.simulateData(numOfCases, false);

        String[] variables = data.getVariableNames().toArray(new String[0]);
        int lastIndex = variables.length - 1;
        for (int i = 0; i < lastIndex; i++) {
            System.out.printf("%s,", variables[i]);
        }
        System.out.printf("%s%n", variables[lastIndex]);

        DataBox dataBox = ((BoxDataSet) data).getDataBox();
        VerticalIntDataBox box = (VerticalIntDataBox) dataBox;
//        int[][] matrix = box.getVariableVectors();
//        int numOfColumns = matrix.length;
//        int numOfRows = matrix[0].length;
//        int[][] dataset = new int[numOfRows][numOfColumns];
//        for (int i = 0; i < matrix.length; i++) {
//            for (int j = 0; j < matrix[i].length; j++) {
//                dataset[j][i] = matrix[i][j];
//            }
//        }
//        for (int[] rowData : dataset) {
//            lastIndex = rowData.length - 1;
//            for (int i = 0; i < lastIndex; i++) {
//                System.out.printf("%d,", rowData[i]);
//            }
//            System.out.printf("%s%n", rowData[lastIndex]);
//        }
    }

}
