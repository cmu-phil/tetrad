/**
 * 
 */
package edu.cmu.tetradapp.editor.cg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;

import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.pitt.dbmi.cg.CgPm;

/**
 * Jun 21, 2019 12:55:16 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class CgPmEditorWizard extends JPanel {
	
	private static final long serialVersionUID = 1L;
	
	private CgPm cgPm;
	
	/**
     * Lets the user select the variable they want to edit.
     */
    private JComboBox<Node> variableChooser;

    /**
     * True iff the editing of measured variables is allowed.
     */
    private boolean editingMeasuredVariablesAllowed = false;

    /**
     * True iff the editing of latent variables is allowed.
     */
    private boolean editingLatentVariablesAllowed = false;

    /**
     * Lets the user see graphically which variable is being edited and click to
     * another variable.
     */
    private GraphWorkbench workbench;

    /**
     * A reference to the category editor.
     */
    private CgCategoryEditor categoryEditor;

    /**
     * A reference to the spinner model.
     */
    private SpinnerNumberModel spinnerModel;

    /**
     * The preset strings that will be used.
     */
    private final String[][] presetStrings = new String[][]{{"Low", "High"},
    {"Low", "Medium", "High"}, {"On", "Off"}, {"Yes", "No"}};

    /**
     * ?
     */
    private List copiedCategories;

    /**
     * ?
     */
    private final Map<Object, Integer> labels = new HashMap<>();

    /**
     * ?
     */
    private JSpinner numCategoriesSpinner;

    /**
     *
     */
    private JMenu presetMenu;

    private JPanel editorWizard;
    
    private JComponent bayesEditorWizard = null;
    private JComponent semEditorWizard = null;
    
    /**
     * This is the wizard for the PMEditor class. Its function is to allow the
     * user to enter, for each variable in the associated Graph, the number of
     * categories it may take on and the string names for each of those
     * categories.
     */
    public CgPmEditorWizard(CgPm cgPm, GraphWorkbench workbench) {
    	if(cgPm == null) {
    		throw new NullPointerException();
        }

        if (workbench == null) {
            throw new NullPointerException();
        }

        this.cgPm = cgPm;
        this.workbench = workbench;
        
        workbench().setAllowDoubleClickActions(false);
        
        // Construct components.
        createVariableChooser(getCgPm(), workbench());

        JButton nextButton = new JButton("Next");
        nextButton.setMnemonic('N');

        int numCategories = getNumCategories();

        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel("Edit variable atributes for: "));
        b2.add(variableChooser);
        b2.add(nextButton);
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(10));

        JMenuBar menuBar = createMenuBar();

        b1.setBorder(new EmptyBorder(10, 10, 0, 10));

        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BorderLayout());
        headerPanel.add(menuBar, BorderLayout.NORTH);
        headerPanel.add(b1, BorderLayout.CENTER);

        editorWizard = new JPanel(new BorderLayout());
        
        setLayout(new BorderLayout());
        add(headerPanel, BorderLayout.NORTH);
        add(editorWizard, BorderLayout.CENTER);
 
        workbench.addPropertyChangeListener((evt) -> {
            if (evt.getPropertyName().equals("selectedNodes")) {
                List selection = (List) (evt.getNewValue());
                if (selection.size() == 1) {
                    Node node = (Node) (selection.get(0));
                    variableChooser.setSelectedItem(node);
                }
            }
        });

        variableChooser.addActionListener((e) -> {
            Node n = (Node) (variableChooser.getSelectedItem());
            workbench().scrollWorkbenchToNode(n);
            setNode(n);
        });

        nextButton.addActionListener((e) -> {
            int current = variableChooser.getSelectedIndex();
            int max = variableChooser.getItemCount();

            ++current;

            if (current == max) {
                JOptionPane.showMessageDialog(CgPmEditorWizard.this,
                        "There are no more variables.");
            }

            int set = (current < max) ? current : 0;

            variableChooser.setSelectedIndex(set);
        });

        editorWizard.removeAll();
        if (numCategories != 0) {
        	if(bayesEditorWizard == null) {
            	bayesEditorWizard = createBayesEditorWizard(numCategories);
        	}
        	
        	editorWizard.add(bayesEditorWizard, BorderLayout.CENTER);
        }else { // SEM or CG Continuous
        	if(semEditorWizard == null) {
        		semEditorWizard = createSemEditorWizard();
        	}
        	
        	editorWizard.add(semEditorWizard, BorderLayout.CENTER);
        }

        enableByNodeType();
    }
    
    private JComponent createSemEditorWizard() {
    	Box b1 = Box.createVerticalBox();
    	return b1; 
    }
    
    private JComponent createBayesEditorWizard(int numCategories) {
    	Box b1 = Box.createVerticalBox();
    	
    	spinnerModel = new SpinnerNumberModel(numCategories, 2, 1000, 1);
        numCategoriesSpinner = new JSpinner(spinnerModel) {

            private static final long serialVersionUID = -7932603602816371347L;

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }

        };
        numCategoriesSpinner.setFont(new Font("Serif", Font.PLAIN, 12));
        numCategoriesSpinner.addChangeListener((e) -> {
            JSpinner spinner = (JSpinner) e.getSource();
            SpinnerNumberModel model
                    = (SpinnerNumberModel) spinner.getModel();
            setNumCategories(model.getNumber().intValue());
        });

        Box b3 = Box.createHorizontalBox();
        b3.add(new JLabel("Number of categories:  "));
        b3.add(numCategoriesSpinner);
        b3.add(Box.createHorizontalGlue());
        b1.add(b3);

        b1.add(Box.createVerticalStrut(10));
        
        categoryEditor = new CgCategoryEditor(cgPm, getNode());
        
        Box b4 = Box.createHorizontalBox();
        b4.add(new JLabel("Category names: "));
        b4.add(Box.createHorizontalGlue());
        b1.add(b4);

        b1.add(Box.createVerticalStrut(10));

        Box b5 = Box.createHorizontalBox();
        b5.add(categoryEditor);
        b1.add(b5);

        Box b6 = Box.createHorizontalBox();
        b6.add(Box.createRigidArea(new Dimension(400, 0)));
        b1.add(b6);
        b1.add(Box.createVerticalGlue());
        
    	return b1;
    }
    
    private void copyCategories() {
        Node node = (Node) variableChooser.getSelectedItem();
        DiscreteVariable variable
                = (DiscreteVariable) cgPm.getDiscreteVariable(node);
        this.copiedCategories = variable.getCategories();
    }

    private void pasteCategories() {
        if (this.copiedCategories != null) {
            setCategories(this.copiedCategories);
        }
    }

    private void setCategories(List categories) {
        categoryEditor.setCategories(categories);
        spinnerModel.setValue(categories.size());
        firePropertyChange("modelChanged", null, null);
    }

    private boolean isEditingMeasuredVariablesAllowed() {
        return editingMeasuredVariablesAllowed;
    }

    private boolean isEditingLatentVariablesAllowed() {
        return editingLatentVariablesAllowed;
    }

    private void enableByNodeType() {
        if (!isEditingMeasuredVariablesAllowed() && categoryEditor.getNode().getNodeType() == NodeType.MEASURED) {
            setEnabled(false);
        } else if (!isEditingLatentVariablesAllowed() && categoryEditor.getNode().getNodeType() == NodeType.LATENT) {
            setEnabled(false);
        } else {
            setEnabled(true);
        }
    }

    private void setNode(Node node) {
        categoryEditor.setNode(node);
        int numCategories = cgPm.getDiscreteNumCategories(node);
        spinnerModel.setValue(numCategories);
        firePropertyChange("modelChanged", null, null);
        enableByNodeType();
    }

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu presetMenu = new JMenu("Presets");
        this.presetMenu = presetMenu;
        menuBar.add(presetMenu);

        for (int i = 0; i < presetStrings.length; i++) {
            StringBuilder buf = new StringBuilder();

            for (int j = 0; j < presetStrings[i].length; j++) {
                buf.append(presetStrings[i][j]);

                if (j < presetStrings[i].length - 1) {
                    buf.append("-");
                }
            }

            Action action = new IndexedAction(buf.toString(), i) {

                private static final long serialVersionUID = 5052478563546335636L;

                @Override
                public void actionPerformed(ActionEvent e) {
                    setCategories(Arrays.asList(presetStrings[getIndex()]));
                }

            };

            presetMenu.add(action);
        }

        presetMenu.addSeparator();

        Action sequence = new AbstractAction("x1, x2, x3, ...") {

            private static final long serialVersionUID = 4377386270269629176L;

            @Override
            public void actionPerformed(ActionEvent e) {
                List categories = new ArrayList();
                String ret = JOptionPane.showInputDialog(
                        JOptionUtils.centeringComp(),
                        "Please input a prefix string for the sequence: ",
                        "category");

                int numCategories = getNumCategories();

                for (int i = 0; i < numCategories; i++) {
                    categories.add(ret + (i + 1));
                }

                setCategories(categories);
            }
        };

        presetMenu.add(sequence);

        JMenu transfer = new JMenu("Transfer");
        JMenuItem copy = new JMenuItem("Copy categories");
        JMenuItem paste = new JMenuItem("Paste categories");

        copy.addActionListener((e) -> {
            copyCategories();

            JOptionPane.showMessageDialog(JOptionUtils.centeringComp(),
                    "<html>"
                    + "The categories for this node have been copied; to transfer "
                    + "<br>these categories, choose another node and paste. You may"
                    + "<br>paste multiple times." + "</html>");
        });

        paste.addActionListener((e) -> {
            pasteCategories();
        });

        copy.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK));
        paste.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK));

        transfer.add(copy);
        transfer.add(paste);

        menuBar.add(transfer);

        return menuBar;
    }
    
    private void setNumCategories(int numCategories) {
    	this.categoryEditor.setNumCategories(numCategories);
        firePropertyChange("modelChanged", null, null);
    }
    
    private int getNumCategories() {
    	int numCategories = getCgPm().getDiscreteNumCategories(getNode());
    	return numCategories;
    }
    
    private Node getNode() {
    	Node selectedItem = (Node) variableChooser.getSelectedItem();

        if (selectedItem == null) {
            throw new NullPointerException();
        }

        return selectedItem;
    }
    
    private CgPm getCgPm() {
    	return this.cgPm;
    }
    
    private GraphWorkbench workbench() {
        return this.workbench;
    }

    private void createVariableChooser(CgPm cgPm, GraphWorkbench workbench) {
    	variableChooser = new JComboBox<>();
        variableChooser.setBackground(Color.white);
        
        Graph graphModel = cgPm.getGraph();
        
        List<Node> nodes = graphModel.getNodes().stream().collect(Collectors.toList());
        Collections.sort(nodes);
        nodes.forEach(variableChooser::addItem);

        if (variableChooser.getItemCount() > 0) {
            variableChooser.setSelectedIndex(0);
        }

        workbench.scrollWorkbenchToNode((Node) variableChooser.getSelectedItem());
    }
    
    /**
     * The actionPerformed method is still abstract.
     */
    abstract static class IndexedAction extends AbstractAction {

        private static final long serialVersionUID = -8261331986030513841L;

        private final int index;

        public IndexedAction(String name, int index) {
            super(name);
            this.index = index;
        }

        public int getIndex() {
            return index;
        }

    }


}
