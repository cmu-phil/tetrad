/**
 * 
 */
package edu.cmu.tetradapp.editor.cg;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.ParamType;
import edu.cmu.tetrad.sem.Parameter;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.StringTextField;
import edu.pitt.dbmi.cg.CgIm;
import edu.pitt.dbmi.cg.CgPm;

/**
 * Jun 27, 2019 4:30:52 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class CgSemParameterEditor extends JPanel {

	private static final long serialVersionUID = 1L;

	private CgIm cgIm = null;
	private CgPm cgPm = null;
	private Node node;
	
	public CgSemParameterEditor(CgIm cgIm, Node node) {
		if (cgIm == null) {
            throw new NullPointerException();
        }

        if (node == null) {
            throw new NullPointerException();
        }

        this.cgIm = cgIm;
        this.cgPm = cgIm.getCgPm();
        this.node = node;
        
        setLayout(new BorderLayout());

        createEditor();
	}
	
	public CgSemParameterEditor(CgPm cgPm, Node node) {
		if (cgPm == null) {
            throw new NullPointerException();
        }

        setLayout(new BorderLayout());

        if (node == null) {
            throw new NullPointerException();
        }

        this.cgPm = cgPm;
        this.node = node;
        
        createEditor();
	}
	
	private void createEditor() {
		removeAll();
		
		Box b1 = Box.createVerticalBox();
		
		SemPm semPm = cgPm.getSemPm();
		
    	// Parameters
    	final Parameter meanParam = semPm.getMeanParameter(semPm.getGraph().getNode(node.getName()));
    	final Parameter errVarParam = semPm.getVarianceParameter(semPm.getGraph().getNode(node.getName()));

    	Box meanPanel = createSemParameterBox(meanParam);
    	Box errVarPanel = createSemParameterBox(errVarParam);

    	b1.add(meanPanel);
        b1.add(Box.createVerticalStrut(10));

    	b1.add(errVarPanel);
        b1.add(Box.createVerticalStrut(10));
    	
    	// Linear Coefficient
        for(Node parentNode : semPm.getGraph().getParents(node)) {
        	//System.out.println("Coeficient with Parent Node: " + parentNode);
        	
        	Box parentNodeNameBox = Box.createHorizontalBox();
        	parentNodeNameBox.add(Box.createHorizontalGlue());
        	
        	
        	final Parameter coefParam = semPm.getCoefficientParameter(parentNode, semPm.getGraph().getNode(node.getName()));
        	
        	Box coefPanel = createSemParameterBox(coefParam);
        	
        	b1.add(parentNodeNameBox);
        	b1.add(coefPanel);
        	b1.add(Box.createVerticalStrut(10));
        }

        b1.setBorder(new EmptyBorder(10, 10, 0, 10));

        setLayout(new BorderLayout());
        add(b1, BorderLayout.CENTER);
	}
	
	private Box createSemParameterBox(final Parameter parameter) {
		int length = 8;
    	
    	String paramType = "" + parameter.getType();
    	
    	if (cgIm != null && parameter.getType() == ParamType.VAR) {
    		paramType = "Standard Deviation";
    	}
    	
    	String nameLabel = paramType + ": ";
    	if (cgIm != null) {
    		nameLabel += "" + node;
    	} else if (parameter.getType() == ParamType.COEF) {
    		Node parentNode = parameter.getNodeA();
    		nameLabel += parentNode + " -> " + node;
    	}
    	
    	Box b1 = Box.createVerticalBox();
    	
    	Box nameBox = Box.createHorizontalBox();  
    	nameBox.setBorder(BorderFactory.createLineBorder(Color.black));
    	nameBox.add(new JLabel(nameLabel));
    	nameBox.add(Box.createHorizontalGlue());

    	if (cgIm != null) {
        	// Starting Value
        	double startingValue;
        	
    		if(parameter.getType() == ParamType.MEAN) {
    			startingValue = cgIm.getSemIm().getMean(node);
    		} else if (parameter.getType() == ParamType.COEF) {
    			startingValue = cgIm.getSemIm().getEdgeCoef(parameter.getNodeA(), parameter.getNodeB());
    		} else {
    			startingValue = Math.sqrt(cgIm.getSemIm().getParamValue(parameter));
    		}
        	
            final DoubleTextField startValueField = new DoubleTextField(startingValue, length, 
            		NumberFormatUtil.getInstance().getNumberFormat());
            startValueField.setFilter(new DoubleTextField.Filter() {
    			
    			public double filter(double value, double oldValue) {
    				try {
    					if(cgIm != null) {
    						double newValue = new Double(value);
    						if(parameter.getType() == ParamType.MEAN) {
    							cgIm.getSemIm().setMean(node, newValue);
    						} else if (parameter.getType() == ParamType.COEF) {
    							cgIm.getSemIm().setEdgeCoef(parameter.getNodeA(), parameter.getNodeB(), newValue);
    						} else if (newValue >= 0) {
    							cgIm.getSemIm().setParamValue(node, node, newValue*newValue);
    						}
    					} else {
    						parameter.setStartingValue(value);
    					}
    			
    					firePropertyChange("modelChanged", null, null);
    					
    					return value;
    				}catch(Exception e) {
    					return oldValue;
    				}
    			}
    		});
            
            nameBox.add(startValueField);
            
            b1.add(nameBox);
    	} else {
        	final StringTextField nameField = new StringTextField(parameter.getName(), length);
        	nameField.setHorizontalAlignment(JTextField.RIGHT);
            nameField.grabFocus();
            nameField.selectAll();
            
        	nameBox.add(nameField);

        	b1.add(nameBox);

        	nameField.setFilter(new StringTextField.Filter() {
                public String filter(String value, String oldValue) {
                    try {
                    	SemPm semPm = cgPm.getSemPm();
                    	
                        Parameter paramForName
                                = semPm.getParameter(value);

                        // Ignore if paramName already exists.
                        if (paramForName == null
                                && !value.equals(parameter.getName())) {
                        	parameter.setName(value);
                        }

                        return parameter.getName();
                    } catch (IllegalArgumentException e) {
                        return parameter.getName();
                    } catch (Exception e) {
                        return parameter.getName();
                    }
                }
            });
    		
        	boolean fixed4Estimation = parameter.isFixed();
        	
        	JCheckBox checkbox = new JCheckBox() {
                
    			private static final long serialVersionUID = 1L;

    			public Dimension getMaximumSize() {
                    return getPreferredSize();
                }
            };
            checkbox.setSelected(fixed4Estimation);
            checkbox.addActionListener(new ActionListener() {

    			public void actionPerformed(ActionEvent e) {
    				JCheckBox checkbox = (JCheckBox) e.getSource();
                    boolean selected = checkbox.isSelected();
                    parameter.setFixed(selected);
    			}
    		
            });
            
        	// Fixed for estimation
        	Box isFixedEstimationBox = Box.createHorizontalBox();
        	isFixedEstimationBox.add(new JLabel(paramType + " Fixed for Estimation?"));
        	isFixedEstimationBox.add(Box.createHorizontalGlue());
            isFixedEstimationBox.add(checkbox);
            
            b1.add(isFixedEstimationBox);
            
        	// Starting Value
        	double startingValue = parameter.getStartingValue();

            final DoubleTextField startValueField = new DoubleTextField(startingValue, length, 
            		NumberFormatUtil.getInstance().getNumberFormat());
            startValueField.setFilter(new DoubleTextField.Filter() {
    			
    			public double filter(double value, double oldValue) {
    				try {
    					if(cgIm != null) {
    						double newValue = new Double(value);
    						if(parameter.getType() == ParamType.MEAN) {
    							cgIm.getSemIm().setMean(node, newValue);
    						} else if (parameter.getType() == ParamType.COEF) {
    							cgIm.getSemIm().setEdgeCoef(parameter.getNodeA(), parameter.getNodeB(), newValue);
    						} else if (newValue >= 0) {
    							cgIm.getSemIm().setParamValue(node, node, newValue*newValue);
    						}
    					} else {
    						parameter.setStartingValue(value);
    					}
    			
    					firePropertyChange("modelChanged", null, null);
    					
    					return value;
    				}catch(Exception e) {
    					return oldValue;
    				}
    			}
    		});

            boolean initRandomly = parameter.isInitializedRandomly();

            startValueField.setEditable(!initRandomly);
        	
            JRadioButton randomRadioButton = new JRadioButton("Drawn mean randomly");
            randomRadioButton.addActionListener(new ActionListener() {
    			
    			public void actionPerformed(ActionEvent e) {
    				parameter.setInitializedRandomly(true);
    				startValueField.setEditable(false);
    			}
    		});
            
            JRadioButton startValueRadioButton = new JRadioButton();
            startValueRadioButton.addActionListener(new ActionListener() {
            	
            	public void actionPerformed(ActionEvent e) {
            		parameter.setInitializedRandomly(false);
    				startValueField.setEditable(true);
            	}
            });
            
            ButtonGroup buttonInitValueGroup = new ButtonGroup();
            buttonInitValueGroup.add(randomRadioButton);
            buttonInitValueGroup.add(startValueRadioButton);
            
            if(initRandomly) {
            	buttonInitValueGroup.setSelected(randomRadioButton.getModel(), true);
            } else {
            	buttonInitValueGroup.setSelected(startValueRadioButton.getModel(), true);
            }
            
            // Starting Value for Estimation
            Box startingValueHeaderBox = Box.createHorizontalBox();
            startingValueHeaderBox.add(new JLabel(paramType + " Starting Value for Estimation:"));
            startingValueHeaderBox.add(Box.createHorizontalGlue());
            b1.add(startingValueHeaderBox);
            
            Box randomRadioBox = Box.createHorizontalBox();
            randomRadioBox.add(Box.createHorizontalStrut(10));
            randomRadioBox.add(randomRadioButton);
            randomRadioBox.add(Box.createHorizontalGlue());
            
            b1.add(randomRadioBox);
            
            Box startValueRadioBox = Box.createHorizontalBox();
            startValueRadioBox.add(Box.createHorizontalStrut(10));
            startValueRadioBox.add(startValueRadioButton);
            startValueRadioBox.add(new JLabel("Set to: "));
            startValueRadioBox.add(Box.createHorizontalGlue());
            startValueRadioBox.add(startValueField);
            
            b1.add(startValueRadioBox);
    	}
		
		return b1;
	}

	public Node getNode() {
        return node;
    }
	
	public void setNode(Node node) {
		this.node = node;
		createEditor();
		revalidate();
        repaint();
        firePropertyChange("modelChanged", null, null);
	}
	
}
