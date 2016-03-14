///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.FactorAnalysis;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetrad.util.TextTable;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * @author Michael Freenor
 */
public class FactorAnalysisRunner extends AbstractAlgorithmRunner {
    static final long serialVersionUID = 23L;

    private String output;

    private TetradMatrix rotatedSolution;

    private double threshold;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public FactorAnalysisRunner(DataWrapper dataWrapper, PcSearchParams pc) {
        super(dataWrapper, pc, null);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static FactorAnalysisRunner serializableInstance() {
        return new FactorAnalysisRunner(DataWrapper.serializableInstance(),
                PcSearchParams.serializableInstance());
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    public void execute() {
        DataSet selectedModel = (DataSet) getDataModel();

        if (selectedModel == null) {
            throw new NullPointerException("Data not specified.");
        }

        FactorAnalysis analysis = new FactorAnalysis(selectedModel);

        threshold = .2;

        TetradMatrix unrotatedSolution = analysis.successiveResidual();
        rotatedSolution = FactorAnalysis.successiveFactorVarimax(unrotatedSolution);

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        output = "Unrotated Factor Loading Matrix:\n";

        output += tableString(unrotatedSolution, nf, Double.POSITIVE_INFINITY);

        if (unrotatedSolution.columns() != 1) {
            output += "\n\nRotated Matrix (using sequential varimax):\n";
            output += tableString(rotatedSolution, nf, threshold);
        }

        SemGraph graph = new SemGraph();

        Vector<Node> observedVariables = new Vector<>();

        for (Node a : selectedModel.getVariables()) {
            graph.addNode(a);
            observedVariables.add(a);
        }

        Vector<Node> factors = new Vector<>();

        for (int i = 0; i < getRotatedSolution().columns(); i++) {
            ContinuousVariable factor = new ContinuousVariable("Factor" + (i + 1));
            factor.setNodeType(NodeType.LATENT);
            graph.addNode(factor);
            factors.add(factor);
        }

        for (int i = 0; i < getRotatedSolution().rows(); i++) {
            for (int j = 0; j < getRotatedSolution().columns(); j++) {
                if (Math.abs(getRotatedSolution().get(i, j)) > getThreshold()) {
                    graph.addDirectedEdge(factors.get(j), observedVariables.get(i));
                }
            }
        }

        setResultGraph(graph);
    }

    private String tableString(TetradMatrix matrix, NumberFormat nf, double threshold) {
        TextTable table = new TextTable(matrix.rows() + 1, matrix.columns() + 1);

        for (int i = 0; i < matrix.rows() + 1; i++) {
            for (int j = 0; j < matrix.columns() + 1; j++) {
                if (i > 0 && j == 0) {
                    table.setToken(i, j, "X" + i);
                } else if (i == 0 && j > 0) {
                    table.setToken(i, j, "Factor " + j);
                } else if (i > 0 && j > 0) {
                    double coefficient = matrix.get(i - 1, j - 1);
                    String token = !Double.isNaN(coefficient) ? nf.format(coefficient) : "Undefined";
                    token += Math.abs(coefficient) > threshold ? "*" : " ";
                    table.setToken(i, j, token);
                }
            }
        }

        return "\n" + table.toString();

    }

    public Graph getGraph() {
        return getResultGraph();
    }

    /**
     * @return the names of the triple classifications. Coordinates with getTriplesList.
     */
    public List<String> getTriplesClassificationTypes() {
        return new ArrayList<>();
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>
     * for the given node.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        return new ArrayList<>();
    }

    public boolean supportsKnowledge() {
        return true;
    }

    @Override
    public String getAlgorithmName() {
        return "Factor Analysis";
    }

    public String getOutput() {
        return output;
    }

    public TetradMatrix getRotatedSolution() {
        return rotatedSolution;
    }

    public double getThreshold() {
        return threshold;
    }
}






