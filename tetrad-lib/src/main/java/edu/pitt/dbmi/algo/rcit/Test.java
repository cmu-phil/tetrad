package edu.pitt.dbmi.algo.rcit;

public class Test {

	// returns the sum of the elements raised to a power
	private static double sum_of_powers(int[] index, double x) {
		double sum = 0;
		for (int i = 0; i < index.length; i++) {
			sum += powers(index[i], x);
		}
		return sum;
	}

	private static double powers(int p, double x) {
		if(p == 0) {
			return 1.0;
		}
		boolean inversed = false;
		if(p < 0) {
			inversed = true;
			p = -p;
		}
		if(p == 1) {
			return !inversed?x:1/x;
		}
		int odd = 0;
		System.out.println("" + p%2);
		if(p%2 == 1) {
			odd++;
			p -= 1;
		}
		System.out.println("p: " + p);
		int height = binlog(p);
		System.out.println("height: " + height);
		odd += p - powers(height,2);
		System.out.println("powers(height,2): " + powers(height,2));
		System.out.println("p - powers(height,2): " + (p - powers(height,2)));
		System.out.println("odd: " + odd);
		double product = x*x;
		for(int i=1;i<height;i++) {
			product = product*product;
		}
		if(odd > 0) {
			product = product*powers(odd, x);
		}
		if(inversed) {
			product = 1/product;
		}
		return product;
	}

	// https://stackoverflow.com/questions/3305059/how-do-you-calculate-log-base-2-in-java-for-integers
	// 10 times faster than Math.log(x)/Math.log(2)
	private static int binlog(int bits) {
		int log = 0;
		if ((bits & 0xffff0000) != 0) {
			bits >>>= 16;
			log = 16;
		}
		if (bits >= 256) {
			bits >>>= 8;
			log += 8;
		}
		if (bits >= 16) {
			bits >>>= 4;
			log += 4;
		}
		if (bits >= 4) {
			bits >>>= 2;
			log += 2;
		}
		return log + (bits >>> 1);
	}
	
	public static double factorial(int c) {
        if (c < 0) throw new IllegalArgumentException("Can't take the factorial of a negative number: " + c);
        if (c == 0) return 1;
        return c * factorial(c - 1);
        //return recfact(1, c);
    }

    public static double recfact(int start, int len) {
    	if(len <= 16) {
    		int result = start;
    		for(int i = start + 1;i < start + len;i++) {
    			result *= i;
    		}
    		return result;
    	}
    	int mid = len / 2;
    	return recfact(start, mid) * recfact(start + mid, len - mid);
    }

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		//int[] index = {1,2,3,4};
		//System.out.println(sum_of_powers(index,2));
		//System.out.println(binlog(6));
		//System.out.println(powers(3,2));
		System.out.println(factorial(17));
	}

}
