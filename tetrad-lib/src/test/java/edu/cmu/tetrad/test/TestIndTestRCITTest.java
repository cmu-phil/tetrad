/**
 * 
 */
package edu.cmu.tetrad.test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.junit.Test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataBox;
import edu.cmu.tetrad.data.DataReader;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DelimiterType;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.pitt.dbmi.algo.rcit.RandomIndApproximateMethod;
import edu.pitt.dbmi.algo.rcit.RandomizedConditionalIndependenceTest;
import edu.pitt.dbmi.data.ContinuousTabularDataset;
import edu.pitt.dbmi.data.Delimiter;
import edu.pitt.dbmi.data.reader.tabular.ContinuousTabularDataFileReader;

/**
 * Jul 2, 2018 4:00:53 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class TestIndTestRCITTest {

	@Test
	public void testPerm() {
		RandomUtil.getInstance().setSeed(1450184147770L);
		TetradLogger.getInstance().addOutputStream(System.out);

		File file = new File("src/test/resources/rcit.test.txt");

		ContinuousTabularDataFileReader reader = new ContinuousTabularDataFileReader(file, Delimiter.WHITESPACE);
		try {
			ContinuousTabularDataset dataset = (ContinuousTabularDataset) reader.readInData();
			DataBox dataBox = new DoubleDataBox(dataset.getData());
			List<Node> nodes = new LinkedList<>();

			dataset.getVariables().forEach(variable -> {
				nodes.add(new ContinuousVariable(variable));
			});

			DataSet dataSet = new BoxDataSet(dataBox, nodes);
			
			for(RandomIndApproximateMethod approx : RandomIndApproximateMethod.values()) {
				System.out.println("RCIT - " + approx);
				RandomizedConditionalIndependenceTest rcit = new RandomizedConditionalIndependenceTest(dataSet);
				rcit.setApprox(approx);
				System.out.println("alpha: " + rcit.getAlpha());
				for (int i = 0; i < nodes.size(); i++) {
					Node x = nodes.get(i);
					for (int j = i + 1; j < nodes.size(); j++) {
						Node y = nodes.get(j);
						List<Node> z = null;
						System.out.println(x.getName() + " _||_ " + y.getName() + " " + rcit.isIndependent(x, y, z)
								+ " | p-value: " + rcit.getPValue());
					}
				}

				for (int i = 0; i < nodes.size(); i++) {
					Node x = nodes.get(i);
					for (int j = i + 1; j < nodes.size(); j++) {
						Node y = nodes.get(j);
						for (int k = 0; k < nodes.size(); k++) {
							if (k != i && k != j) {
								List<Node> z = new ArrayList<>();
								z.add(nodes.get(k));
								System.out.println(x.getName() + " _||_ " + y.getName() + " | " + nodes.get(k).getName()
										+ " " + rcit.isIndependent(x, y, z) + " | p-value: " + rcit.getPValue());
							}
						}
					}
				}

				for (int i = 0; i < nodes.size(); i++) {
					Node x = nodes.get(i);
					for (int j = i + 1; j < nodes.size(); j++) {
						Node y = nodes.get(j);
						for (int k = 0; k < nodes.size(); k++) {
							if (k != i && k != j) {
								List<Node> z = new ArrayList<>();
								z.add(nodes.get(k));
								for (int l = k+1; l < nodes.size(); l++) {
									if (l != i && l != j) {
										z.add(nodes.get(l));
										System.out.println(x.getName() + " _||_ " + y.getName() + " | "
												+ nodes.get(k).getName() + ", " + nodes.get(l).getName() + " "
												+ rcit.isIndependent(x, y, z) + " | p-value: " + rcit.getPValue());
									}
								}
							}
						}
					}
				}
				System.out.println("==============================================");
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		TetradLogger.getInstance().removeOutputStream(System.out);
	}

}
