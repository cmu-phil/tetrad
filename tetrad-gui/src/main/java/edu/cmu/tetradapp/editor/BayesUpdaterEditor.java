///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.model.*;
import edu.cmu.tetradapp.session.DelegatesEditing;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;

/**
 * Lets the user calculate updated probabilities for a Bayes net.
 *
 * @author josephramsey
 * @version $Id: $Id
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
     * Contains the other right-hand panels; included so that the right-hand view panel (contained in this panel) can
     * easily be reset.
     */
    private JPanel singleResultPanel;

    /**
     * Contains the text printout of the multi result.
     */
    private JPanel multiResultPanel;

    /**
     * Remember which tab the user selected last time around so as not to irritate the user.
     */
    private int updatedBayesImWizardTab;

    /**
     * A JPanel with a card layout that contains the various cards of the wizard.
     */
    private JPanel cardPanel;

    /**
     * The getModel mode.
     */
    private int mode = BayesUpdaterEditor.SINGLE_VALUE;

    //===============================CONSTRUCTORS=========================//

    /**
     * Constructs a new instantiated model editor from a Bayes Updater.
     */
    private BayesUpdaterEditor(UpdaterWrapper updaterWrapper) {
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
        file.add(new SaveComponentImage(this.workbench, "Save Graph Image..."));
        add(menuBar, BorderLayout.NORTH);

        this.workbench.addPropertyChangeListener(evt -> {
            if (BayesUpdaterEditor.this.mode == BayesUpdaterEditor.MULTI_VALUE
                && "selectedNodes".equals(evt.getPropertyName())) {
                setMode(BayesUpdaterEditor.MULTI_VALUE);
            }
        });
    }

    /**
     * Constructs a new instantiated model editor from a Bayes IM wrapper.
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.RowSummingExactWrapper} object
     */
    public BayesUpdaterEditor(RowSummingExactWrapper wrapper) {
        this((UpdaterWrapper) wrapper);
    }

    /**
     * Constructs a new instantiated model editor from a Bayes IM wrapper.
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.CptInvariantUpdaterWrapper} object
     */
    public BayesUpdaterEditor(CptInvariantUpdaterWrapper wrapper) {
        this((UpdaterWrapper) wrapper);
    }

    /**
     * Constructs a new instantiated model editor from a Bayes IM wrapper.
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.ApproximateUpdaterWrapper} object
     */
    public BayesUpdaterEditor(ApproximateUpdaterWrapper wrapper) {
        this((UpdaterWrapper) wrapper);
    }

    /**
     * <p>Constructor for BayesUpdaterEditor.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.JunctionTreeWrapper} object
     */
    public BayesUpdaterEditor(JunctionTreeWrapper wrapper) {
        this((UpdaterWrapper) wrapper);
    }

    //================================PUBLIC METHODS========================//

    /**
     * {@inheritDoc}
     * <p>
     * Sets the name of this editor.
     */
    public void setName(String name) {
        String oldName = getName();
        super.setName(name);
        this.firePropertyChange("name", oldName, getName());
    }

    private EvidenceWizardSingle getEvidenceWizardSingle() {
        return this.evidenceWizardSingle;
    }

    private EvidenceWizardMultiple getEvidenceWizardMultiple() {
        return this.evidenceWizardMultiple;
    }

    /**
     * <p>getEditDelegate.</p>
     *
     * @return a {@link javax.swing.JComponent} object
     */
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
     *
     * @param e a {@link java.beans.PropertyChangeEvent} object
     */
    public void propertyChange(PropertyChangeEvent e) {
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
        this.workbench = new GraphWorkbench(updaterWrapper.getBayesUpdater().getManipulatedGraph());
        this.workbench.setAllowDoubleClickActions(false);
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
        this.cardPanel = new JPanel();
        this.cardPanel.setLayout(new CardLayout());
        this.evidenceWizardSingle
                = new EvidenceWizardSingle(updaterWrapper, getWorkbench());
        getEvidenceWizardSingle().addPropertyChangeListener(
                e -> {
                    if ("updateButtonPressed".equals(e.getPropertyName())) {
                        resetSingleResultPanel();
                        show("viewSingleResult");
                    }
                });
        this.cardPanel.add(new JScrollPane(getEvidenceWizardSingle()),
                "editEvidenceSingle");

        this.evidenceWizardMultiple
                = new EvidenceWizardMultiple(updaterWrapper, getWorkbench());
        getEvidenceWizardMultiple().addPropertyChangeListener(
                e -> {
                    if ("updateButtonPressed".equals(e.getPropertyName())) {
                        resetMultipleResultPanel();
                        show("viewMultiResult");
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

    private void show(String s) {
        CardLayout card = (CardLayout) this.cardPanel.getLayout();
        card.show(this.cardPanel, s);
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu evidenceMenu = new JMenu("Evidence");
        menuBar.add(evidenceMenu);
        JMenuItem editEvidence = new JMenuItem("Edit Evidence");
        editEvidence.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.ALT_DOWN_MASK));
        evidenceMenu.add(editEvidence);

        JMenu modeMenu = new JMenu("Mode");
        menuBar.add(modeMenu);
        JCheckBoxMenuItem singleVariable
                = new JCheckBoxMenuItem("In-Depth Information (Single Variable)");
        JCheckBoxMenuItem multiVariable
                = new JCheckBoxMenuItem("Marginals Only (Multiple Variables)");

        ButtonGroup group = new ButtonGroup();
        group.add(singleVariable);
        group.add(multiVariable);

        if (this.mode == BayesUpdaterEditor.SINGLE_VALUE) {
            singleVariable.setSelected(true);
        } else if (this.mode == BayesUpdaterEditor.MULTI_VALUE) {
            multiVariable.setSelected(true);
        }

        modeMenu.add(singleVariable);
        modeMenu.add(multiVariable);

        editEvidence.addActionListener(e -> setMode(BayesUpdaterEditor.this.mode));

        singleVariable.addActionListener(e -> setMode(BayesUpdaterEditor.SINGLE_VALUE));

        multiVariable.addActionListener(e -> setMode(BayesUpdaterEditor.MULTI_VALUE));

        return menuBar;
    }

    private void setMode(int mode) {
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
        class MyWatchedProcess extends WatchedProcess {
            public void watch() {
                resetSingleResultPanelSub();
            }
        }

        new MyWatchedProcess();
    }

    private void resetSingleResultPanelSub() {
        UpdatedBayesImWizard wizard = new UpdatedBayesImWizard(
                getUpdaterWrapper(), getWorkbench(), this.updatedBayesImWizardTab,
                getSelectedNode());
        wizard.addPropertyChangeListener(e -> {
            if ("updatedBayesImWizardTab".equals(e.getPropertyName())) {
                BayesUpdaterEditor.this.updatedBayesImWizardTab = ((Integer) (e.getNewValue()));
            }
        });
        this.singleResultPanel.removeAll();
        this.singleResultPanel.add(wizard, BorderLayout.CENTER);
        this.singleResultPanel.revalidate();
        this.singleResultPanel.repaint();
    }

    private void resetMultipleResultPanel() {
        JTextArea textArea = getEvidenceWizardMultiple().getTextArea();
        this.multiResultPanel.removeAll();
        this.multiResultPanel.add(textArea, BorderLayout.CENTER);
        this.multiResultPanel.revalidate();
        this.multiResultPanel.repaint();
    }

    private Node getSelectedNode() {
        UpdatedBayesImWizard wizard = null;
        Node selectedNode = null;

        for (int i = 0; i < this.singleResultPanel.getComponentCount(); i++) {
            Component component = this.singleResultPanel.getComponent(i);
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

