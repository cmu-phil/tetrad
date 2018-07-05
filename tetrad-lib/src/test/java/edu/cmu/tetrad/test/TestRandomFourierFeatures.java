package edu.cmu.tetrad.test;

import org.junit.Test;

import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradMatrix;
import edu.pitt.dbmi.algo.rcit.RandomFourierFeatures;

public final class TestRandomFourierFeatures {

	@Test
	public void testArraysInput() {
		int seed = 25;
		RandomUtil ru = RandomUtil.getInstance();
		ru.setSeed(seed);

		double[][] x = new double[100][100];
		for (int i = 0; i < x.length; i++) {
			for (int j = 0; j < x[0].length; j++) {
				x[i][j] = ru.nextNormal(0, 1);
			}
		}

		int num_f = 5;
		double sigma = 1;

		RandomFourierFeatures r = RandomFourierFeatures.generate(x, null, null, num_f, sigma);

		TetradMatrix feature = r.getFeature();
		System.out.println("feature rows: " + feature.rows());
		System.out.println("feature columns: " + feature.columns());
		for (int i = 0; i < feature.rows(); i++) {
			for (int j = 0; j < feature.columns(); j++) {
				System.out.println("feature[" + i + "][" + j + "]: " + feature.get(i, j));
			}
		}

		TetradMatrix w = r.getW();
		System.out.println("w rows: " + w.rows());
		System.out.println("w columns: " + w.columns());
		for (int i = 0; i < w.rows(); i++) {
			for (int j = 0; j < w.columns(); j++) {
				System.out.println("w[" + i + "][" + j + "]: " + w.get(i, j));
			}
		}

		TetradMatrix b = r.getB();
		System.out.println("b rows: " + b.rows());
		System.out.println("b columns: " + b.columns());
		for (int i = 0; i < b.rows(); i++) {
			for (int j = 0; j < b.columns(); j++) {
				System.out.println("b[" + i + "][" + j + "]: " + b.get(i, j));
			}
		}

	}

	@Test
	public void testTetradMatrixInput() {
		int seed = 25;
		RandomUtil ru = RandomUtil.getInstance();
		ru.setSeed(seed);

		double[][] x = new double[100][100];
		for (int i = 0; i < x.length; i++) {
			for (int j = 0; j < x[0].length; j++) {
				x[i][j] = ru.nextNormal(0, 1);
			}
		}

		int num_f = 5;
		double sigma = 1;

		TetradMatrix matrix_x = new TetradMatrix(x);
		RandomFourierFeatures r = RandomFourierFeatures.generate(matrix_x, null, null, num_f, sigma);

		TetradMatrix feature = r.getFeature();
		System.out.println("feature rows: " + feature.rows());
		System.out.println("feature columns: " + feature.columns());
		for (int i = 0; i < feature.rows(); i++) {
			for (int j = 0; j < feature.columns(); j++) {
				System.out.println("feature[" + i + "][" + j + "]: " + feature.get(i, j));
			}
		}

		TetradMatrix w = r.getW();
		System.out.println("w rows: " + w.rows());
		System.out.println("w columns: " + w.columns());
		for (int i = 0; i < w.rows(); i++) {
			for (int j = 0; j < w.columns(); j++) {
				System.out.println("w[" + i + "][" + j + "]: " + w.get(i, j));
			}
		}

		TetradMatrix b = r.getB();
		System.out.println("b rows: " + b.rows());
		System.out.println("b columns: " + b.columns());
		for (int i = 0; i < b.rows(); i++) {
			for (int j = 0; j < b.columns(); j++) {
				System.out.println("b[" + i + "][" + j + "]: " + b.get(i, j));
			}
		}

	}
}
