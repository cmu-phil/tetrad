package edu.cmu.tetradapp.app.hpc;

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

import edu.pitt.dbmi.tetrad.db.entity.ComputingAccount;

/**
 * 
 * Oct 31, 2016 1:50:37 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class ComputingAccountEditor extends JPanel {

    private static final long serialVersionUID = -3028667139958773226L;

    private final JComponent parentComponent;
    
    private final DefaultListModel<ComputingAccount> listModel;

    private final ComputingAccountManager manager;

    private final ComputingAccount computingAccount;

    public ComputingAccountEditor(
	    final JComponent parentComponent,
	    final DefaultListModel<ComputingAccount> listModel,
	    final ComputingAccountManager manager,
	    final ComputingAccount computingAccount) {
	this.parentComponent = parentComponent;
	this.listModel = listModel;
	this.manager = manager;
	this.computingAccount = computingAccount;
	initiateUI();
    }

    private void initiateUI() {
	setLayout(new BorderLayout());

	// Header
	JPanel headerPanel = new JPanel(new BorderLayout());
	JLabel headerLabel = new JLabel("High-Performance Computing Account");
	headerLabel.setFont(new Font(headerLabel.getFont().getName(),
		Font.BOLD, 20));
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
	connField.setText(computingAccount.getConnectionName());
	connField.addKeyListener(new KeyListener() {

	    @Override
	    public void keyTyped(KeyEvent e) {
	    }

	    @Override
	    public void keyReleased(KeyEvent e) {
		computingAccount.setConnectionName(connField.getText());
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
	userField.setText(computingAccount.getUsername());
	userField.addKeyListener(new KeyListener() {

	    @Override
	    public void keyTyped(KeyEvent e) {
	    }

	    @Override
	    public void keyReleased(KeyEvent e) {
		computingAccount.setUsername(userField.getText());
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
	passField.setText(computingAccount.getPassword());
	passField.addKeyListener(new KeyListener() {

	    @Override
	    public void keyTyped(KeyEvent e) {
	    }

	    @Override
	    public void keyReleased(KeyEvent e) {
		computingAccount.setPassword(new String(passField.getPassword()));
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
	if (computingAccount.getScheme().equalsIgnoreCase("https")) {
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
		    computingAccount.setScheme("http");
		} else {
		    computingAccount.setScheme("https");
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
	hostField.setText(computingAccount.getHostname());
	hostField.addKeyListener(new KeyListener() {

	    @Override
	    public void keyTyped(KeyEvent e) {
	    }

	    @Override
	    public void keyReleased(KeyEvent e) {
		computingAccount.setHostname(hostField.getText());
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
	portField.setText(String.valueOf(computingAccount.getPort()));
	portField.addKeyListener(new KeyListener() {

	    @Override
	    public void keyTyped(KeyEvent e) {
	    }

	    @Override
	    public void keyReleased(KeyEvent e) {
		try {
		    int port = Integer.parseInt(portField.getText());
		    computingAccount.setPort(port);
		} catch (NumberFormatException e1) {
		    // TODO Auto-generated catch block
		    if (portField.getText().trim().length() > 0) {
			JOptionPane.showMessageDialog(portField,
				"Port number is decimal number only!");
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
		boolean success = ComputingAccountUtils
			.testConnection(computingAccount);
		// Pop-up the test result
		JOptionPane.showMessageDialog(null, ""
			+ computingAccount + " Connection "
			+ (success ? "Successful" : "Failed"),
			"HPC Account Setting", JOptionPane.INFORMATION_MESSAGE);
		button.setEnabled(true);
		button.setText("Test Connection");
		computingAccount.setLastLoginDate(new Date());
		manager.saveAccount(computingAccount);
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
		manager.saveAccount(computingAccount);
		if (listModel.indexOf(computingAccount) == -1) {
		    listModel.addElement(computingAccount);
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
