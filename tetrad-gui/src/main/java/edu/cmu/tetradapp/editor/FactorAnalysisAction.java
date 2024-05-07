///////////////////////////////////////////////////////////////////////////////
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

package edu.cmu.tetradapp.editor;


import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.FactorAnalysis;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TextTable;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import org.apache.commons.math3.util.FastMath;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Vector;

/**
 * Created by IntelliJ IDEA.
 *
 * @author Mike Freenor
 * @version $Id: $Id
 */
public class FactorAnalysisAction extends AbstractAction {


    /**
     * The data edtitor that action is attached to.
     */
    private final DataEditor dataEditor;


    /**
     * Constructs the <code>HistogramAction</code> given the <code>DataEditor</code> that its attached to.
     *
     * @param editor a {@link edu.cmu.tetradapp.editor.DataEditor} object
     */
    public FactorAnalysisAction(DataEditor editor) {
        super("Factor Analysis...");
        this.dataEditor = editor;
    }

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        java.util.List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 9; i++) {
            nodes.add(new ContinuousVariable("X" + (i + 1)));
        }

        Graph graph = new Dag(RandomGraph.randomGraph(nodes, 0, 9,
                30, 15, 15, false));
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(500, false);
        ICovarianceMatrix cov = new CovarianceMatrix(data);

        FactorAnalysis factorAnalysis = new FactorAnalysis(cov);
        //factorAnalysis.centroidUnity();
        factorAnalysis.successiveResidual();
    }

    /**
     * {@inheritDoc}
     */
    public void actionPerformed(ActionEvent e) {
        DataSet dataSet = (DataSet) this.dataEditor.getSelectedDataModel();
        if (dataSet == null || dataSet.getNumColumns() == 0) {
            JOptionPane.showMessageDialog(findOwner(), "Cannot perform factor analysis on an empty data set.");
            return;
        }

        FactorAnalysis factorAnalysis = new FactorAnalysis(dataSet);
        //factorAnalysis.centroidUnity();

        JPanel panel = createDialog(factorAnalysis);

        EditorWindow window = new EditorWindow(panel,
                "Factor Loading Matrices", "Close", false, this.dataEditor);
        DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
        window.setVisible(true);

    }

    private JPanel createDialog(FactorAnalysis analysis) {
        final double threshold = .2;

        Matrix unrotatedSolution = analysis.successiveResidual();
        Matrix rotatedSolution = analysis.successiveFactorVarimax(unrotatedSolution);

        DataSet dataSet = (DataSet) this.dataEditor.getSelectedDataModel();
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        String output = "Unrotated Factor Loading Matrix:\n";

        output += tableString(unrotatedSolution, nf, Double.POSITIVE_INFINITY);

        if (unrotatedSolution.getNumColumns() != 1) {
            output += "\n\nRotated Matrix (using sequential varimax):\n";

            output += tableString(rotatedSolution, nf, threshold);

        }

        JTextArea display = new JTextArea(output);
        JScrollPane scrollPane = new JScrollPane(display);
        scrollPane.setPreferredSize(new Dimension(500, 400));
        display.setEditable(false);
        display.setFont(new Font("Monospaced", Font.PLAIN, 12));
        //editorPanel.addPropertyChangeListener(new NormalityTestListener(display));


        SemGraph graph = new SemGraph();

        Vector<Node> observedVariables = new Vector<>();

        assert dataSet != null;
        for (Node a : dataSet.getVariables()) {
            graph.addNode(a);
            observedVariables.add(a);
        }

        Vector<Node> factors = new Vector<>();

        for (int i = 0; i < rotatedSolution.getNumColumns(); i++) {
            ContinuousVariable factor = new ContinuousVariable("Factor" + (i + 1));
            factor.setNodeType(NodeType.LATENT);
            graph.addNode(factor);
            factors.add(factor);
        }

        for (int i = 0; i < rotatedSolution.getNumRows(); i++) {
            for (int j = 0; j < rotatedSolution.getNumColumns(); j++) {
                if (FastMath.abs(rotatedSolution.get(i, j)) > threshold) {
                    graph.addDirectedEdge(factors.get(j), observedVariables.get(i));
                    //HEY JOE -- rotatedSolution.get(i, j) is the edge coeficient
                }
            }
        }

        LayoutUtil.defaultLayout(graph);
        LayoutUtil.fruchtermanReingoldLayout(graph);

        GraphWorkbench workbench = new GraphWorkbench(graph);

        JScrollPane graphPane = new JScrollPane(workbench);
        graphPane.setPreferredSize(new Dimension(500, 400));

        Box box = Box.createHorizontalBox();
        box.add(scrollPane);

        box.add(Box.createHorizontalStrut(3));
        //box.add(editorPanel);
        box.add(Box.createHorizontalStrut(5));
        box.add(Box.createHorizontalGlue());

        Box vBox = Box.createVerticalBox();
        vBox.add(Box.createVerticalStrut(15));
        vBox.add(box);
        vBox.add(Box.createVerticalStrut(5));
        box.add(graphPane);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(vBox, BorderLayout.CENTER);

        return panel;
    }

    //============================== Private methods ============================//

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

    private JFrame findOwner() {
        return (JFrame) SwingUtilities.getAncestorOfClass(
                JFrame.class, this.dataEditor);
    }
}




