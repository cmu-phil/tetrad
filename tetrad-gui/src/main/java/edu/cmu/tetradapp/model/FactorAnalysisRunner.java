/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.FactorAnalysis;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * <p>FactorAnalysisRunner class.</p>
 *
 * @author Michael Freenor
 * @version $Id: $Id
 */
public class FactorAnalysisRunner extends AbstractAlgorithmRunner {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The output of the algorithm.
     */
    private String output;

    /**
     * The rotated solution.
     */
    private Matrix rotatedSolution;

    /**
     * The threshold for the rotated solution.
     */
    private double threshold;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must contain a DataSet that is either a DataSet
     * or a DataSet or a DataList containing either a DataSet or a DataSet as its selected model.
     */
    private FactorAnalysisRunner(DataWrapper dataWrapper, Parameters pc) {
        super(dataWrapper, pc, null);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    /**
     * <p>execute.</p>
     */
    public void execute() {
        DataSet selectedModel = (DataSet) getDataModel();

        if (selectedModel == null) {
            throw new NullPointerException("Data not specified.");
        }

        FactorAnalysis analysis = new FactorAnalysis(selectedModel);

        this.threshold = .2;

        Matrix unrotatedSolution = analysis.successiveResidual();
        this.rotatedSolution = analysis.successiveFactorVarimax(unrotatedSolution);

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        this.output = "Unrotated Factor Loading Matrix:\n";

        this.output += tableString(unrotatedSolution, nf, Double.POSITIVE_INFINITY);

        if (unrotatedSolution.getNumColumns() != 1) {
            this.output += "\n\nRotated Matrix (using sequential varimax):\n";
            this.output += tableString(this.rotatedSolution, nf, this.threshold);
        }

        SemGraph graph = new SemGraph();

        Vector<Node> observedVariables = new Vector<>();

        for (Node a : selectedModel.getVariables()) {
            graph.addNode(a);
            observedVariables.add(a);
        }

        Vector<Node> factors = new Vector<>();

        for (int i = 0; i < getRotatedSolution().getNumColumns(); i++) {
            ContinuousVariable factor = new ContinuousVariable("Factor" + (i + 1));
            factor.setNodeType(NodeType.LATENT);
            graph.addNode(factor);
            factors.add(factor);
        }

        for (int i = 0; i < getRotatedSolution().getNumRows(); i++) {
            for (int j = 0; j < getRotatedSolution().getNumColumns(); j++) {
                if (FastMath.abs(getRotatedSolution().get(i, j)) > getThreshold()) {
                    graph.addDirectedEdge(factors.get(j), observedVariables.get(i));
                }
            }
        }

        setResultGraph(graph);
    }

    private String tableString(Matrix matrix, NumberFormat nf, double threshold) {
        TextTable table = new TextTable(matrix.getNumRows() + 1, matrix.getNumColumns() + 1);

        for (int i = 0; i < matrix.getNumRows() + 1; i++) {
            for (int j = 0; j < matrix.getNumColumns() + 1; j++) {
                if (i > 0 && j == 0) {
                    table.setToken(i, 0, "X" + i);
                } else if (i == 0 && j > 0) {
                    table.setToken(0, j, "Factor " + j);
                } else if (i > 0) {
                    double coefficient = matrix.get(i - 1, j - 1);
                    String token = !Double.isNaN(coefficient) ? nf.format(coefficient) : "Undefined";
                    token += FastMath.abs(coefficient) > threshold ? "*" : " ";
                    table.setToken(i, j, token);
                }
            }
        }

        return "\n" + table;

    }

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return getResultGraph();
    }

    /**
     * <p>getTriplesClassificationTypes.</p>
     *
     * @return the names of the triple classifications. Coordinates with getTriplesList.
     */
    public List<String> getTriplesClassificationTypes() {
        return new ArrayList<>();
    }

    /**
     * {@inheritDoc}
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        return new ArrayList<>();
    }

    /**
     * <p>supportsKnowledge.</p>
     *
     * @return a boolean
     */
    public boolean supportsKnowledge() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAlgorithmName() {
        return "Factor Analysis";
    }

    /**
     * <p>Getter for the field <code>output</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getOutput() {
        return this.output;
    }

    private Matrix getRotatedSolution() {
        return this.rotatedSolution;
    }

    private double getThreshold() {
        return this.threshold;
    }
}






