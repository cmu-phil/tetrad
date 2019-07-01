/**
 * 
 */
package edu.cmu.tetradapp.editor.cg;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
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
    /**
     * True iff the editing of measured variables is allowed.
     */
    private boolean editingMeasuredVariablesAllowed = true;

    /**
     * True iff the editing of latent variables is allowed.
     */
    private boolean editingLatentVariablesAllowed = true;

    /**
     * The wizard that lets the user edit values.
     */
    private CgPmEditorWizard wizard;
    
	public CgPmEditor(final CgPmWrapper wrapper) {
		this.wrapper = wrapper;
		setLayout(new BorderLayout());
		
		targetPanel = new JPanel();
		targetPanel.setLayout(new BorderLayout());
		
		setEditorPanel();
		
		add(targetPanel, BorderLayout.CENTER);
        validate();
        
        if(wrapper.getNumModels() > 1) {
        	final JComboBox<Integer> comp = new JComboBox<>();

            for (int i = 0; i < wrapper.getNumModels(); i++) {
                comp.addItem(i + 1);
            }

            comp.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    wrapper.setModelIndex(((Integer) comp.getSelectedItem()).intValue() - 1);
                    setEditorPanel();
                    validate();
                }
            });
            
            comp.setMaximumSize(comp.getPreferredSize());

            Box b = Box.createHorizontalBox();
            b.add(new JLabel("Using model"));
            b.add(comp);
            b.add(new JLabel("from "));
            b.add(new JLabel(wrapper.getModelSourceName()));
            b.add(Box.createHorizontalGlue());

            add(b, BorderLayout.NORTH);
        }
        
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
        
        wizard.setEditingLatentVariablesAllowed(isEditingLatentVariablesAllowed());
        wizard.setEditingMeasuredVariablesAllowed(isEditingMeasuredVariablesAllowed());
        
        this.wizard = wizard;
        
        targetPanel.add(panel, BorderLayout.CENTER);
	}
	
    /**
     * True iff the editing of measured variables is allowed.
     */
    private boolean isEditingMeasuredVariablesAllowed() {
        return editingMeasuredVariablesAllowed;
    }

    /**
     * True iff the editing of measured variables is allowed.
     */
    public void setEditingMeasuredVariablesAllowed(boolean editingMeasuredVariablesAllowed) {
        this.editingMeasuredVariablesAllowed = editingMeasuredVariablesAllowed;
        wizard.setEditingMeasuredVariablesAllowed(isEditingMeasuredVariablesAllowed());
    }

    /**
     * True iff the editing of latent variables is allowed.
     */
    private boolean isEditingLatentVariablesAllowed() {
        return editingLatentVariablesAllowed;
    }

    /**
     * True iff the editing of latent variables is allowed.
     */
    public void setEditingLatentVariablesAllowed(boolean editingLatentVariablesAllowed) {
        this.editingLatentVariablesAllowed = editingLatentVariablesAllowed;
        wizard.setEditingLatentVariablesAllowed(isEditingLatentVariablesAllowed());
    }

	@Override
	public JComponent getEditDelegate() {
		return wizard;
	}

	@Override
	public void propertyChange(PropertyChangeEvent e) {
        if ("editorClosing".equals(e.getPropertyName())) {
            firePropertyChange("editorClosing", null, getName());
        }

        if ("closeFrame".equals(e.getPropertyName())) {
            firePropertyChange("closeFrame", null, null);
        }

        if ("modelChanged".equals(e.getPropertyName())) {
            firePropertyChange("modelChanged", e.getOldValue(),
                    e.getNewValue());
        }
	}
	
    /**
     * Sets the name for the CG PM.
     */
    public void setName(String name) {
        String oldName = getName();
        super.setName(name);
        firePropertyChange("name", oldName, getName());
    }

}
