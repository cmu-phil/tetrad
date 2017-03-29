package edu.cmu.tetradapp.app.hpc.action;

import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JCheckBox;
import javax.swing.JTabbedPane;

import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;

/**
 * 
 * Feb 9, 2017 8:09:33 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class HpcAccountSelectionAction extends AbstractAction {

	private static final long serialVersionUID = -5506074283478552872L;

	private final List<HpcAccount> hpcAccounts;

	private final List<HpcAccount> checkedHpcAccountList;

	private final JTabbedPane tabbedPane;

	public HpcAccountSelectionAction(final List<HpcAccount> hpcAccounts, final List<HpcAccount> checkedHpcAccountList,
			final JTabbedPane tabbedPane) {
		this.hpcAccounts = hpcAccounts;
		this.checkedHpcAccountList = checkedHpcAccountList;
		this.tabbedPane = tabbedPane;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		final JCheckBox checkBox = (JCheckBox) e.getSource();
		for (HpcAccount hpcAccount : hpcAccounts) {
			if (checkBox.getText().equals(hpcAccount.getConnectionName())) {
				if (checkBox.isSelected() && !checkedHpcAccountList.contains(hpcAccount)) {
					checkedHpcAccountList.add(hpcAccount);
				} else if (!checkBox.isSelected() && checkedHpcAccountList.contains(hpcAccount)) {
					checkedHpcAccountList.remove(hpcAccount);
				}
			}
		}
		int index = tabbedPane.getSelectedIndex();
		tabbedPane.setSelectedIndex(-1);
		tabbedPane.setSelectedIndex(index);
	}

}
