package edu.cmu.tetradapp.app.hpc.action;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.util.Date;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.app.hpc.editor.HpcJobActivityEditor;
import edu.cmu.tetradapp.app.hpc.manager.HpcJobManager;
import edu.cmu.tetradapp.util.DesktopController;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobInfo;
import edu.pitt.dbmi.tetrad.db.entity.HpcJobLog;

/**
 * 
 * Feb 8, 2017 5:43:52 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class KillHpcJobAction extends AbstractAction {

	private static final long serialVersionUID = 8275717978736439467L;

	private final Component parentComp;

	public KillHpcJobAction(Component parentComp) {
		this.parentComp = parentComp;
	}

	@Override
	public void actionPerformed(ActionEvent e) {

		JTable table = (JTable) e.getSource();
		int modelRow = Integer.valueOf(e.getActionCommand());
		DefaultTableModel activeJobTableModel = (DefaultTableModel) table.getModel();

		long jobId = Long.valueOf(activeJobTableModel.getValueAt(modelRow, HpcJobActivityEditor.ID_COLUMN).toString())
				.longValue();

		int answer = JOptionPane.showConfirmDialog(parentComp,
				"Would you like to cancel this HPC job id: " + jobId + "?", "Cancel HPC job",
				JOptionPane.YES_NO_OPTION);

		if (answer == JOptionPane.NO_OPTION)
			return;

		TetradDesktop desktop = (TetradDesktop) DesktopController.getInstance();
		final HpcJobManager hpcJobManager = desktop.getHpcJobManager();

		HpcJobInfo hpcJobInfo = hpcJobManager.findHpcJobInfoById(
				Long.valueOf(activeJobTableModel.getValueAt(modelRow, HpcJobActivityEditor.ID_COLUMN).toString())
						.longValue());

		if (hpcJobInfo != null) {
			try {
				if (hpcJobInfo.getPid() != null) {
					// Update table
					activeJobTableModel.setValueAt("Kill Request", modelRow, 1);
					table.updateUI();

					hpcJobInfo = hpcJobManager.requestHpcJobKilled(hpcJobInfo);
					// Update hpcJobInfo instance
					hpcJobManager.updateHpcJobInfo(hpcJobInfo);

					// Update hpcJobLog instance
					HpcJobLog hpcJobLog = hpcJobManager.getHpcJobLog(hpcJobInfo);

					if (hpcJobLog != null) {
						hpcJobLog.setLastUpdatedTime(new Date(System.currentTimeMillis()));
						hpcJobManager.updateHpcJobLog(hpcJobLog);

						// Update hpcJobLogDetail instance
						String log = "Requested job id " + hpcJobLog.getId() + " killed";

						hpcJobManager.logHpcJobLogDetail(hpcJobLog, 2, log);
					}
				} else {
					// Update table
					activeJobTableModel.removeRow(modelRow);
					table.updateUI();

					hpcJobManager.removePendingHpcJob(hpcJobInfo);

					hpcJobInfo.setStatus(4); // Killed

					// Update hpcJobInfo instance
					hpcJobManager.updateHpcJobInfo(hpcJobInfo);

					// Update hpcJobLog instance
					HpcJobLog hpcJobLog = hpcJobManager.getHpcJobLog(hpcJobInfo);
					if (hpcJobLog != null) {
						hpcJobLog.setCanceledTime(new Date(System.currentTimeMillis()));
						hpcJobLog.setLastUpdatedTime(new Date(System.currentTimeMillis()));
						hpcJobManager.updateHpcJobLog(hpcJobLog);

						// Update hpcJobLogDetail instance
						String log = "Killed job id " + hpcJobLog.getId();

						hpcJobManager.logHpcJobLogDetail(hpcJobLog, 4, log);
					}
				}

			} catch (Exception e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

		}

	}
}
