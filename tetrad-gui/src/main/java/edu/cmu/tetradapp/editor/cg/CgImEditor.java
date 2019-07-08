/**
 * 
 */
package edu.cmu.tetradapp.editor.cg;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;

import edu.cmu.tetrad.graph.Graph;
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
