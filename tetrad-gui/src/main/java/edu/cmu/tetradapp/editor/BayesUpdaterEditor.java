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
import edu.cmu.tetradapp.model.*;
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
public class BayesUpdaterEditor extends JPanel implements DelegatesEditing {

    private static final int SINGLE_VALUE = 0;
    private static final int MULTI_VALUE = 1;

    /**
     * The Bayes updater being edited.
     */
    private final UpdaterWrapper updaterWrapper;

    /**
     * The workbench used to display the graph for this Updater.
     */
    private GraphWorkbench workbench;

    /**
     * Lets the user specify evidence for updating.
     */
    private EvidenceWizardSingle evidenceWizardSingle;

    /**
     * Lets the user specify evidence for updating.
     */
    private EvidenceWizardMultiple evidenceWizardMultiple;

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
    private int mode = BayesUpdaterEditor.SINGLE_VALUE;

    //===============================CONSTRUCTORS=========================//

    /**
     * Constructs a new instanted model editor from a Bayes Updater.
     */
    private BayesUpdaterEditor(final UpdaterWrapper updaterWrapper) {
        if (updaterWrapper == null) {
            throw new NullPointerException(
                    "Updater Wrapper must not be null.");
        }

        this.updaterWrapper = updaterWrapper;
        setLayout(new BorderLayout());
        add(createSplitPane(getUpdaterWrapper()), BorderLayout.CENTER);
        setName("Bayes Updater Editor");

        final JMenuBar menuBar = new JMenuBar();
        final JMenu file = new JMenu("File");
        menuBar.add(file);
//        file.add(new SaveScreenshot(this, true, "Save Screenshot..."));
        file.add(new SaveComponentImage(this.workbench, "Save Graph Image..."));
        add(menuBar, BorderLayout.NORTH);

        this.workbench.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(final PropertyChangeEvent evt) {
                if (BayesUpdaterEditor.this.mode == BayesUpdaterEditor.MULTI_VALUE
                        && "selectedNodes".equals(evt.getPropertyName())) {
                    setMode(BayesUpdaterEditor.MULTI_VALUE);
                }
            }
        });
    }

    /**
     * Constructs a new instanted model editor from a Bayes IM wrapper.
     */
    public BayesUpdaterEditor(final RowSummingExactWrapper wrapper) {
        this((UpdaterWrapper) wrapper);
    }

    /**
     * Constructs a new instanted model editor from a Bayes IM wrapper.
     */
    public BayesUpdaterEditor(final CptInvariantUpdaterWrapper wrapper) {
        this((UpdaterWrapper) wrapper);
    }

    /**
     * Constructs a new instanted model editor from a Bayes IM wrapper.
     */
    public BayesUpdaterEditor(final ApproximateUpdaterWrapper wrapper) {
        this((UpdaterWrapper) wrapper);
    }

    public BayesUpdaterEditor(final JunctionTreeWrapper wrapper) {
        this((UpdaterWrapper) wrapper);
    }

    //================================PUBLIC METHODS========================//

    /**
     * Sets the name of this editor.
     */
    public void setName(final String name) {
        final String oldName = getName();
        super.setName(name);
        this.firePropertyChange("name", oldName, getName());
    }

    private EvidenceWizardSingle getEvidenceWizardSingle() {
        return this.evidenceWizardSingle;
    }

    private EvidenceWizardMultiple getEvidenceWizardMultiple() {
        return this.evidenceWizardMultiple;
    }

    public JComponent getEditDelegate() {
        return this.evidenceWizardSingle;
    }

    private UpdaterWrapper getUpdaterWrapper() {
        return this.updaterWrapper;
    }

    private GraphWorkbench getWorkbench() {
        return this.workbench;
    }

    /**
     * Reacts to property change events.
     */
    public void propertyChange(final PropertyChangeEvent e) {
        if ("editorClosing".equals(e.getPropertyName())) {
            this.firePropertyChange("editorClosing", null, getName());
        } else if ("closeFrame".equals(e.getPropertyName())) {
            this.firePropertyChange("closeFrame", null, null);
            this.firePropertyChange("editorClosing", true, true);
        } else if ("updatedBayesImWizardTab".equals(e.getPropertyName())) {
            this.updatedBayesImWizardTab = ((Integer) (e.getNewValue()));
        }
    }

    //================================PRIVATE METHODS=======================//
    private JSplitPane createSplitPane(final UpdaterWrapper updaterWrapper) {
        final JScrollPane workbenchScroll = createWorkbenchScroll(updaterWrapper);
        workbenchScroll.setBorder(new TitledBorder("Manipulated Graph"));
        final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                workbenchScroll, createRightPanel(updaterWrapper));
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(workbenchScroll.getPreferredSize().width);
        return splitPane;
    }

    private JScrollPane createWorkbenchScroll(
            final UpdaterWrapper updaterWrapper) {
        this.workbench = new GraphWorkbench(updaterWrapper.getBayesUpdater().getManipulatedGraph());
        this.workbench.setAllowDoubleClickActions(false);
        final JScrollPane workbenchScroll = new JScrollPane(getWorkbench());
        workbenchScroll.setPreferredSize(new Dimension(400, 400));
        return workbenchScroll;
    }

    private JPanel createRightPanel(final UpdaterWrapper bayesUpdater) {
        final JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BorderLayout());
        rightPanel.add(createMenuBar(), BorderLayout.NORTH);
        rightPanel.add(createWizardPanel(bayesUpdater), BorderLayout.CENTER);

        final BayesIm bayesIm = bayesUpdater.getBayesUpdater().getBayesIm();
        boolean incomplete = false;

        for (int i = 0; i < bayesIm.getNumNodes(); i++) {
            if (bayesIm.isIncomplete(i)) {
                incomplete = true;
                break;
            }
        }

        if (incomplete) {
            final JLabel label = new JLabel("NOTE: The Bayes IM is not completely specified.");
            label.setFont(new Font("Dialog", Font.BOLD, 12));
            rightPanel.add(label,
                    BorderLayout.SOUTH);
        }

        return rightPanel;
    }

    private JPanel createWizardPanel(final UpdaterWrapper updaterWrapper) {
        this.cardPanel = new JPanel();
        this.cardPanel.setLayout(new CardLayout());
        this.evidenceWizardSingle
                = new EvidenceWizardSingle(updaterWrapper, getWorkbench());
        getEvidenceWizardSingle().addPropertyChangeListener(
                new PropertyChangeListener() {
                    public void propertyChange(final PropertyChangeEvent e) {
                        if ("updateButtonPressed".equals(e.getPropertyName())) {
                            resetSingleResultPanel();
                            show("viewSingleResult");
                        }
                    }
                });
        this.cardPanel.add(new JScrollPane(getEvidenceWizardSingle()),
                "editEvidenceSingle");

        this.evidenceWizardMultiple
                = new EvidenceWizardMultiple(updaterWrapper, getWorkbench());
        getEvidenceWizardMultiple().addPropertyChangeListener(
                new PropertyChangeListener() {
                    public void propertyChange(final PropertyChangeEvent e) {
                        if ("updateButtonPressed".equals(e.getPropertyName())) {
                            resetMultipleResultPanel();
                            show("viewMultiResult");
                        }
                    }
                });
        this.cardPanel.add(new JScrollPane(getEvidenceWizardMultiple()),
                "editEvidenceMultiple");

        this.singleResultPanel = new JPanel();
        this.singleResultPanel.setLayout(new BorderLayout());
        resetSingleResultPanel();

        this.multiResultPanel = new JPanel();
        this.multiResultPanel.setLayout(new BorderLayout());
        resetMultipleResultPanel();

        this.cardPanel.add(new JScrollPane(this.singleResultPanel), "viewSingleResult");
        this.cardPanel.add(new JScrollPane(this.multiResultPanel), "viewMultiResult");

        return this.cardPanel;
    }

    private void show(final String s) {
        final CardLayout card = (CardLayout) this.cardPanel.getLayout();
        card.show(this.cardPanel, s);
    }

    private JMenuBar createMenuBar() {
        final JMenuBar menuBar = new JMenuBar();
        final JMenu evidenceMenu = new JMenu("Evidence");
        menuBar.add(evidenceMenu);
        final JMenuItem editEvidence = new JMenuItem("Edit Evidence");
        editEvidence.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_E, ActionEvent.ALT_MASK));
        evidenceMenu.add(editEvidence);

        final JMenu modeMenu = new JMenu("Mode");
        menuBar.add(modeMenu);
        final JCheckBoxMenuItem singleVariable
                = new JCheckBoxMenuItem("In-Depth Information (Single Variable)");
        final JCheckBoxMenuItem multiVariable
                = new JCheckBoxMenuItem("Marginals Only (Multiple Variables)");

        final ButtonGroup group = new ButtonGroup();
        group.add(singleVariable);
        group.add(multiVariable);

        if (this.mode == BayesUpdaterEditor.SINGLE_VALUE) {
            singleVariable.setSelected(true);
        } else if (this.mode == BayesUpdaterEditor.MULTI_VALUE) {
            multiVariable.setSelected(true);
        }

        modeMenu.add(singleVariable);
        modeMenu.add(multiVariable);

        editEvidence.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                setMode(BayesUpdaterEditor.this.mode);
            }
        });

        singleVariable.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                setMode(BayesUpdaterEditor.SINGLE_VALUE);
            }
        });

        multiVariable.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                setMode(BayesUpdaterEditor.MULTI_VALUE);
            }
        });

        return menuBar;
    }

    private void setMode(final int mode) {
        this.mode = mode;

        if (mode == BayesUpdaterEditor.SINGLE_VALUE) {
            show("editEvidenceSingle");
        } else if (mode == BayesUpdaterEditor.MULTI_VALUE) {
            show("editEvidenceMultiple");
        } else {
            throw new IllegalStateException();
        }
    }

    private void resetSingleResultPanel() {
        final Window owner = (Window) getTopLevelAncestor();

        if (owner == null) {
            resetSingleResultPanelSub();
        } else {
            new WatchedProcess(owner) {
                public void watch() {
                    resetSingleResultPanelSub();
                }
            };
        }
    }

    private void resetSingleResultPanelSub() {
        final UpdatedBayesImWizard wizard = new UpdatedBayesImWizard(
                getUpdaterWrapper(), getWorkbench(), this.updatedBayesImWizardTab,
                getSelectedNode());
        wizard.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(final PropertyChangeEvent e) {
                if ("updatedBayesImWizardTab".equals(e.getPropertyName())) {
                    BayesUpdaterEditor.this.updatedBayesImWizardTab = ((Integer) (e.getNewValue()));
                }
            }
        });
        this.singleResultPanel.removeAll();
        this.singleResultPanel.add(wizard, BorderLayout.CENTER);
        this.singleResultPanel.revalidate();
        this.singleResultPanel.repaint();
    }

    private void resetMultipleResultPanel() {
        final JTextArea textArea = getEvidenceWizardMultiple().getTextArea();
        this.multiResultPanel.removeAll();
        this.multiResultPanel.add(textArea, BorderLayout.CENTER);
        this.multiResultPanel.revalidate();
        this.multiResultPanel.repaint();
    }

    private Node getSelectedNode() {
        UpdatedBayesImWizard wizard = null;
        Node selectedNode = null;

        for (int i = 0; i < this.singleResultPanel.getComponentCount(); i++) {
            final Component component = this.singleResultPanel.getComponent(i);
            if (component instanceof UpdatedBayesImWizard) {
                wizard = (UpdatedBayesImWizard) component;
            }
        }

        if (wizard != null) {
            selectedNode = wizard.getSelectedNode();
        }

        return selectedNode;
    }
}
