/**
 * 
 */
package edu.cmu.tetradapp.editor.cg;

import java.awt.BorderLayout;

import javax.swing.JPanel;

import edu.cmu.tetrad.graph.Node;
import edu.pitt.dbmi.cg.CgPm;

/**
 * Jun 21, 2019 5:29:14 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class CgParameterEditor extends JPanel {

	private static final long serialVersionUID = 1L;

	private CgPm cgPm;
	private Node node;
	
	public CgParameterEditor(CgPm cgPm, Node node) {
        if (cgPm == null) {
            throw new NullPointerException();
        }

        setLayout(new BorderLayout());

        if (node == null) {
//            return;
            throw new NullPointerException();
        }

        this.cgPm = cgPm;
        this.node = node;
		
	}
}
