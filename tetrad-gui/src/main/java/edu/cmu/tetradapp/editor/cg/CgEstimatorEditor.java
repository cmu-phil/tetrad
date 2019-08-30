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
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetradapp.editor.SaveComponentImage;
import edu.cmu.tetradapp.model.CgEstimatorWrapper;
import edu.cmu.tetradapp.model.CgImWrapper;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import edu.pitt.dbmi.cg.CgIm;
import edu.pitt.dbmi.cg.CgPm;
import edu.pitt.dbmi.cg.CgProperties;

/**
 * Jul 22, 2019 3:16:37 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class CgEstimatorEditor extends JPanel {

	private static final long serialVersionUID = 1L;

	private JPanel targetPanel;
	/**
     * The wizard that allows the user to modify parameter values for this IM.
     */
	private CgEstimatorEditorWizard wizard;
	private CgEstimatorWrapper wrapper;
	
	/**
     * Constructs a new instantiated model editor from a Bayes IM.
     */
	public CgEstimatorEditor(CgIm cgIm, DataSet dataSet) {
		this(new CgEstimatorWrapper(new DataWrapper(dataSet), new CgImWrapper(cgIm)));
	}
	
	public CgEstimatorEditor(CgEstimatorWrapper wrapper) {
		this.wrapper = wrapper;
		
		setLayout(new BorderLayout());

        targetPanel = new JPanel();
        targetPanel.setLayout(new BorderLayout());
        
        resetCgImEditor();
        
        add(targetPanel, BorderLayout.CENTER);
        validate();

        if (wrapper.getNumModels() > 1) {
        	final JComboBox<Integer> comp = new JComboBox<>();

            for (int i = 0; i < wrapper.getNumModels(); i++) {
                comp.addItem(i + 1);
            }

            comp.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    wrapper.setModelIndex(((Integer) comp.getSelectedItem()).intValue() - 1);
                    resetCgImEditor();
                    validate();
                }
            });
            
            comp.setMaximumSize(comp.getPreferredSize());

            Box b = Box.createHorizontalBox();
            b.add(new JLabel("Using model"));
            b.add(comp);
            b.add(new JLabel("from "));
            b.add(new JLabel(wrapper.getName()));
            b.add(Box.createHorizontalGlue());

            add(b, BorderLayout.NORTH);
        }
        
	}
	
    /**
     * Sets the name of this editor.
     */
    public void setName(String name) {
        String oldName = getName();
        super.setName(name);
        this.firePropertyChange("name", oldName, getName());
    }

    /**
     * @return a reference to this editor.
     */
    private CgEstimatorEditorWizard getWizard() {
    	return wizard;
    }
    
    private void resetCgImEditor() {
    	JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        
        // Rest of setup
        CgIm cgIm = wrapper.getEstimatedCgIm();
        CgPm cgPm = cgIm.getCgPm();
        Graph graph = cgPm.getGraph();
        
        GraphWorkbench workbench = new GraphWorkbench(graph);
        wizard = new CgEstimatorEditorWizard(cgIm, workbench);
        wizard.enableEditing(false);

        wizard.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("editorValueChanged".equals(evt.getPropertyName())) {
                    firePropertyChange("modelChanged", null, null);
                }
            }
        });
        
        JScrollPane workbenchScroll = new JScrollPane(workbench);
        workbenchScroll.setPreferredSize(new Dimension(400, 400));

        JScrollPane wizardScroll = new JScrollPane(getWizard());

        // CG properties
        CgProperties properties = new CgProperties(wrapper.getDataSet(), graph);
        
        StringBuilder buf = new StringBuilder();
        buf.append("\nDf = ").append(properties.getDof());
        buf.append("\n\nH0: Complete graph.");
        
        JTextArea modelParametersText = new JTextArea();
        modelParametersText.setText(buf.toString());
        
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Model", wizardScroll);
        tabbedPane.add("Model Statistics", modelParametersText);
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                workbenchScroll, tabbedPane);
        splitPane.setOneTouchExpandable(true);
        splitPane.setDividerLocation(workbenchScroll.getPreferredSize().width);

        setLayout(new BorderLayout());
        panel.add(splitPane, BorderLayout.CENTER);

        setName("CG IM Editor");
        getWizard().addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                if ("editorClosing".equals(evt.getPropertyName())) {
                    firePropertyChange("editorClosing", null, getName());
                }

                if ("closeFrame".equals(evt.getPropertyName())) {
                    firePropertyChange("closeFrame", null, null);
                    firePropertyChange("editorClosing", true, true);
                }
            }
        });
        
        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);
        file.add(new SaveComponentImage(workbench, "Save Graph Image..."));
        panel.add(menuBar, BorderLayout.NORTH);

        targetPanel.add(panel, BorderLayout.CENTER);
        validate();
    }
}
