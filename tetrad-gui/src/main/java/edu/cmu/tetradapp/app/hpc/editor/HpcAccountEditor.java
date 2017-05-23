package edu.cmu.tetradapp.app.hpc.editor;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Date;

import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import edu.cmu.tetradapp.app.hpc.manager.HpcAccountManager;
import edu.cmu.tetradapp.app.hpc.util.HpcAccountUtils;
import edu.pitt.dbmi.tetrad.db.entity.HpcAccount;

/**
 * 
 * Oct 31, 2016 1:50:37 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class HpcAccountEditor extends JPanel {

	private static final long serialVersionUID = -3028667139958773226L;

	private final JComponent parentComponent;

	private final DefaultListModel<HpcAccount> listModel;

	private final HpcAccountManager hpcAccountManager;

	private final HpcAccount hpcAccount;

	public HpcAccountEditor(final JComponent parentComponent, final DefaultListModel<HpcAccount> listModel,
			final HpcAccountManager hpcAccountManager, final HpcAccount hpcAccount) {
		this.parentComponent = parentComponent;
		this.listModel = listModel;
		this.hpcAccountManager = hpcAccountManager;
		this.hpcAccount = hpcAccount;
		initiateUI();
	}

	private void initiateUI() {
		setLayout(new BorderLayout());

		// Header
		JPanel headerPanel = new JPanel(new BorderLayout());
		JLabel headerLabel = new JLabel("High-Performance Computing Account");
		headerLabel.setFont(new Font(headerLabel.getFont().getName(), Font.BOLD, 20));
		headerPanel.add(headerLabel, BorderLayout.CENTER);
		JPanel spacePanel = new JPanel();
		spacePanel.setSize(300, 100);
		headerPanel.add(spacePanel, BorderLayout.SOUTH);
		add(headerPanel, BorderLayout.NORTH);

		// Content
		Box contentBox = Box.createVerticalBox();

		// Connection Name
		Box connBox = Box.createHorizontalBox();
		JLabel connLabel = new JLabel("Connection", JLabel.TRAILING);
		connLabel.setPreferredSize(new Dimension(100, 5));
		connBox.add(connLabel);

		final JTextField connField = new JTextField(20);
		connField.setText(hpcAccount.getConnectionName());
		connField.addKeyListener(new KeyListener() {

			@Override
			public void keyTyped(KeyEvent e) {
			}

			@Override
			public void keyReleased(KeyEvent e) {
				hpcAccount.setConnectionName(connField.getText());
			}

			@Override
			public void keyPressed(KeyEvent e) {
			}
		});
		connLabel.setLabelFor(connField);
		connBox.add(connField);

		contentBox.add(connBox);

		// Username
		Box userBox = Box.createHorizontalBox();
		JLabel userLabel = new JLabel("Username", JLabel.TRAILING);
		userLabel.setPreferredSize(new Dimension(100, 5));
		userBox.add(userLabel);

		final JTextField userField = new JTextField(20);
		userField.setText(hpcAccount.getUsername());
		userField.addKeyListener(new KeyListener() {

			@Override
			public void keyTyped(KeyEvent e) {
			}

			@Override
			public void keyReleased(KeyEvent e) {
				hpcAccount.setUsername(userField.getText());
			}

			@Override
			public void keyPressed(KeyEvent e) {
			}
		});
		userLabel.setLabelFor(userField);
		userBox.add(userField);

		contentBox.add(userBox);

		// Password
		Box passBox = Box.createHorizontalBox();
		JLabel passLabel = new JLabel("Password", JLabel.TRAILING);
		passLabel.setPreferredSize(new Dimension(100, 5));
		passBox.add(passLabel);

		final JPasswordField passField = new JPasswordField(20);
		passField.setText(hpcAccount.getPassword());
		passField.addKeyListener(new KeyListener() {

			@Override
			public void keyTyped(KeyEvent e) {
			}

			@Override
			public void keyReleased(KeyEvent e) {
				hpcAccount.setPassword(new String(passField.getPassword()));
			}

			@Override
			public void keyPressed(KeyEvent e) {
			}
		});
		passLabel.setLabelFor(passField);
		passBox.add(passField);

		contentBox.add(passBox);

		// Scheme
		JPanel schemePanel = new JPanel(new BorderLayout());
		JLabel schemeLabel = new JLabel("Scheme", JLabel.TRAILING);
		schemeLabel.setPreferredSize(new Dimension(100, 5));
		schemePanel.add(schemeLabel, BorderLayout.WEST);

		final JRadioButton httpRadioButton = new JRadioButton("http");
		final JRadioButton httpsRadioButton = new JRadioButton("https");
		if (hpcAccount.getScheme().equalsIgnoreCase("https")) {
			httpsRadioButton.setSelected(true);
		} else {
			httpRadioButton.setSelected(true);
		}
		ButtonGroup schemeGroup = new ButtonGroup();
		schemeGroup.add(httpRadioButton);
		schemeGroup.add(httpsRadioButton);
		Box schemeRadioBox = Box.createHorizontalBox();
		schemeRadioBox.add(httpRadioButton);
		schemeRadioBox.add(httpsRadioButton);
		schemeLabel.setLabelFor(schemeRadioBox);
		ActionListener schemeActionListener = new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (httpRadioButton.isSelected()) {
					hpcAccount.setScheme("http");
				} else {
					hpcAccount.setScheme("https");
				}
			}
		};
		httpRadioButton.addActionListener(schemeActionListener);
		httpsRadioButton.addActionListener(schemeActionListener);
		schemePanel.add(schemeRadioBox, BorderLayout.CENTER);

		contentBox.add(schemePanel);

		// Host
		Box hostBox = Box.createHorizontalBox();
		JLabel hostLabel = new JLabel("Host Name", JLabel.TRAILING);
		hostLabel.setPreferredSize(new Dimension(100, 5));
		hostBox.add(hostLabel);

		final JTextField hostField = new JTextField(20);
		hostField.setText(hpcAccount.getHostname());
		hostField.addKeyListener(new KeyListener() {

			@Override
			public void keyTyped(KeyEvent e) {
			}

			@Override
			public void keyReleased(KeyEvent e) {
				hpcAccount.setHostname(hostField.getText());
			}

			@Override
			public void keyPressed(KeyEvent e) {
			}
		});
		hostLabel.setLabelFor(hostField);
		hostBox.add(hostField);

		contentBox.add(hostBox);

		// Port number
		Box portBox = Box.createHorizontalBox();
		JLabel portLabel = new JLabel("Port Number", JLabel.TRAILING);
		portLabel.setPreferredSize(new Dimension(100, 5));
		portBox.add(portLabel);

		final JTextField portField = new JTextField(20);
		portField.setText(String.valueOf(hpcAccount.getPort()));
		portField.addKeyListener(new KeyListener() {

			@Override
			public void keyTyped(KeyEvent e) {
			}

			@Override
			public void keyReleased(KeyEvent e) {
				try {
					int port = Integer.parseInt(portField.getText());
					hpcAccount.setPort(port);
				} catch (NumberFormatException e1) {
					// TODO Auto-generated catch block
					if (portField.getText().trim().length() > 0) {
						JOptionPane.showMessageDialog(portField, "Port number is decimal number only!");
					}
				}
			}

			@Override
			public void keyPressed(KeyEvent e) {
			}
		});
		portLabel.setLabelFor(portField);
		portBox.add(portField);

		contentBox.add(portBox);

		JPanel contentPanel = new JPanel(new BorderLayout());
		contentPanel.add(contentBox, BorderLayout.NORTH);
		add(contentPanel, BorderLayout.CENTER);

		// Footer -> Test and Save buttons
		JPanel footerPanel = new JPanel(new BorderLayout());
		final JButton testConnButton = new JButton("Test Connection");
		testConnButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				JButton button = (JButton) e.getSource();
				button.setText("Testing...");
				parentComponent.updateUI();
				button.setEnabled(false);
				boolean success = HpcAccountUtils.testConnection(hpcAccountManager, hpcAccount);
				// Pop-up the test result
				JOptionPane.showMessageDialog(null,
						"" + hpcAccount + " Connection " + (success ? "Successful" : "Failed"), "HPC Account Setting",
						JOptionPane.INFORMATION_MESSAGE);
				button.setEnabled(true);
				button.setText("Test Connection");
				hpcAccount.setLastLoginDate(new Date());
				hpcAccountManager.saveAccount(hpcAccount);
			}
		});
		JButton saveButton = new JButton("Save");
		saveButton.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				JButton button = (JButton) e.getSource();
				button.setText("Saving...");
				parentComponent.updateUI();
				button.setEnabled(false);
				hpcAccountManager.saveAccount(hpcAccount);
				if (listModel.indexOf(hpcAccount) == -1) {
					listModel.addElement(hpcAccount);
				}
				button.setEnabled(true);
				button.setText("Save");
				parentComponent.updateUI();
			}
		});
		footerPanel.add(testConnButton, BorderLayout.WEST);
		footerPanel.add(saveButton, BorderLayout.EAST);
		add(footerPanel, BorderLayout.SOUTH);
	}

}
