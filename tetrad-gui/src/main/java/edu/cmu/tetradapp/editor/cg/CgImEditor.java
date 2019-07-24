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
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetradapp.editor.SaveComponentImage;
import edu.cmu.tetradapp.editor.SaveScreenshot;
import edu.cmu.tetradapp.model.CgImWrapper;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.pitt.dbmi.cg.CgIm;

/**
 * Jul 2, 2019 2:50:52 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class CgImEditor extends JPanel {

	private static final long serialVersionUID = 1L;

	private JPanel targetPanel;
	
	private CgImEditorWizard wizard;
	
	private CgImWrapper wrapper;
	
	/**
     * Constructs a new instantiated model editor from a CG IM.
     */
	public CgImEditor(final CgImWrapper wrapper) {
		this.wrapper = wrapper;
		setLayout(new BorderLayout());
		
		targetPanel = new JPanel();
		targetPanel.setLayout(new BorderLayout());
		
		setEditorPanel();
		
		add(targetPanel, BorderLayout.CENTER);
		validate();
		
		if (wrapper.getNumModels() > 1) {
            final JComboBox<Integer> comp = new JComboBox<>();

            for (int i = 0; i < wrapper.getNumModels(); i++) {
                comp.addItem(i + 1);
            }

            comp.setSelectedIndex(wrapper.getModelIndex());

            comp.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    wrapper.setModelIndex(comp.getSelectedIndex() - 1);
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

        CgIm cgIm = wrapper.getCgIm();
        Graph graph = cgIm.getDag();
        
        GraphUtils.circleLayout(graph, 225, 225, 150);
        
        GraphWorkbench workbench = new GraphWorkbench(graph);

        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);
        file.add(new SaveScreenshot(this, true, "Save Screenshot..."));
        file.add(new SaveComponentImage(workbench, "Save Graph Image..."));
        setLayout(new BorderLayout());
        panel.add(menuBar, BorderLayout.NORTH);
        
        wizard = new CgImEditorWizard(cgIm, workbench);
        wizard.enableEditing(false);
        
        wizard.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("editorValueChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("modelChanged", null, null);
                }
            }
        });
        
        JScrollPane workbenchScroll = new JScrollPane(workbench);
        JScrollPane wizardScroll = new JScrollPane(getWizard());
        
        workbenchScroll.setPreferredSize(new Dimension(450, 450));
        wizardScroll.setPreferredSize(new Dimension(450, 450));
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                workbenchScroll, wizardScroll);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(workbenchScroll.getPreferredSize().width);
        panel.add(splitPane, BorderLayout.CENTER);
        
        setName("Conditional Gaussian IM Editor");
        getWizard().addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("editorClosing".equals(evt.getPropertyName())) {
                    firePropertyChange("editorClosing", null, getName());
                }

                if ("closeFrame".equals(evt.getPropertyName())) {
                    firePropertyChange("closeFrame", null, null);
                    firePropertyChange("editorClosing", true, true);
                }

                if ("modelChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("modelChanged", evt.getOldValue(),
                            evt.getNewValue());
                }
            }
        });

        targetPanel.add(panel, BorderLayout.CENTER);
        revalidate();
        repaint();
	}

    /**
     * Sets the name of this editor.
     */
    public void setName(String name) {
        String oldName = getName();
        super.setName(name);
        firePropertyChange("name", oldName, getName());
    }

    /**
     * @return a reference to this editor.
     */
	public CgImEditorWizard getWizard() {
		return this.wizard;
	}

	public void getCgIm(CgIm cgIm) {
		removeAll();
        setEditorPanel();
        revalidate();
        repaint();
        firePropertyChange("modelChanged", null, null);
	}
	
}
