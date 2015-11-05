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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.session.DelegatesEditing;
import edu.cmu.tetradapp.model.IdentifiabilityWrapper;
import edu.cmu.tetradapp.model.UpdaterWrapper;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Lets the user calculate updated probabilities for a Bayes net.
 *
 * @author Joseph Ramsey
 */
public class BayesUpdaterEditorObs extends JPanel implements DelegatesEditing {
    private static int SINGLE_VALUE = 0;
    private static int MULTI_VALUE = 1;

    /**
     * The Bayes updater being edited.
     */
    private UpdaterWrapper updaterWrapper;

    /**
     * The workbench used to display the graph for this Updater.
     */
    private GraphWorkbench workbench;

    /**
     * Lets the user specify evidence for updating.
     */
    private EvidenceWizardSingleObs evidenceWizardSingle;

    /**
     * Lets the user specify evidence for updating.
     */
    private EvidenceWizardMultipleObs evidenceWizardMultiple;

    /**
     * Contains the other right-hand panels; included so that the right-hand
     * view panel (contained in this panel) can easily be reset.
     */
    private JPanel singleResultPanel;

    /**
     * Contains the text printout of the multi result.
     */
    private JPanel multiResultPanel;

    /**
     * Remember which tab the user selected last time around so as not to
     * irritate the user.
     */
    private int updatedBayesImWizardTab = 0;

    /**
     * A JPanel with a card layout that contains the various cards of the
     * wizard.
     */
    private JPanel cardPanel;

    /**
     * The getModel mode.
     */
    private int mode = SINGLE_VALUE;

    //===============================CONSTRUCTORS=========================//

    /**
     * Constructs a new instanted model editor from a Bayes Updater.
     */
    private BayesUpdaterEditorObs(UpdaterWrapper updaterWrapper) {
        if (updaterWrapper == null) {
            throw new NullPointerException(
                    "Updater Wrapper must not be null.");
        }

        this.updaterWrapper = updaterWrapper;
        setLayout(new BorderLayout());
        add(createSplitPane(getUpdaterWrapper()), BorderLayout.CENTER);
        setName("Bayes Updater Editor");

        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);
//        file.add(new SaveScreenshot(this, true, "Save Screenshot..."));
        file.add(new SaveComponentImage(workbench, "Save Graph Image..."));
        add(menuBar, BorderLayout.NORTH);

