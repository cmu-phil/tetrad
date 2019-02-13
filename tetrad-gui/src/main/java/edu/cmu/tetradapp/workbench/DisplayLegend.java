/**
 * 
 */
package edu.cmu.tetradapp.workbench;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import edu.cmu.tetrad.util.TetradSerializableExcluded;

/**
 * Jan 22, 2019 3:39:27 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class DisplayLegend extends JComponent implements TetradSerializableExcluded {

	private static final long serialVersionUID = 1L;
	
	private final Map<String, Object> attributes;
	
	public DisplayLegend(Map<String, Object> attributes) {
		this.attributes = attributes;
		initiateUI();
	}
	
	private void initiateUI() {
		setLayout(new BorderLayout());
		// Header
		JPanel headerPanel = new JPanel(new BorderLayout());
		JLabel headerLabel = new JLabel("Graph's Attribute(s)");
		headerLabel.setFont(new Font(headerLabel.getFont().getName(), Font.BOLD, 12));
		headerPanel.add(headerLabel, BorderLayout.CENTER);
		add(headerPanel, BorderLayout.NORTH);

		JPanel attributesPanel = new JPanel();
		attributesPanel.setLayout(new BoxLayout(attributesPanel, BoxLayout.PAGE_AXIS));
		
		for(String key : attributes.keySet()) {
			Object value = attributes.get(key);
			
			JLabel attributeLabel = new JLabel(key + ":\t" + value.toString());
			attributesPanel.add(attributeLabel);
		}
		
		add(attributesPanel, BorderLayout.CENTER);
		
		// Set the bounds of the display node.
        Dimension dim = new Dimension(150, 20 + 15*attributes.size());
        setSize(dim);
        
        setBorder(BorderFactory.createLineBorder(Color.red));
        
        revalidate();
        repaint();
	}

}
