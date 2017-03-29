package edu.cmu.tetradapp.app.hpc.action;

import java.awt.Component;
import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.app.hpc.editor.HpcJobActivityEditor;
import edu.cmu.tetradapp.app.hpc.manager.HpcJobManager;
import edu.cmu.tetradapp.util.DesktopController;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobInfo;

/**
 * 
 * Feb 8, 2017 7:34:03 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class DeleteHpcJobInfoAction extends AbstractAction {

	private static final long serialVersionUID = 7915068087861233608L;

	private final Component parentComp;

	public DeleteHpcJobInfoAction(final Component parentComp) {
		this.parentComp = parentComp;
	}

	@Override
	public void actionPerformed(ActionEvent e) {

		JTable table = (JTable) e.getSource();
		int modelRow = Integer.valueOf(e.getActionCommand());
		DefaultTableModel finishedJobTableModel = (DefaultTableModel) table.getModel();

		long jobId = Long.valueOf(finishedJobTableModel.getValueAt(modelRow, HpcJobActivityEditor.ID_COLUMN).toString())
				.longValue();

		int answer = JOptionPane.showConfirmDialog(parentComp,
				"Would you like to delete this HPC job id: " + jobId + "?", "Delete HPC job",
				JOptionPane.YES_NO_OPTION);

		if (answer == JOptionPane.NO_OPTION)
			return;

		TetradDesktop desktop = (TetradDesktop) DesktopController.getInstance();
		final HpcJobManager hpcJobManager = desktop.getHpcJobManager();

		HpcJobInfo hpcJobInfo = hpcJobManager.findHpcJobInfoById(
				Long.valueOf(finishedJobTableModel.getValueAt(modelRow, HpcJobActivityEditor.ID_COLUMN).toString())
						.longValue());

		if (hpcJobInfo != null) {
			// Update table
			finishedJobTableModel.removeRow(modelRow);
			table.updateUI();
			hpcJobManager.removeHpcJobInfoTransaction(hpcJobInfo);
		}

	}

}
