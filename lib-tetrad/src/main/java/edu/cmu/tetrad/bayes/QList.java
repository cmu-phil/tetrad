///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.bayes;

/**
 * Q-factors, for use in Identifiability
 *
 * @author Choh Man Teng
 */
public final class QList {

	int nVariables; // number of nodes
	
	boolean inNumerator;   // true: in the numerator; false: in the denominator
	int[] sumOverVariables; // the variables to be summed over in the summation

	// only one of probTerm and subList should be instantiated
	int[] probTerm;  // probability term
	QList subList;  // recursive list to be summed over sumOverVariables
	
	QList nextTerm;  // next term in the list
	
	private boolean debug = false;
	
    //=============================CONSTRUCTORS=========================//
	
	/////////////////////////////////////////////////////////////////
	// intialize an empty QList: Q(emptyset) = 1
	public QList(int nVariables)
	{
		this.nVariables = nVariables;
		initialize();
	}
	
	/////////////////////////////////////////////////////////////////
	// initialize with a probability term
	public QList(int nVariables, int[] probTerm, boolean inNumerator)
	{
		this.nVariables = nVariables;
		initialize();

		for (int i = 0; i < nVariables; i++)
		{
			this.probTerm[i] = probTerm[i];
		}
		
		this.inNumerator = inNumerator;
	}

	/////////////////////////////////////////////////////////////////
	// copy an existing QList
	//
	public QList(QList q)
	{
		this.nVariables = q.nVariables;
		initialize();
		
		for (int i = 0; i < nVariables; i++)
		{
			sumOverVariables[i] = q.sumOverVariables[i];
			probTerm[i] = q.probTerm[i];
		}
		
		inNumerator = q.inNumerator;
		
		if (q.subList != null)
		{
			subList = new QList(q.subList);
		}
		
		if (q.nextTerm != null)
		{
			nextTerm = new QList(q.nextTerm);
		}
		
	}
	
    //==========================PUBLIC METHODS==========================//

	
	/////////////////////////////////////////////////////////////////
	// append a term to the end
	//
	public void add(QList q, int[] sumOverVariables, boolean inNumerator)
	{
		QList qAdd = new QList(nVariables);
		
		qAdd.initialize();
		
		qAdd.inNumerator = inNumerator;
		for (int i = 0; i < nVariables; i++)
		{
			qAdd.sumOverVariables[i] = sumOverVariables[i];
		}
		qAdd.subList = new QList(q);
		
		// append qAdd 
		if (this.nextTerm == null)
		{
			this.nextTerm = new QList(qAdd);
		}
		else 
		{
			QList curTerm = this.nextTerm;
			while (curTerm.nextTerm != null)
			{
				curTerm = curTerm.nextTerm;
			}
			curTerm.nextTerm = new QList(qAdd);
		}
	}
	
	/////////////////////////////////////////////////////////////////
	// print
	// index1: the numbering of terms in the linked list
	// index2: the depth of recursive sublists
	//
	public void printQList(int index1, int index2)
	{
		System.out.println("======= " + index1 + "  " + index2 + 
						   ": printQList");
		System.out.println("inNumerator: " + inNumerator);
		
		System.out.print("sumOverVariables: ");
		for (int i = 0; i < nVariables; i++)
		{
			System.out.print(sumOverVariables[i] + "  ");
		}
		System.out.println();
		
		if (subList == null)    // probTerm is instantiated
		{
			System.out.print("probTerm: ");
			for (int i = 0; i < nVariables; i++)
			{
				System.out.print(probTerm[i] + "  ");
			}
			System.out.println();
		}
		else  
		// subList != null
		// increment index1 and reset index2 to 0 
		{
			System.out.println("--------------------------------------- " 
							   + (index1 + 1) + "  " + 0 + 
							   ": subList");
			subList.printQList(index1 + 1, 0);
		}
		
		if (nextTerm != null)
		{
			index2++;
			System.out.println("---------------------------- " 
							   + index1 + "  " + index2 + 
							   ": nextTerm");
			nextTerm.printQList(index1, index2);
		}
	}

