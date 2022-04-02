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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.IndTestType;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.model.MarkovBlanketSearchRunner;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Editor + param editor for markov blanket searches.
 *
 * @author Tyler Gibson
 */
public class MarkovBlanketSearchEditor extends JPanel implements GraphEditable, IndTestTypeSetter {

    /**
     * The algorithm wrapper being viewed.
     */
    private final MarkovBlanketSearchRunner algorithmRunner;


    /**
     * The button one clicks to executeButton the algorithm.
     */
    private final JButton executeButton = new JButton();


    /**
     * The scrollpange for the result workbench.
     */
    private JScrollPane workbenchScroll;


    /**
     * Table used to display data.
     */
    private final TabularDataJTable table;

    /**
     * True if the warning message that previously defined knowledge is being
     * used has already been shown and doesn't need to be shown again.
     */
    private boolean knowledgeMessageShown;


    /**
     * Constructs the eidtor.
     */
    public MarkovBlanketSearchEditor(MarkovBlanketSearchRunner algorithmRunner) {
        if (algorithmRunner == null) {
            throw new NullPointerException();
        }
        this.algorithmRunner = algorithmRunner;
        Parameters params = algorithmRunner.getParams();
        List<String> vars = algorithmRunner.getSource().getVariableNames();
        if (params.getString("targetName", null) == null && !vars.isEmpty()) {
            params.set("targetName", vars.get(0));
        }
        DataSet data;
        if (algorithmRunner.getDataModelForMarkovBlanket() == null) {
            data = algorithmRunner.getSource();
        } else {
            data = algorithmRunner.getDataModelForMarkovBlanket();
        }
        this.table = new TabularDataJTable(data);
        this.table.setEditable(false);
        this.table.setTableHeader(null);

        setup();
    }


    /**
     * @return the data model being viewed.
     */
    public DataModel getDataModel() {
        if (this.algorithmRunner.getDataModelForMarkovBlanket() != null) {
            return this.algorithmRunner.getDataModelForMarkovBlanket();
        }

        return this.algorithmRunner.getSource();
    }

    public Object getSourceGraph() {
        return getParams().get("sourceGraph", null);
    }

    //===========================PRIVATE METHODS==========================//


