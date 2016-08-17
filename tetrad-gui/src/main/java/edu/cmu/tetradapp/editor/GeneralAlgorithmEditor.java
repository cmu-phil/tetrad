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

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.util.WatchedProcess;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * Edits some algorithm to search for Markov blanket patterns.
 *
 * @author Joseph Ramsey
 */
public class GeneralAlgorithmEditor extends JPanel {
    private final JButton searchButton = new JButton("Search");
    private final JTabbedPane pane;
    private JComboBox<String> testDropdown = new JComboBox<>();
    private JComboBox<String> scoreDropdown = new JComboBox<>();
    private JComboBox<String> algTypesDropdown = new JComboBox<>();
    private JComboBox<String> algorithmsDropdown = new JComboBox<>();
    private final SimulationGraphEditor graphEditor;
    private Algorithm algorithm;
    private Parameters parameters;

    private String[] test = {"Fisher Z", "Chi Square", "G Square"};
    private String[] score = {"SEM BIC", "BDeu"};
    private String[] algTypes = {"Pattern", "PAG"};
    private String[][] algorithms = {
            {"PC", "CPC", "FGS"},
            {"FCI", "RFCI", "GFCI"}
    };

    //=========================CONSTRUCTORS============================//

    /**
     * Opens up an editor to let the user view the given PcRunner.
     */
    public GeneralAlgorithmEditor(final GeneralAlgorithmRunner runner) {
        this.algorithm = runner.getAlgorithm();
        this.parameters = runner.getParameters();
        graphEditor = new SimulationGraphEditor(new ArrayList<Graph>(), JTabbedPane.LEFT);
        graphEditor.replace(runner.getGraphList());
        setLayout(new BorderLayout());

        // Initialize all of the dropdowns.
        for (String item : test) {
            testDropdown.addItem(item);
        }

        for (String item : score) {
            scoreDropdown.addItem(item);
        }

        for (String item : algTypes) {
            algTypesDropdown.addItem(item);
        }

        for (String item : algorithms[0]) {
            algorithmsDropdown.addItem(item);
        }

        pane = new JTabbedPane();
        pane.add("Algorithm", getParametersPane());
        pane.add("Output Graphs", graphEditor);

        add(pane, BorderLayout.CENTER);

        searchButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                new WatchedProcess((Window) getTopLevelAncestor()) {
                    @Override
                    public void watch() {
                        DataModelList dataList = runner.getDataModelList();

                        if (dataList != null) {
                            List<Graph> graphList = new ArrayList<>();

                            for (DataModel data : dataList) {
                                DataSet dataSet = (DataSet) data;
                                Graph graph = algorithm.search(dataSet, parameters);
                                GraphUtils.circleLayout(graph, 225, 200, 150);
                                graphList.add(graph);
                            }

                            runner.setGraphList(graphList);
                            graphEditor.replace(graphList);
                            graphEditor.validate();
                            pane.setSelectedIndex(1);
                        }
                    }
                };
            }
        });
    }

    //=============================== Public Methods ==================================//

    private Box getParametersPane() {
        ParameterPanel comp = new ParameterPanel(getAlgorithm().getParameters(), getParameters());
        JScrollPane scroll = new JScrollPane(comp);
        scroll.setPreferredSize(graphEditor.getPreferredSize());
        Box c = Box.createVerticalBox();

        Box d0 = Box.createHorizontalBox();
        JLabel label0 = new JLabel("Pick an agorithm and parameterize it; then click Search.");
        label0.setFont(new Font("Dialog", Font.BOLD, 12));
        d0.add(label0);
        d0.add(Box.createHorizontalGlue());
        c.add(d0);
        c.add(Box.createVerticalStrut(5));

        Box d3 = Box.createHorizontalBox();
        JLabel label3 = new JLabel("TYPE OF ALGORITHM:");
        label3.setFont(new Font("Dialog", Font.BOLD, 12));
        d3.add(label3);
        d3.add(algTypesDropdown);
        c.add(d3);

        Box d4 = Box.createHorizontalBox();
        JLabel label4 = new JLabel("WHICH ALGORITHM:");
        label4.setFont(new Font("Dialog", Font.BOLD, 12));
        d4.add(label4);
        d4.add(algorithmsDropdown);
        c.add(d4);

        Box d1 = Box.createHorizontalBox();
        JLabel label1 = new JLabel("TEST TYPE (if needed):");
        label1.setFont(new Font("Dialog", Font.BOLD, 12));
        d1.add(label1);
        d1.add(scoreDropdown);
        c.add(d1);

        Box d2 = Box.createHorizontalBox();
        JLabel label2 = new JLabel("SCORE TYPE (if needed):");
        label2.setFont(new Font("Dialog", Font.BOLD, 12));
        d2.add(label2);
        d2.add(testDropdown);
        c.add(d2);
        c.add(Box.createVerticalStrut(5));

        Box d5 = Box.createHorizontalBox();
        JLabel label5 = new JLabel("You wisely chose: " + algorithm.getDescription());
        label5.setFont(new Font("Dialog", Font.BOLD, 12));
        d5.add(label5);
        d5.add(Box.createHorizontalGlue());
        d5.add(new JButton("Explain This"));
        c.add(d5);
        c.add(Box.createVerticalStrut(10));

        c.add(scroll);
        c.add(searchButton);

        Box b = Box.createHorizontalBox();
        b.add(c);
        b.add(Box.createHorizontalGlue());
        return b;
    }

    public Algorithm getAlgorithm() {
        return algorithm;
    }

    public Parameters getParameters() {
        return parameters;
    }
}