	/////////////////////////////////////////////////////////////////
	// compute the numeric value of the expression in qPTS
	// by using the RowSummingExactUpdater
	//
	public double computeValue(BayesIm bayesIm, int[] fixedVarValues)
	{
		double resultAll = 0.0;  
		double resultOneConfig = 0.0;  
		double resultNextTerm = 1.0; 
		
		// updater with no evidence
        //ManipulatingBayesUpdater rowSumUpdater = 
		//	new RowSummingExactUpdater(bayesIm, Evidence.tautology(bayesIm));
		
		int nNodes = bayesIm.getNumNodes();

		int[] loopVarValues = new int[nNodes];
		
		if (debug)
		{
			System.out.print("\n***************** fixedVarValues:   ");
			for (int i = 0; i < nNodes; i++)
			{
				System.out.print(fixedVarValues[i] + "  ");
			}
			System.out.println(" *****************");			
			
			System.out.print("***************** sumOverVariables: ");
			for (int i = 0; i < nNodes; i++)
			{
				System.out.print(sumOverVariables[i] + "  ");
			}
			System.out.println(" *****************");			
		}
		
		// starting loop variable configuration
		for (int i = 0; i < nNodes; i++)
		{
			// inherit values from fixedVarValues
			loopVarValues[i] = fixedVarValues[i];
			
			// reset the values of the variables to be summed
			if (sumOverVariables[i] == 1)
			{
				// Set node i to its first value
				// It is possible (and legal) to overwrite given values 
				// in S and T (and fixedVarValues)
				loopVarValues[i] = 0;  
			}
		}

		// next variable to be incremented
		int curVar = nNodes - 1;

		while (curVar >= 0)
		{
			if (debug)
			{
				System.out.print("entering while loop: curVar: " + curVar + "; loopVarValues: ");
				for (int i = 0; i < nNodes; i++)
				{
					System.out.print(loopVarValues[i] + "  ");
				}
				System.out.println();
			}
		
			// one iteration of computing with this variable value 
			// configuration

			if (subList == null)   // probTerm is instantiated
			{
				if (debug)
				{
					System.out.print("---------- probTerm: ");
					for (int i = 0; i < nNodes; i++)
					{
						System.out.print(probTerm[i] + "  ");
					}
					System.out.println();			
				}
				
				// the number of variables represented in probTerm
				int nVarInMarginal = 0;
				for (int i = 0; i < nNodes; i++)
				{
					if (probTerm[i] == 1) 
					{
						nVarInMarginal++;
					}
				}
				
				int[] pVar = new int[nVarInMarginal];
				int[] pValues = new int[nVarInMarginal];
				int pIndex = 0;
				for (int i = 0; i < nNodes; i++)
				{
					if (probTerm[i] == 1) 
					{
						pVar[pIndex] = i;
						
						// This variable does not have an instantiated value
						// and is not one of the ones to be summed.
						// (It does not matter what value is set as long as
						// the value is consistent across all cases.  
						// The probabilities with uninstantiated variables
						// should cancel out in the end.)
						if (loopVarValues[i] == -1)  
						{
							pValues[pIndex] = 0;
						}
						else 
						{
							pValues[pIndex] = loopVarValues[i];
						}
						
						pIndex++;
					}
				}	
				
				if (debug)
				{
					System.out.print("---------- pVar: ");
					for (int i = 0; i < nVarInMarginal; i++)
					{
						System.out.print(pVar[i] + "  ");
					}
					System.out.println();			
					
					System.out.print("---------- pValues: ");
					for (int i = 0; i < nVarInMarginal; i++)
					{
						System.out.print(pValues[i] + "  ");
					}
					System.out.println();			
				}
				
				if (debug)
				{
					System.out.print(
						"---------- probTerm resultOneConfig (old; new): " +
						resultOneConfig + ";  ");
				}
				
				//resultOneConfig = rowSumUpdater.getJointMarginal(pVar, 
				//												 pValues);
				
				// We do not need this: the "else" condition will never happen
				
				// skip over combinations where the latent variables
				// do not have values 0
				// This is so as to avoid duplicate summing with only
				// the latent variable values varying
				/*
				boolean flag = true;
				for (int i = 0; i < nVarInMarginal; i++)
				{
					if ((bayesIm.getNode(pVar[i]).getNodeType() == 
														NodeType.LATENT)
						&&
						(pValues[i] != 0))
					{
						flag = false;
						break;
					}
				}
				
				if (flag)
				{
				 */
					Proposition prop = Proposition.tautology(bayesIm);
					for (int i = 0; i < nVarInMarginal; i++) 
					{
						prop.setCategory(pVar[i], pValues[i]);		
					}
					// restrict the proposition to only observed variables
					Proposition propObs = 
						new Proposition(((MlBayesImObs)bayesIm).getBayesImObs(), prop);

					resultOneConfig = ((MlBayesImObs) bayesIm).getJPD().getProb(propObs);
				/*
				}
				else  // skip over extra latent variable combinations
				{
					resultOneConfig = 0.0;
				}
				 */

				
				if (debug)
				{
					System.out.println(resultOneConfig);
				}
			}
			else // process subList
			{
				if (debug)
				{
					System.out.println(
					"---------- ---------- ---------- ---------- subList");
				}

				if (debug)
				{
					System.out.println(
					"---------- ---------- ---------- ---------- subList resultOneConfig (old): " +
					resultOneConfig);
				}
				resultOneConfig = subList.computeValue(bayesIm, loopVarValues);
				if (debug)
				{
					System.out.println(
					"---------- ---------- ---------- ---------- subList resultOneConfig (new): " +
					resultOneConfig);
				}
			}
			
			// sum the result from this iteration
			resultAll = resultAll + resultOneConfig;
			
			if (debug)
			{
				System.out.println("========== so far resultAll: " + 
																resultAll);
			}
			
			// reset 
			resultOneConfig = 0.0;
			
			// compose next variable value configuration
			if ((sumOverVariables[curVar] == 1) &&
				(loopVarValues[curVar] < bayesIm.getNumColumns(curVar) - 1)
				)
			{
				loopVarValues[curVar]++;
				if (debug)
				{
					System.out.print("next config1: curVar: " + curVar + 
														"; loopVarValues: ");
					for (int i = 0; i < nNodes; i++)
					{
						System.out.print(loopVarValues[i] + "  ");
					}
					System.out.println();
				}
			}
			else 
			{
				while ((curVar >= 0)  
					   &&
					   ((sumOverVariables[curVar] != 1) 
						||
						(loopVarValues[curVar] == 
						 bayesIm.getNumColumns(curVar) - 1))
					   )
				{
					curVar--;
				}
				
				if (curVar >= 0)
				{
					loopVarValues[curVar]++;
					
					for (int j = curVar+1; j < nNodes; j++)
					{
						if (sumOverVariables[j] == 1)
						{
							loopVarValues[j] = 0;
						}
					}
					curVar = nNodes - 1;
				}
				
				if (debug)
				{
					System.out.print("next config2: curVar: " + curVar + 
														"; loopVarValues: ");
					for (int i = 0; i < nNodes; i++)
					{
						System.out.print(loopVarValues[i] + "  ");
					}
					System.out.println();
				}
			}
		}
				
		if (nextTerm != null)  // next term 
		{
			if (debug)
			{
				System.out.println(
					"---------- ---------- ---------- nextTerm");
			}
			
			resultNextTerm = nextTerm.computeValue(bayesIm, fixedVarValues);
			
			if (debug)
			{
				System.out.println(
					"---------- ---------- ---------- nextTerm resultNextTerm: " +
					resultNextTerm);
			}
		}
		

		if (inNumerator)
		{
			if (debug)
			{
				System.out.println("========== resultAll: " + 
											resultAll * resultNextTerm);
			}
			return (resultAll * resultNextTerm);
		}
		else // in the denominator
		{
			if (debug)
			{
				System.out.println("========== resultAll: " + 
										(1.0 / resultAll) * resultNextTerm);
			}
			return ((1.0 / resultAll) * resultNextTerm);
			
		}

	}
	
	//==============================PRIVATE METHODS=======================//
	
	/////////////////////////////////////////////////////////////////
	// initialization
	//
	private void initialize()
	{
		sumOverVariables = new int[nVariables];
		probTerm = new int[nVariables];
		
		for (int i = 0; i < nVariables; i++)
		{
			sumOverVariables[i] = 0;
			probTerm[i] = 0;   // Q(emptyset) = 1
		}
		
		inNumerator = true;
		
		subList = null;
		nextTerm = null;		
	}
}




