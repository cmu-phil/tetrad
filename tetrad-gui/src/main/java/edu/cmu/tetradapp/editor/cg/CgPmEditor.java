/**
 * 
 */
package edu.cmu.tetradapp.editor.cg;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.session.DelegatesEditing;
import edu.cmu.tetradapp.editor.SaveComponentImage;
import edu.cmu.tetradapp.model.CgPmWrapper;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

/**
 * Jun 20, 2019 3:40:42 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class CgPmEditor extends JPanel implements PropertyChangeListener, DelegatesEditing {

	private static final long serialVersionUID = 1L;

	private final JPanel targetPanel;
	private final CgPmWrapper wrapper;
	
	public CgPmEditor(final CgPmWrapper wrapper) {
		this.wrapper = wrapper;
		setLayout(new BorderLayout());
		
		targetPanel = new JPanel();
		targetPanel.setLayout(new BorderLayout());
		
		setEditorPanel();
		
		add(targetPanel, BorderLayout.CENTER);
        validate();
	}
	
	private void setEditorPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        if(wrapper.getCgPm().getGraph().getNumNodes() == 0) {
        	throw new IllegalArgumentException("There are no nodes in that Conditional Gaussian PM.");
        }
        
        setLayout(new BorderLayout());
        
        Graph graph = wrapper.getCgPm().getGraph();
        GraphWorkbench workbench = new GraphWorkbench(graph);
        workbench.enableEditing(false);
        CgPmEditorWizard wizard = new CgPmEditorWizard(wrapper.getCgPm(), workbench);
        
        JScrollPane workbenchScroll = new JScrollPane(workbench);
        JScrollPane wizardScroll = new JScrollPane(wizard);
        
        workbenchScroll.setPreferredSize(new Dimension(450, 450));
        wizardScroll.setPreferredSize(new Dimension(450, 450));
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                workbenchScroll, wizardScroll);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(workbenchScroll.getPreferredSize().width);
        panel.add(splitPane, BorderLayout.CENTER);

        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);
//        file.add(new SaveScreenshot(this, true, "Save Screenshot..."));
        file.add(new SaveComponentImage(workbench, "Save Graph Image..."));
        panel.add(menuBar, BorderLayout.NORTH);
        
        setName("Conditional Gaussian PM Editor");
        wizard.addPropertyChangeListener(this);
	}
	
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
