package edu.cmu.tetradapp.app.hpc;

import java.awt.event.ActionEvent;
import java.util.Date;

import javax.swing.AbstractAction;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import edu.cmu.tetradapp.app.TetradDesktop;
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

    /**
     * 
     */
    private static final long serialVersionUID = 8275717978736439467L;

    @Override
    public void actionPerformed(ActionEvent e) {
	JTable table = (JTable) e.getSource();
	int modelRow = Integer.valueOf(e.getActionCommand());
	DefaultTableModel activeJobTableModel = (DefaultTableModel) table
		.getModel();

	TetradDesktop desktop = (TetradDesktop) DesktopController.getInstance();
	final HpcJobManager hpcJobManager = desktop.getHpcJobManager();

	HpcJobInfo hpcJobInfo = hpcJobManager.findHpcJobInfoById(Long.valueOf(
		activeJobTableModel.getValueAt(modelRow, 0).toString())
		.longValue());

	if (hpcJobInfo != null) {
	    try {
		hpcJobInfo = hpcJobManager.requestHpcJobKilled(hpcJobInfo);

		if (hpcJobInfo != null) {
		    // Update table
		    activeJobTableModel.setValueAt("Kill Request", modelRow, 1);

		    // Update hpcJobInfo instance
		    hpcJobManager.updateHpcJobInfo(hpcJobInfo);

		    // Update hpcJobLog instance
		    HpcJobLog hpcJobLog = hpcJobManager
			    .getHpcJobLog(hpcJobInfo);
		    if (hpcJobLog != null) {
			hpcJobLog.setLastUpdatedTime(new Date(System
				.currentTimeMillis()));
			hpcJobManager.updateHpcJobLog(hpcJobLog);

			// Update hpcJobLogDetail instance
			String log = "Requested job id " + hpcJobLog.getId()
				+ " killed";

			hpcJobManager.logHpcJobLogDetail(hpcJobLog, 2, log);
		    }

		}
	    } catch (Exception e1) {
		// TODO Auto-generated catch block
		e1.printStackTrace();
	    }

	}
    }
}
