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
 *
 * @author Michael Freenor
 */
public class FactorAnalysisRunner extends AbstractAlgorithmRunner {
    static final long serialVersionUID = 23L;

    private DataWrapper dataWrapper;
    private String output;

    private DataSet dataSet;

    private TetradMatrix rotatedSolution;

    private double threshold;

    public DataWrapper getDataWrapper() {
        return dataWrapper;
    }

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public FactorAnalysisRunner(DataWrapper dataWrapper, PcSearchParams pc) {
        super(dataWrapper, pc, null);
        this.dataWrapper = dataWrapper;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static FactorAnalysisRunner serializableInstance() {
        return new FactorAnalysisRunner(DataWrapper.serializableInstance(),
                PcSearchParams.serializableInstance());
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    public void execute() {
        FactorAnalysis analysis = new FactorAnalysis((DataSet)dataWrapper.getDataModelList().get(0));

        threshold = .2;

        TetradMatrix unrotatedSolution = analysis.successiveResidual();
        rotatedSolution = FactorAnalysis.successiveFactorVarimax(unrotatedSolution);

        dataSet = (DataSet) this.dataWrapper.getDataModelList().get(0);
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        output = "Unrotated Factor Loading Matrix:\n";

        output += tableString(unrotatedSolution, nf, Double.POSITIVE_INFINITY);

        if(unrotatedSolution.columns() != 1)
        {
            output += "\n\nRotated Matrix (using sequential varimax):\n";
            output += tableString(rotatedSolution, nf, threshold);
        }

        SemGraph graph = new SemGraph();

        Vector<Node> observedVariables = new Vector<Node>();

        for(Node a : getDataSet().getVariables())
        {
            graph.addNode(a);
            observedVariables.add(a);
        }

        Vector<Node> factors = new Vector<Node>();

        for(int i = 0; i < getRotatedSolution().columns(); i++)
        {
            ContinuousVariable factor = new ContinuousVariable("Factor" + (i + 1));
            factor.setNodeType(NodeType.LATENT);
            graph.addNode(factor);
            factors.add(factor);
        }

        for(int i = 0; i < getRotatedSolution().rows(); i++)
        {
            for(int j = 0; j < getRotatedSolution().columns(); j++)
            {
                if(Math.abs(getRotatedSolution().get(i, j)) > getThreshold())
                {
                    graph.addDirectedEdge(factors.get(j), observedVariables.get(i));
                }
            }
        }

//        this.window = panel;
        
        setResultGraph(graph);
    }
    
    private String tableString(TetradMatrix matrix, NumberFormat nf, double threshold) {
        TextTable table = new TextTable(matrix.rows() + 1, matrix.columns() + 1);

        for (int i = 0; i < matrix.rows() + 1; i++) {
            for (int j = 0; j < matrix.columns() + 1; j++) {
                if (i > 0 && j == 0) {
                    table.setToken(i, j, "X" + i);
                }
                else if (i == 0 && j > 0) {
                    table.setToken(i, j, "Factor " + j);
                }
                else if (i > 0 && j > 0) {
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
        List<String> names = new ArrayList<String>();
        return names;
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>
     * for the given node.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<List<Triple>>();
        return triplesList;
    }

    public boolean supportsKnowledge() {
        return true;
    }

    public String getOutput() {
        return output;
    }

    public DataSet getDataSet() {
        return dataSet;
    }

    public TetradMatrix getRotatedSolution() {
        return rotatedSolution;
    }

    public double getThreshold() {
        return threshold;
    }
}






