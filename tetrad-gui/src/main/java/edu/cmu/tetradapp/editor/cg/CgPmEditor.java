/**
 * 
 */
package edu.cmu.tetradapp.editor.cg;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JComponent;
import javax.swing.JPanel;

import edu.cmu.tetrad.session.DelegatesEditing;

/**
 * Jun 20, 2019 3:40:42 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class CgPmEditor extends JPanel implements PropertyChangeListener, DelegatesEditing {

	private static final long serialVersionUID = 1L;

	@Override
	public JComponent getEditDelegate() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		// TODO Auto-generated method stub

	}

}
