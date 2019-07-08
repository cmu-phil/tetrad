/**
 * 
 */
package edu.cmu.tetradapp.editor.cg;

import java.awt.BorderLayout;

import javax.swing.JPanel;

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
        
        int cgContIdx = cgIm.getCgContinuousNodeIndex(node);
        int cgDiscreteIdx = cgIm.getCgDiscreteNodeIndex(node);
        
        // Bayes
        if(node instanceof DiscreteVariable && cgDiscreteIdx < 0) {
        	BayesImNodeEditingTable editingTable = new BayesImNodeEditingTable(node, cgIm.getBayesIm());
        	add(editingTable, BorderLayout.CENTER);
        // SEM
        } else if (node instanceof ContinuousVariable && cgContIdx < 0) {
        	CgSemParameterEditor semEditor = new CgSemParameterEditor(cgIm, node);
        	add(semEditor, BorderLayout.CENTER);
        // CG Discrete
        } else if(cgDiscreteIdx > -1) {
        
        // CG Continuous
        } else if(cgContIdx > -1) {
        	
        } else {
        	throw new IllegalArgumentException("Node " + node +
                    " is not a node" + " for CgIm " + cgIm + ".");
        }
        
	}

}
