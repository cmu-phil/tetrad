/*
 * Copyright (C) 2015 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.pitt.dbmi.algo.rcit;

/**
 * 
 * Apr 10, 2018 5:23:04 PM
 * 
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 *
 */
public class RandomFourierFeatures {

	public double[][] x;
	
	public double[] w;
	
	public double[][] b;
	
	
	public double[][] getX() {
		return x;
	}


	public void setX(double[][] x) {
		this.x = x;
	}


	public double[] getW() {
		return w;
	}


	public void setW(double[] w) {
		this.w = w;
	}


	public double[][] getB() {
		return b;
	}


	public void setB(double[][] b) {
		this.b = b;
	}


	public static RandomFourierFeatures generate(double[][] x, double[] w,double[][] b,int num_f,int sigma,int seed){
				
		if(num_f < 1){
			num_f = 25;
		}
		
		if(w == null){
			
		}
		
		return null;
	}
}
