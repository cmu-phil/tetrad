/**
 * 
 */
package edu.cmu.tetradapp.editor.cg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.MatteBorder;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.pitt.dbmi.cg.CgIm;

/**
 * Jul 22, 2019 3:28:29 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class CgEstimatorEditorWizard extends JPanel {

	private static final long serialVersionUID = 1L;

	private CgIm cgIm;
	private JComboBox<Node> varNamesComboBox;
    private GraphWorkbench workbench;
    private CgImNodeEditingPanel editingPanel;
	private JPanel tablePanel;
	
	private boolean enableEditing = true;
	
	public CgEstimatorEditorWizard(CgIm cgIm, GraphWorkbench workbench) {
		if(cgIm == null) {
			throw new NullPointerException();
		}
		
		if(workbench == null) {
			throw new NullPointerException();
		}
		
		workbench.setAllowDoubleClickActions(false);
        setBorder(new MatteBorder(10, 10, 10, 10, getBackground()));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setFont(new Font("SanSerif", Font.BOLD, 12));
        
        // Set up components.
        this.varNamesComboBox = createVarNamesComboBox(cgIm);
        workbench.scrollWorkbenchToNode(
                (Node) (varNamesComboBox.getSelectedItem()));
        
        JButton nextButton = new JButton("Next");
        nextButton.setMnemonic('N');
        
        Node node = (Node) (varNamesComboBox.getSelectedItem());
        editingPanel = new CgImNodeEditingPanel(node, cgIm);
        editingPanel.addPropertyChangeListener((evt) -> {
            if ("modelChanged".equals(evt.getPropertyName())) {
                firePropertyChange("modelChanged", null, null);
            }
        });
        
        JScrollPane scroll = new JScrollPane(editingPanel);
        scroll.setPreferredSize(new Dimension(0, 150));
        
        tablePanel = new JPanel();
        tablePanel.setLayout(new BorderLayout());
        tablePanel.add(scroll, BorderLayout.CENTER);
        
        editingPanel.grabFocus();

        // Do Layout.
        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Choose the next variable to edit:  "));
        b1.add(varNamesComboBox);
        b1.add(nextButton);
        b1.add(Box.createHorizontalGlue());

        Box b4 = Box.createHorizontalBox();
        b4.add(tablePanel, BorderLayout.CENTER);

        add(b1);
        add(Box.createVerticalStrut(1));
        add(b4);

        // Add listeners.
        varNamesComboBox.addActionListener((e) -> {
            Node n = (Node) (varNamesComboBox.getSelectedItem());
            getWorkbench().scrollWorkbenchToNode(n);
            setCurrentNode(n);
        });

        nextButton.addActionListener((e) -> {
            int current = varNamesComboBox.getSelectedIndex();
            int max = varNamesComboBox.getItemCount();

            ++current;

            if (current == max) {
                JOptionPane.showMessageDialog(CgEstimatorEditorWizard.this,
                        "There are no more variables.");
            }

            int set = (current < max) ? current : 0;

            varNamesComboBox.setSelectedIndex(set);
        });

        workbench.addPropertyChangeListener((evt) -> {
            if (evt.getPropertyName().equals("selectedNodes")) {
                List selection = (List) (evt.getNewValue());
                if (selection.size() == 1) {
                    varNamesComboBox.setSelectedItem((Node) (selection.get(0)));
                }
            }
        });

		this.cgIm = cgIm;
		this.workbench = workbench;
	}
    
	private void setCurrentNode(Node node) {
		editingPanel = new CgImNodeEditingPanel(node, cgIm);
        editingPanel.addPropertyChangeListener((evt) -> {
            if ("modelChanged".equals(evt.getPropertyName())) {
                firePropertyChange("modelChanged", null, null);
            }
        });
        
        JScrollPane scroll = new JScrollPane(editingPanel);
        scroll.setPreferredSize(new Dimension(0, 150));
        
        tablePanel.removeAll();
        tablePanel.add(scroll, BorderLayout.CENTER);
        tablePanel.revalidate();
        tablePanel.repaint();
        
        editingPanel.grabFocus();
	}

	private JComboBox<Node> createVarNamesComboBox(CgIm cgIm) {
		JComboBox<Node> varNameComboBox = new JComboBox<>();
        varNameComboBox.setBackground(Color.white);

        Graph graph = cgIm.getDag();
		
        List<Node> nodes = graph.getNodes().stream().collect(Collectors.toList());
        Collections.sort(nodes);
        nodes.forEach(varNameComboBox::addItem);

        if (varNameComboBox.getItemCount() > 0) {
            varNameComboBox.setSelectedIndex(0);
        }

        return varNameComboBox;
	}

	public CgIm getCgIm() {
		return cgIm;
	}


	private GraphWorkbench getWorkbench() {
        return workbench;
    }

    public boolean isEnableEditing() {
        return enableEditing;
    }

    public void enableEditing(boolean enableEditing) {
        this.enableEditing = enableEditing;
        if (this.workbench != null) {
            this.workbench.enableEditing(enableEditing);
        }
    }
}
