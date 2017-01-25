package edu.cmu.tetradapp.app.hpc;

import javax.swing.JInternalFrame;

import edu.cmu.tetradapp.app.TetradDesktop;
import edu.cmu.tetradapp.app.TetradInternalFrame;

/**
 * 
 * Jan 25, 2017 1:12:20 PM
 * 
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 * 
 */
public class HpcJobActivityFrame extends TetradInternalFrame {

    /**
     * 
     */
    private static final long serialVersionUID = -5165988861160079610L;

    private static final String TITLE = "HPC Job Activity";

    private final TetradDesktop desktop;
    
    /**
     * @param title
     */
    public HpcJobActivityFrame(final TetradDesktop desktop) {
	super(TITLE);
	this.desktop = desktop;
	
	buildUI();
    }

    private void buildUI(){
	
    }
    
}
