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

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.JsonUtils;
import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.editor.search.AlgorithmCard;
import edu.cmu.tetradapp.editor.search.GraphCard;
import edu.cmu.tetradapp.editor.search.ParameterCard;
import edu.cmu.tetradapp.model.GeneralAlgorithmRunner;
import edu.cmu.tetradapp.ui.PaddingPanel;
import edu.cmu.tetradapp.ui.model.AlgorithmModel;
import edu.cmu.tetradapp.util.DesktopController;
import edu.cmu.tetradapp.util.FinalizingEditor;
import edu.cmu.tetradapp.util.WatchedProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

/**
 * Edits some algorithm to search for Markov blanket CPDAGs.
 *
 * @author Joseph Ramsey
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 * @author Zhou Yuan (zhy19@pitt.edu)
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class GeneralAlgorithmEditor extends JPanel implements PropertyChangeListener, ActionListener, FinalizingEditor {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeneralAlgorithmEditor.class);

    private static final long serialVersionUID = -5719467682865706447L;

    private final JButton algoFwdBtn = new JButton("Set Parameters   >");
    private final JButton paramBkBtn = new JButton("<   Choose Algorithm");
    private final JButton paramFwdBtn = new JButton("Run Search & Generate Graph   >");
    private final JButton graphBkBtn = new JButton("<   Set Parameters");

    private final AlgorithmCard algorithmCard;
    private final ParameterCard parameterCard;
    private final GraphCard graphCard;

    private String jsonResult;

    private final GeneralAlgorithmRunner algorithmRunner;
    private final TetradDesktop desktop;

    public GeneralAlgorithmEditor(GeneralAlgorithmRunner algorithmRunner) {
        this.algorithmRunner = algorithmRunner;
        this.desktop = (TetradDesktop) DesktopController.getInstance();
        this.algorithmCard = new AlgorithmCard(algorithmRunner);
        this.parameterCard = new ParameterCard(algorithmRunner);
        this.graphCard = new GraphCard(algorithmRunner);

        initComponents();
        initListeners();

        // repopulate all the previous selections if reopen the search box
        if (algorithmRunner.getGraphs() != null && algorithmRunner.getGraphs().size() > 0) {
            this.algorithmCard.refresh();
            this.parameterCard.refresh();
            this.graphCard.refresh();

            showGraphCard();
        }
    }

    private void initComponents() {
        if (this.algorithmRunner.hasMissingValues()) {
            setPreferredSize(new Dimension(827, 670));
        } else {
            setPreferredSize(new Dimension(827, 620));
        }

        setLayout(new CardLayout());
        add(new SingleButtonCard(this.algorithmCard, this.algoFwdBtn));
        add(new DualButtonCard(this.parameterCard, this.paramBkBtn, this.paramFwdBtn));
        add(new SingleButtonCard(this.graphCard, this.graphBkBtn));
    }

    private void initListeners() {
        this.algoFwdBtn.addActionListener(this);
        this.paramBkBtn.addActionListener(this);
        this.paramFwdBtn.addActionListener(this);
        this.graphBkBtn.addActionListener(this);

        this.algorithmCard.addPropertyChangeListener(this);
        this.parameterCard.addPropertyChangeListener(this);
        this.graphCard.addPropertyChangeListener(this);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("algoFwdBtn".equals(evt.getPropertyName())) {
            this.algoFwdBtn.setEnabled((boolean) evt.getNewValue());
        }
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        Object obj = evt.getSource();
        if (obj == this.algoFwdBtn) {
            if (this.algorithmCard.isAllValid()) {
                this.parameterCard.refresh();
                showNextCard();
            }
        } else if (obj == this.paramBkBtn) {
            showPreviousCard();
        } else if (obj == this.paramFwdBtn) {
            doSearch();
        } else if (obj == this.graphBkBtn) {
            showPreviousCard();
        }
    }

    public void setAlgorithmResult(String jsonResult) {
        this.jsonResult = jsonResult;
        System.out.println("json result: " + jsonResult);

        Graph graph = JsonUtils.parseJSONObjectToTetradGraph(jsonResult);
        this.algorithmRunner.getGraphs().clear();
        this.algorithmRunner.getGraphs().add(graph);

        GeneralAlgorithmEditor.LOGGER.info("Remote graph result assigned to algorithmRunner!");
        firePropertyChange("modelChanged", null, null);

        this.graphCard.refresh();
        showGraphCard();
    }

    public void setAlgorithmErrorResult(String errorResult) {
        JOptionPane.showMessageDialog(this.desktop, this.jsonResult);

        throw new IllegalArgumentException(errorResult);
    }

    private void showNextCard() {
        CardLayout cardLayout = (CardLayout) getLayout();
        cardLayout.next(this);
    }

    private void showPreviousCard() {
        CardLayout cardLayout = (CardLayout) getLayout();
        cardLayout.previous(this);
    }

    private void showGraphCard() {
        CardLayout cardLayout = (CardLayout) getLayout();
        cardLayout.last(this);
    }

    private void doSearch() {
        new WatchedProcess((Window) getTopLevelAncestor()) {
            @Override
            public void watch() {
                AlgorithmModel algoModel = GeneralAlgorithmEditor.this.algorithmCard.getSelectedAlgorithm();
                if (algoModel != null) {
                    try {
                        GeneralAlgorithmEditor.this.algorithmCard.saveStates();
                        GeneralAlgorithmEditor.this.algorithmRunner.execute();

                        firePropertyChange("modelChanged", null, null);
                        GeneralAlgorithmEditor.this.graphCard.refresh();
                        showGraphCard();
                    } catch (Exception exception) {
                        exception.printStackTrace(System.err);

                        JOptionPane.showMessageDialog(
                                getTopLevelAncestor(),
                                "Stopped with error:\n"
                                        + exception.getMessage());
                    }

                }
            }
        };

    }

    @Override
    public boolean finalizeEditor() {
        List<Graph> graphs = this.algorithmRunner.getGraphs();
        if (graphs == null || graphs.isEmpty()) {
            int option = JOptionPane.showConfirmDialog(this, "You have not performed a search. Close anyway?", "Close?",
                    JOptionPane.YES_NO_OPTION);
            return option == JOptionPane.YES_OPTION;
        }

        return true;
    }

    private static class SingleButtonCard extends JPanel {

        private static final long serialVersionUID = 7154917933096522203L;

        private final JComponent component;
        private final JButton button;

        public SingleButtonCard(JComponent component, JButton button) {
            this.component = component;
            this.button = button;

            initComponents();
        }

        private void initComponents() {
            Dimension buttonSize = new Dimension(268, 25);
            this.button.setMinimumSize(buttonSize);
            this.button.setMaximumSize(buttonSize);
            this.button.setPreferredSize(buttonSize);

            setLayout(new BorderLayout());
            add(new JScrollPane(new PaddingPanel(this.component)), BorderLayout.CENTER);
            add(new SouthPanel(), BorderLayout.SOUTH);
        }

        private class SouthPanel extends JPanel {

            private static final long serialVersionUID = -126249189388443046L;

            public SouthPanel() {
                initComponents();
            }

            private void initComponents() {
                GroupLayout layout = new GroupLayout(this);
                this.setLayout(layout);
                layout.setHorizontalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(button)
                                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                );
                layout.setVerticalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addContainerGap()
                                        .addComponent(button)
                                        .addContainerGap())
                );
            }
        }

    }

    private static class DualButtonCard extends JPanel {

        private static final long serialVersionUID = 7995297102462362969L;

        private final JComponent component;
        private final JButton backButton;
        private final JButton forwardButton;

        public DualButtonCard(JComponent component, JButton backButton, JButton forwardButton) {
            this.component = component;
            this.backButton = backButton;
            this.forwardButton = forwardButton;

            this.initComponents();
        }

        private void initComponents() {
            this.setLayout(new BorderLayout());

            Dimension buttonSize = new Dimension(268, 25);

            backButton.setMinimumSize(buttonSize);
            backButton.setMaximumSize(buttonSize);
            backButton.setPreferredSize(buttonSize);

            forwardButton.setMinimumSize(buttonSize);
            forwardButton.setMaximumSize(buttonSize);
            forwardButton.setPreferredSize(buttonSize);

            this.add(new JScrollPane(new PaddingPanel(component)), BorderLayout.CENTER);
            this.add(new SouthPanel(), BorderLayout.SOUTH);
        }

        private class SouthPanel extends JPanel {

            private static final long serialVersionUID = 3980233325015220843L;

            public SouthPanel() {
                this.initComponents();
            }

            private void initComponents() {
                GroupLayout layout = new GroupLayout(this);
                setLayout(layout);
                layout.setHorizontalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(backButton)
                                        .addGap(18, 18, 18)
                                        .addComponent(forwardButton)
                                        .addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                );

                layout.linkSize(SwingConstants.HORIZONTAL, backButton, forwardButton);

                layout.setVerticalGroup(
                        layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                .addGroup(layout.createSequentialGroup()
                                        .addContainerGap()
                                        .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                                .addComponent(DualButtonCard.this.backButton)
                                                .addComponent(DualButtonCard.this.forwardButton))
                                        .addContainerGap())
                );
            }
        }

    }

}
