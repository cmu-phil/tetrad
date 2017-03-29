package edu.cmu.tetradapp.app.hpc.action;

import java.awt.Frame;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JOptionPane;

import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetradapp.app.hpc.editor.HpcJobActivityEditor;

/**
 * 
 * Feb 7, 2017 1:53:53 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class HpcJobActivityAction extends AbstractAction {

	private static final long serialVersionUID = -8500391011385619809L;

	private static final String TITLE = "High-Performance Computing Job Activity";

	public HpcJobActivityAction(String actionTitle) {
		super(actionTitle);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		try {
			Frame ancestor = (Frame) JOptionUtils.centeringComp().getTopLevelAncestor();
			JComponent comp = new HpcJobActivityEditor();
			JOptionPane.showMessageDialog(ancestor, comp, TITLE, JOptionPane.PLAIN_MESSAGE);
		} catch (HeadlessException e1) {
			// e1.printStackTrace();
		} catch (Exception e1) {
			// e1.printStackTrace();
		}
	}

}