        workbench.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if (mode == MULTI_VALUE &&
                        "selectedNodes".equals(evt.getPropertyName())) {
                    setMode(MULTI_VALUE);
                }
            }
        });
    }

    /**
     * Constructs a new instanted model editor from a Bayes IM wrapper.
     */
	/*
    public BayesUpdaterEditor(RowSummingExactWrapper wrapper) {
        this((UpdaterWrapper) wrapper);
    }
	 */

    /**
     * Constructs a new instanted model editor from a Bayes IM wrapper.
     */
	/*
    public BayesUpdaterEditor(CptInvariantUpdaterWrapper wrapper) {
        this((UpdaterWrapper) wrapper);
    }
	 */

    /**
     * Constructs a new instanted model editor from a Bayes IM wrapper.
     */
	/*
    public BayesUpdaterEditor(ApproximateUpdaterWrapper wrapper) {
        this((UpdaterWrapper) wrapper);
    }
	 */

    /**
     * Constructs a new instanted model editor from a Bayes IM wrapper.
     */
    public BayesUpdaterEditorObs(IdentifiabilityWrapper wrapper) {
        this((UpdaterWrapper) wrapper);
    }
	
    //================================PUBLIC METHODS========================//

    /**
     * Sets the name of this editor.
     */
    public void setName(String name) {
        String oldName = getName();
        super.setName(name);
        this.firePropertyChange("name", oldName, getName());
    }

    private EvidenceWizardSingleObs getEvidenceWizardSingle() {
        return evidenceWizardSingle;
    }

    private EvidenceWizardMultipleObs getEvidenceWizardMultiple() {
        return evidenceWizardMultiple;
    }

    public JComponent getEditDelegate() {
        return evidenceWizardSingle;
    }

    private UpdaterWrapper getUpdaterWrapper() {
        return updaterWrapper;
    }

    private GraphWorkbench getWorkbench() {
        return workbench;
    }

    /**
     * Reacts to property change events.
     */
    public void propertyChange(PropertyChangeEvent e) {
        if ("editorClosing".equals(e.getPropertyName())) {
            this.firePropertyChange("editorClosing", null, getName());
        }
        else if ("closeFrame".equals(e.getPropertyName())) {
            this.firePropertyChange("closeFrame", null, null);
            this.firePropertyChange("editorClosing", true, true);
        }
        else if ("updatedBayesImWizardTab".equals(e.getPropertyName())) {
            this.updatedBayesImWizardTab = ((Integer) (e.getNewValue()));
        }
    }

    //================================PRIVATE METHODS=======================//

    private JSplitPane createSplitPane(UpdaterWrapper updaterWrapper) {
        JScrollPane workbenchScroll = createWorkbenchScroll(updaterWrapper);
        workbenchScroll.setBorder(new TitledBorder("Manipulated Graph"));
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                workbenchScroll, createRightPanel(updaterWrapper));
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(workbenchScroll.getPreferredSize().width);
        return splitPane;
    }

    private JScrollPane createWorkbenchScroll(
            UpdaterWrapper updaterWrapper) {
        workbench = new GraphWorkbench(updaterWrapper.getBayesUpdater().getManipulatedGraph());
        workbench.setAllowDoubleClickActions(false);
        JScrollPane workbenchScroll = new JScrollPane(getWorkbench());
        workbenchScroll.setPreferredSize(new Dimension(400, 400));
        return workbenchScroll;
    }

    private JPanel createRightPanel(UpdaterWrapper bayesUpdater) {
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BorderLayout());
        rightPanel.add(createMenuBar(), BorderLayout.NORTH);
        rightPanel.add(createWizardPanel(bayesUpdater), BorderLayout.CENTER);

        BayesIm bayesIm = bayesUpdater.getBayesUpdater().getBayesIm();
        boolean incomplete = false;

        for (int i = 0; i < bayesIm.getNumNodes(); i++) {
            if (bayesIm.isIncomplete(i)) {
                incomplete = true;
                break;
            }
        }

        if (incomplete) {
            JLabel label = new JLabel("NOTE: The Bayes IM is not completely specified.");
            label.setFont(new Font("Dialog", Font.BOLD, 12));
            rightPanel.add(label,
                    BorderLayout.SOUTH);
        }

        return rightPanel;
    }

    private JPanel createWizardPanel(UpdaterWrapper updaterWrapper) {
        cardPanel = new JPanel();
        cardPanel.setLayout(new CardLayout());
        evidenceWizardSingle =
                new EvidenceWizardSingleObs(updaterWrapper, getWorkbench());
        getEvidenceWizardSingle().addPropertyChangeListener(
                new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent e) {
                        if ("updateButtonPressed".equals(e.getPropertyName())) {
                            resetSingleResultPanel();
                            show("viewSingleResult");
                        }
                    }
                });
        cardPanel.add(new JScrollPane(getEvidenceWizardSingle()),
                "editEvidenceSingle");

        evidenceWizardMultiple =
                new EvidenceWizardMultipleObs(updaterWrapper, getWorkbench());
        getEvidenceWizardMultiple().addPropertyChangeListener(
                new PropertyChangeListener() {
                    public void propertyChange(PropertyChangeEvent e) {
                        if ("updateButtonPressed".equals(e.getPropertyName())) {
                            resetMultipleResultPanel();
                            show("viewMultiResult");
                        }
                    }
                });
        cardPanel.add(new JScrollPane(getEvidenceWizardMultiple()),
                "editEvidenceMultiple");

        singleResultPanel = new JPanel();
        singleResultPanel.setLayout(new BorderLayout());
        resetSingleResultPanel();

        multiResultPanel = new JPanel();
        multiResultPanel.setLayout(new BorderLayout());
        resetMultipleResultPanel();

        cardPanel.add(new JScrollPane(singleResultPanel), "viewSingleResult");
        cardPanel.add(new JScrollPane(multiResultPanel), "viewMultiResult");

        return cardPanel;
    }

    private void show(String s) {
        CardLayout card = (CardLayout) cardPanel.getLayout();
        card.show(cardPanel, s);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu evidenceMenu = new JMenu("Evidence");
        menuBar.add(evidenceMenu);
        JMenuItem editEvidence = new JMenuItem("Edit Evidence");
        editEvidence.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_E, ActionEvent.ALT_MASK));
        evidenceMenu.add(editEvidence);

        JMenu modeMenu = new JMenu("Mode");
        menuBar.add(modeMenu);
        JCheckBoxMenuItem singleVariable =
                new JCheckBoxMenuItem("In-Depth Information (Single Variable)");
        JCheckBoxMenuItem multiVariable =
                new JCheckBoxMenuItem("Marginals Only (Multiple Variables)");

        ButtonGroup group = new ButtonGroup();
        group.add(singleVariable);
        group.add(multiVariable);

        if (mode == SINGLE_VALUE) {
            singleVariable.setSelected(true);
        }
        else if (mode == MULTI_VALUE) {
            multiVariable.setSelected(true);
        }

        modeMenu.add(singleVariable);
        modeMenu.add(multiVariable);

        editEvidence.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setMode(mode);
            }
        });

        singleVariable.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setMode(SINGLE_VALUE);
            }
        });

        multiVariable.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setMode(MULTI_VALUE);
            }
        });

        return menuBar;
    }

    private void setMode(int mode) {
        this.mode = mode;

        if (mode == SINGLE_VALUE) {
            show("editEvidenceSingle");
        }
        else if (mode == MULTI_VALUE) {
            show("editEvidenceMultiple");
        }
        else {
            throw new IllegalStateException();
        }
    }

    private void resetSingleResultPanel() {
        Window owner = (Window) getTopLevelAncestor();

        if (owner == null) {
            resetSingleResultPanelSub();
        }
        else {
            new WatchedProcess(owner) {
                public void watch() {
                    resetSingleResultPanelSub();
                }
            };
        }
    }

    private void resetSingleResultPanelSub() {
        UpdatedBayesImWizardObs wizard = new UpdatedBayesImWizardObs(
                getUpdaterWrapper(), getWorkbench(), this.updatedBayesImWizardTab,
                getSelectedNode());
        wizard.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent e) {
                if ("updatedBayesImWizardTab".equals(e.getPropertyName())) {
                    updatedBayesImWizardTab = ((Integer) (e.getNewValue()));
                }
            }
        });
        singleResultPanel.removeAll();
        singleResultPanel.add(wizard, BorderLayout.CENTER);
        singleResultPanel.revalidate();
        singleResultPanel.repaint();
    }

    private void resetMultipleResultPanel() {
        JTextArea textArea = getEvidenceWizardMultiple().getTextArea();
        multiResultPanel.removeAll();
        multiResultPanel.add(textArea, BorderLayout.CENTER);
        multiResultPanel.revalidate();
        multiResultPanel.repaint();
    }

    private Node getSelectedNode() {
        UpdatedBayesImWizardObs wizard = null;
        Node selectedNode = null;

        for (int i = 0; i < singleResultPanel.getComponentCount(); i++) {
            Component component = singleResultPanel.getComponent(i);
            if (component instanceof UpdatedBayesImWizardObs) {
                wizard = (UpdatedBayesImWizardObs) component;
            }
        }

        if (wizard != null) {
            selectedNode = wizard.getSelectedNode();
        }

        return selectedNode;
    }
}





