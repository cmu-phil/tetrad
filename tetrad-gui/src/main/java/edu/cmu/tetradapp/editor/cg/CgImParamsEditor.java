/**
 * 
 */
package edu.cmu.tetradapp.editor.cg;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.ParameterEditor;
import edu.cmu.tetradapp.util.DoubleTextField;

/**
 * Jul 11, 2019 1:38:17 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class CgImParamsEditor extends JPanel implements ParameterEditor {

	private static final long serialVersionUID = 1L;
	
	/**
     * The parameters object being edited.
     */
    private Parameters params = null;


	@Override
	public void setParams(Parameters params) {
        if (params == null) {
            throw new NullPointerException();
        }

        this.params = params;
	}

	@Override
	public void setParentModels(Object[] parentModels) {
		// Do nothing.
	}

	@Override
    /**
     * Constructs the Gui used to edit properties; called from each constructor.
     * Constructs labels and text fields for editing each property and adds
     * appropriate listeners.
     */
	public void setup() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        add(createBayesImParamsEditor());
        add(createSemImParamsEditor());
	}
	
	private JPanel createSemImParamsEditor() {
		String title = "SEM Parameters";
		JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
    
        DecimalFormat decimalFormat = new DecimalFormat("0.0######");

        final DoubleTextField coefLowField = new DoubleTextField(params.getDouble("coefLow", 0.5),
                6, decimalFormat);

        coefLowField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    getParams().set("coefLow", value);
                    getParams().set("coefHigh", params.getDouble("coefHigh", 1.5));
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });


        final DoubleTextField coefHighField = new DoubleTextField(params.getDouble("coefHigh", 1.5),
                6, decimalFormat);

        coefHighField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    getParams().set("coefLow", params.getDouble("coefLow", 0.5));
                    getParams().set("coefHigh", value);
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        final DoubleTextField covLowField = new DoubleTextField(params.getDouble("covLow", 0.0),
                6, decimalFormat);

        covLowField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    params.set("covLow", value);
                    params.set("covHigh", params.getDouble("covHigh", 0.2));
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        final DoubleTextField covHighField = new DoubleTextField(params.getDouble("covHigh", 0.0),
                6, decimalFormat);

        covHighField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    params.set("covLow", params.getDouble("covLow", 0.1));
                    params.set("covHigh", value);
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        final DoubleTextField varLowField = new DoubleTextField(params.getDouble("varLow", 1),
                6, decimalFormat);

        varLowField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    params.set("varLow", value);
                    params.set("varHigh", params.getDouble("varHigh", 3));
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        final DoubleTextField varHighField = new DoubleTextField(params.getDouble("varHigh", 3),
                6, decimalFormat);

        varHighField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    params.set("varLow", params.getDouble("varLow", 1));
                    params.set("varHigh", value);
                    return value;
                }
                catch (IllegalArgumentException e) {
                    return oldValue;
                }
            }
        });

        final JCheckBox coefSymmetric = new JCheckBox("Symmetric about zero.");
        final JCheckBox covSymmetric = new JCheckBox("Symmetric about zero.");

        coefSymmetric.setSelected(params.getBoolean("coefSymmetric", true));
        covSymmetric.setSelected(params.getBoolean("covSymmetric", true));

        coefSymmetric.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox checkBox = (JCheckBox) e.getSource();
                params.set("coefSymmetric", checkBox.isSelected());
            }

        });

        covSymmetric.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JCheckBox checkBox = (JCheckBox) e.getSource();
                params.set("covSymmetric", checkBox.isSelected());
            }
        });
        
        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel(
                "Unfixed parameter values for this SEM IM are drawn as follows:"));
        b2.add(Box.createHorizontalGlue());
        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));

        Box b4a = Box.createHorizontalBox();
        b4a.add(new JLabel("Coefficient values are drawn from "));
        b4a.add(new BigLabel("("));
        b4a.add(coefLowField);
        b4a.add(new BigLabel(", "));
        b4a.add(coefHighField);
        b4a.add(new BigLabel(") "));
        b4a.add(coefSymmetric);
        b4a.add(Box.createHorizontalGlue());
        b1.add(b4a);

        Box b4b = Box.createHorizontalBox();
        b4b.add(new JLabel("Error covariance values are drawn from "));
        b4b.add(new BigLabel("("));
        b4b.add(covLowField);
        b4b.add(new BigLabel(", "));
        b4b.add(covHighField);
        b4b.add(new BigLabel(") "));
        b4b.add(covSymmetric);
        b4b.add(Box.createHorizontalGlue());
        b1.add(b4b);

        Box b4c = Box.createHorizontalBox();
        b4c.add(new JLabel("Error standard deviation values are drawn from "));
        b4c.add(new BigLabel("("));
        b4c.add(varLowField);
        b4c.add(new BigLabel(", "));
        b4c.add(varHighField);
        b4c.add(new BigLabel(")"));
        b4c.add(new JLabel("."));
        b4c.add(Box.createHorizontalGlue());
        b1.add(b4c);

        b1.add(Box.createHorizontalGlue());

        panel.add(b1, BorderLayout.CENTER);
        
        return panel;
	}
	
	private JPanel createBayesImParamsEditor() {
		String title = "Bayes Parameters";
		JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        
        JRadioButton manually = new JRadioButton();
        final JRadioButton randomly = new JRadioButton();

        manually.setText("Manually.");
        randomly.setText("Randomly.");

        ButtonGroup group = new ButtonGroup();
        group.add(manually);
        group.add(randomly);

        if (getParams().getString("initializationMode", "manualRetain").equals("manualRetain")) {
            manually.setSelected(true);
        } else if (getParams().getString("initializationMode", "manualRetain").equals("randomRetain")) {
            randomly.setSelected(true);
        } else if (getParams().getString("initializationMode", "manualRetain").equals("randomOverwrite")) {
            randomly.setSelected(true);
        } else {
            throw new IllegalStateException();
        }

        manually.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getParams().set("initializationMode", "manualRetain");
                firePropertyChange("modelChanged", null, null);
            }
        });

        randomly.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                getParams().set("initializationMode", "randomRetain");
                firePropertyChange("modelChanged", null, null);
            }
        });

        // continue workbench construction.
        Box b1 = Box.createVerticalBox();

        Box b2 = Box.createHorizontalBox();
        b2.add(new JLabel(
                "Probability values for this Conditional Gaussian IM should be filled in: "));
        b2.add(Box.createHorizontalGlue());

        Box b3 = Box.createHorizontalBox();
        b3.add(manually);
        b3.add(Box.createHorizontalGlue());

        Box b4 = Box.createHorizontalBox();
        b4.add(randomly);
        b4.add(Box.createHorizontalGlue());

        b1.add(b2);
        b1.add(Box.createVerticalStrut(5));
        b1.add(b3);
        b1.add(b4);
        b1.add(Box.createHorizontalGlue());

        panel.add(b1, BorderLayout.CENTER);
        
        return panel;
	}

	@Override
	public boolean mustBeShown() {
		return false;
	}
	
    /**
     * Returns the getMappings object being edited. (This probably should not be
     * public, but it is needed so that the textfields can edit the model.)
     *
     * @return the stored simulation parameters model.
     */
    private synchronized Parameters getParams() {
        return this.params;
    }
    
    private final static class BigLabel extends JLabel {

		private static final long serialVersionUID = 1L;
		
		private static final Font FONT = new Font("Dialog", Font.BOLD, 20);

        public BigLabel(String text) {
            super(text);
            setFont(FONT);
        }
    }
}