    /**
     * Executes the algorithm. The execution takes place inside a thread, so one
     * cannot count on a result graph having been found when the method
     */
    private void execute() {
        Window owner = (Window) getTopLevelAncestor();

        WatchedProcess process = new WatchedProcess(owner) {
            public void watch() {
                getExecuteButton().setEnabled(false);
                setErrorMessage(null);

                if (!MarkovBlanketSearchEditor.this.knowledgeMessageShown) {
                    IKnowledge knowledge = (IKnowledge) getAlgorithmRunner().getParams().get("knowledge", new Knowledge2());
                    if (!knowledge.isEmpty()) {
                        JOptionPane.showMessageDialog(
                                JOptionUtils.centeringComp(),
                                "Using previously set knowledge. (To edit, use " +
                                        "the Knowledge menu.)");
                        MarkovBlanketSearchEditor.this.knowledgeMessageShown = true;
                    }
                }

                try {
                    getAlgorithmRunner().execute();
                } catch (Exception e) {
                    CharArrayWriter writer1 = new CharArrayWriter();
                    PrintWriter writer2 = new PrintWriter(writer1);
                    e.printStackTrace(writer2);
                    String message = writer1.toString();
                    writer2.close();

                    e.printStackTrace(System.out);

                    TetradLogger.getInstance().error(message);

                    String messageString = e.getMessage();

                    if (e.getCause() != null) {
                        messageString = e.getCause().getMessage();
                    }

                    if (messageString == null) {
                        messageString = message;
                    }
                    setErrorMessage(messageString);

                    getExecuteButton().setEnabled(true);
                    throw new RuntimeException(e);
                }


                setLabel();
                DataSet modelForMarkovBlanket = MarkovBlanketSearchEditor.this.algorithmRunner.getDataModelForMarkovBlanket();
                if (modelForMarkovBlanket != null) {
                    MarkovBlanketSearchEditor.this.table.setDataSet(modelForMarkovBlanket);
                }
                MarkovBlanketSearchEditor.this.table.repaint();
                getExecuteButton().setEnabled(true);
            }
        };


        Thread watcher = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(300);

                    if (!process.isAlive()) {
                        getExecuteButton().setEnabled(true);
                        return;
                    }
                } catch (InterruptedException e) {
                    getExecuteButton().setEnabled(true);
                    return;
                }
            }
        });

        watcher.start();
    }

    private void setLabel() {
        getWorkbenchScroll().setBorder(new TitledBorder(this.algorithmRunner.getSearchName()));
    }


    private JButton getExecuteButton() {
        return this.executeButton;
    }

    private MarkovBlanketSearchRunner getAlgorithmRunner() {
        return this.algorithmRunner;
    }


    /**
     * Sets up the editor, does the layout, and so on.
     */
    private void setup() {
        setLayout(new BorderLayout());
        add(createToolbar(), BorderLayout.WEST);
        add(workbenchScroll(), BorderLayout.CENTER);
        add(menuBar(), BorderLayout.NORTH);
    }


    /**
     * Creates param editor and tool bar.
     */
    private JPanel createToolbar() {
        JPanel toolbar = new JPanel();
        getExecuteButton().setText("Execute*");
        getExecuteButton().addActionListener(e -> execute());

        Box b1 = Box.createVerticalBox();
        b1.add(getParamEditor());
        b1.add(Box.createVerticalStrut(10));
        Box b2 = Box.createHorizontalBox();
        b2.add(Box.createGlue());
        b2.add(getExecuteButton());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(10));

        Box b3 = Box.createHorizontalBox();
        JLabel label = new JLabel("<html>" + "*Please note that some" +
                "<br>searches may take a" + "<br>long time to complete." +
                "</html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        label.setBorder(new TitledBorder(""));
        b3.add(label);
        b1.add(b3);

        toolbar.add(b1);
        return toolbar;
    }

    /**
     * Creates the param editor.
     */
    private JComponent getParamEditor() {
        Box box = Box.createVerticalBox();
        JComboBox comboBox = new JComboBox(this.algorithmRunner.getSource().getVariableNames().toArray());
        comboBox.addItemListener(e -> {
            String s = (String) e.getItem();
            if (s != null) {
                MarkovBlanketSearchEditor.this.algorithmRunner.getParams().set("targetName", s);
            }
        });
        DoubleTextField alphaField = new DoubleTextField(getParams().getDouble("alpha", 0.001), 4,
                NumberFormatUtil.getInstance().getNumberFormat());
        alphaField.setFilter((value, oldValue) -> {
            try {
                getParams().set("alpha", 0.001);
                Preferences.userRoot().putDouble("alpha",
                        getParams().getDouble("alpha", 0.001));
                return value;
            } catch (Exception e) {
                return oldValue;
            }
        });

        box.add(comboBox);
        box.add(Box.createVerticalStrut(4));
        box.add(createLabeledComponent(alphaField));


        box.setBorder(new TitledBorder("Parameters"));
        return box;
    }


    private Box createLabeledComponent(JComponent comp) {
        Box box = Box.createHorizontalBox();
        box.add(new JLabel("Alpha"));
        box.add(Box.createHorizontalStrut(5));
        box.add(comp);
        box.add(Box.createHorizontalGlue());

        return box;
    }


    private Parameters getParams() {
        return this.algorithmRunner.getParams();
    }


    /**
     * Creates the workbench
     */
    private JScrollPane workbenchScroll() {
        this.workbenchScroll = new JScrollPane(this.table);
        this.workbenchScroll.setPreferredSize(new Dimension(500, 500));
        this.setLabel();

        return this.workbenchScroll;
    }


    /**
     * Creates the menubar for the search editor.
     */
    private JMenuBar menuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(new JMenuItem(new SaveDataAction(this)));
        file.add(new GraphFileMenu(this, getWorkbench()));
//        file.add(new SaveGraph(this, "Save Graph..."));

        JMenu edit = new JMenu("Edit");
        JMenuItem copyCells = new JMenuItem("Copy Cells");
        copyCells.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        copyCells.addActionListener(e -> {
            Action copyAction = TransferHandler.getCopyAction();
            ActionEvent actionEvent = new ActionEvent(MarkovBlanketSearchEditor.this.table,
                    ActionEvent.ACTION_PERFORMED, "copy");
            copyAction.actionPerformed(actionEvent);
        });
        edit.add(copyCells);


        menuBar.add(file);
        menuBar.add(edit);

        JMenu independence = new JMenu("Independence");
        if (this.algorithmRunner.getSource().isContinuous()) {
            IndTestMenuItems.addContinuousTestMenuItems(independence, this);
            menuBar.add(independence);
        } else if (this.algorithmRunner.getSource().isDiscrete()) {
            IndTestMenuItems.addDiscreteTestMenuItems(independence, this);
            menuBar.add(independence);
        }


        menuBar.add(independence);

        return menuBar;
    }


    private JScrollPane getWorkbenchScroll() {
        return this.workbenchScroll;
    }

    public List getSelectedModelComponents() {
        throw new UnsupportedOperationException("Cannot return selected components.");
    }

    public void pasteSubsession(List<Object> sessionElements, Point upperLeft) {
        throw new UnsupportedOperationException("Cannot paste subsessions on a search editor.");
    }

    public GraphWorkbench getWorkbench() {
        return null;
    }

    /**
     * Not supported.
     */
    public void setGraph(Graph g) {
        throw new UnsupportedOperationException("Cannot set the graph on a search editor.");
    }


    /**
     * @return the graph.
     */
    public Graph getGraph() {
        if (getWorkbench().getGraph() != null) {
            return getWorkbench().getGraph();
        }
        return new EdgeListGraph();
    }

    public void setTestType(IndTestType testType) {
        getParams().set("indTestType", testType);
    }

    public IndTestType getTestType() {
        return (IndTestType) getParams().get("indTestType", IndTestType.FISHER_Z);
    }
}




