/**
 * 
 */
package edu.cmu.tetradapp.editor.cg;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.editor.bayes.BayesImNodeEditingTable;
import edu.pitt.dbmi.cg.CgIm;

/**
 * Jul 5, 2019 4:18:54 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class CgImNodeEditingPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	
	
	
	public CgImNodeEditingPanel(Node node, CgIm cgIm) {
		if (node == null) {
            return;
        }

        if (cgIm == null) {
        	throw new NullPointerException();
        }
        
        setLayout(new BorderLayout());
        
        System.out.println("node: " + node);
        
        int cgContIdx = cgIm.getCgContinuousNodeIndex(node);
        int cgDiscreteIdx = cgIm.getCgDiscreteNodeIndex(node);
        
        System.out.println("cgContIdx: " + cgContIdx);
        System.out.println("cgDiscreteIdx: " + cgDiscreteIdx);
        
        // Bayes
        if(node instanceof DiscreteVariable && cgDiscreteIdx < 0) {
        	System.out.println("Bayes");
        	BayesImNodeEditingTable editingTable = new BayesImNodeEditingTable(node, cgIm.getBayesIm());
        	editingTable.addPropertyChangeListener((evt) -> {
                if ("modelChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("modelChanged", null, null);
                }
            });

            JScrollPane scroll = new JScrollPane(editingTable);
            scroll.setPreferredSize(new Dimension(0, 150));
        	
        	add(scroll, BorderLayout.CENTER);
        // SEM
        } else if (node instanceof ContinuousVariable && cgContIdx < 0) {
        	System.out.println("SEM");
        	CgSemParameterEditor semEditor = new CgSemParameterEditor(cgIm, node);
        	add(semEditor, BorderLayout.CENTER);
        // CG Discrete
        } else if(cgDiscreteIdx > -1) {
        	System.out.println("CG Discrete");
        // CG Continuous
        } else if(cgContIdx > -1) {
        	System.out.println("CG Continuous");
        } else {
        	throw new IllegalArgumentException("Node " + node +
                    " is not a node" + " for CgIm " + cgIm + ".");
        }
        
        revalidate();
        repaint();
        
	}

}
