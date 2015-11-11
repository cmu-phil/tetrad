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

package edu.cmu.tetradapp.editor;


import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.FactorAnalysis;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TextTable;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.NumberFormat;
import java.util.Vector;

/**
 * Created by IntelliJ IDEA.
 *
 * @author Mike Freenor
 */
public class FactorAnalysisAction extends AbstractAction {


    /**
     * The data edtitor that action is attached to.                        
     */
    private DataEditor dataEditor;


    /**
     * Constructs the <code>HistogramAction</code> given the <code>DataEditor</code>
     * that its attached to.
     *
     * @param editor
     */
    public FactorAnalysisAction(DataEditor editor) {
        super("Factor Analysis...");
        this.dataEditor = editor;
    }

    public void actionPerformed(ActionEvent e) {
        DataSet dataSet = (DataSet) dataEditor.getSelectedDataModel();
        if(dataSet == null || dataSet.getNumColumns() == 0){
            JOptionPane.showMessageDialog(findOwner(), "Cannot perform factor analysis on an empty data set.");
            return;
        }

        FactorAnalysis factorAnalysis = new FactorAnalysis(dataSet);
        //factorAnalysis.centroidUnity();

        JPanel panel = createDialog(factorAnalysis);

        EditorWindow window = new EditorWindow(panel,
                "Factor Loading Matrices", "Close", false, dataEditor);
        DesktopController.getInstance().addEditorWindow(window, JLayeredPane.PALETTE_LAYER);
        window.setVisible(true);

        /*
        EditorWindow window2 = new EditorWindow(new GraphEditor(new DirectedGraph()), "Factor Analysis", "Close", false, dataEditor);
        window2.setLocation(800, 400);
        DesktopController.getInstance().addEditorWindow(window2, JLayeredPane.PALETTE_LAYER);
        window2.setVisible(true);
        */
    }

    public JPanel createDialog(FactorAnalysis analysis)
    {
        double threshold = .2;

        TetradMatrix unrotatedSolution = analysis.successiveResidual();
        TetradMatrix rotatedSolution = FactorAnalysis.successiveFactorVarimax(unrotatedSolution);

        DataSet dataSet = (DataSet) dataEditor.getSelectedDataModel();
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        String output = "Unrotated Factor Loading Matrix:\n";

        output += tableString(unrotatedSolution, nf, Double.POSITIVE_INFINITY);
//        String temp = unrotatedSolution.toString();
//        temp = temp.split("\n", 2)[1];
//        output += temp;

        if(unrotatedSolution.columns() != 1)
        {
            output += "\n\nRotated Matrix (using sequential varimax):\n";

            output += tableString(rotatedSolution, nf, threshold);
    //        temp = rotatedSolution.toString();
    //        temp = temp.split("\n", 2)[1];
    //        output += temp;

        }

        JTextArea display = new JTextArea(output);
        JScrollPane scrollPane = new JScrollPane(display);
        scrollPane.setPreferredSize(new Dimension(500, 400));
        display.setEditable(false);
        display.setFont(new Font("Monospaced", Font.PLAIN, 12));
        //editorPanel.addPropertyChangeListener(new NormalityTestListener(display));


        SemGraph graph = new SemGraph();

        Vector<Node> observedVariables = new Vector<Node>();

        for(Node a : dataSet.getVariables())
        {
            graph.addNode(a);
            observedVariables.add(a);
        }

        Vector<Node> factors = new Vector<Node>();

        for(int i = 0; i < rotatedSolution.columns(); i++)
        {
            ContinuousVariable factor = new ContinuousVariable("Factor" + (i + 1));
            factor.setNodeType(NodeType.LATENT);
            graph.addNode(factor);
            factors.add(factor);
        }

        for(int i = 0; i < rotatedSolution.rows(); i++)
        {
            for(int j = 0; j < rotatedSolution.columns(); j++)
            {
                if(Math.abs(rotatedSolution.get(i, j)) > threshold)
                {
                    graph.addDirectedEdge(factors.get(j), observedVariables.get(i));
                    //HEY JOE -- rotatedSolution.get(i, j) is the edge coeficient
                }
            }
        }

        GraphUtils.circleLayout(graph, 225, 200, 150);
        GraphUtils.fruchtermanReingoldLayout(graph);
        
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

    //============================== Private methods ============================//

    private JFrame findOwner() {
        return (JFrame) SwingUtilities.getAncestorOfClass(
                JFrame.class, dataEditor);
    }

    public static void main(String[] args) {
        Graph graph = new Dag(GraphUtils.randomGraph(9, 0, 9, 30, 15, 15, false));
        SemPm pm = new SemPm(graph);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(500, false);
        ICovarianceMatrix cov = new CovarianceMatrix(data);

        FactorAnalysis factorAnalysis = new FactorAnalysis(cov);
        //factorAnalysis.centroidUnity();
        factorAnalysis.successiveResidual();
    }
}




