package org.doube.geometry;

import ij.IJ;

/**
 * <p>
 * Represents an ellipsoid defined as <i>ax</i><sup>2</sup> +
 * <i>by</i><sup>2</sup> + <i>cz</i><sup>2</sup> + 2<i>dxy</i> + 2<i>exz</i> +
 * 2<i>fyz</i> + 2<i>gx</i> + 2<i>hy</i> + 2<i>iz</i> = 1 <br />
 * </p>
 * 
 * @author Michael Doube
 */
public class Ellipsoid {

	// centre
	private double cx;
	private double cy;
	private double cz;

	// radii
	private double ra;
	private double rb;
	private double rc;

	// ellipsoid equation coefficients
	private double a;
	private double b;
	private double c;
	private double d;
	private double e;
	private double f;
	private double g;
	private double h;
	private double i;

	// calculated volume
	private double volume = -1;

	// eigenvectors of axes
	private double eV00;
	private double eV01;
	private double eV02;
	private double eV10;
	private double eV11;
	private double eV12;
	private double eV20;
	private double eV21;
	private double eV22;

	/**
	 * Instantiate an ellipsoid from the result of FitEllipsoid
	 * 
	 * @param ellipsoid
	 */
	public Ellipsoid(Object[] ellipsoid) {
		// Object[] ellipsoid = { centre, radii, eigenVectors, equation, E };

		double[] centre = (double[]) ellipsoid[0];
		this.cx = centre[0];
		this.cy = centre[1];
		this.cz = centre[2];

		double[] radii = (double[]) ellipsoid[1];
		this.ra = radii[0];
		this.rb = radii[1];
		this.rc = radii[2];
		if (Double.isNaN(ra) || Double.isNaN(rb) || Double.isNaN(rc))
			throw new IllegalArgumentException("Radius is NaN");

		setVolume();
		IJ.log("ra = " + ra + ", rb =" + rb + ", rc = " + rc);

		double[][] eigenVectors = (double[][]) ellipsoid[2];
		setEigenVectors(eigenVectors);

		double[] equation = (double[]) ellipsoid[3];
		this.a = equation[0];
		this.b = equation[1];
		this.c = equation[2];
		this.d = equation[3];
		this.e = equation[4];
		this.f = equation[5];
		this.g = equation[6];
		this.h = equation[7];
		this.i = equation[8];
	}

	/**
	 * Construct an Ellipsoid from the radii (a,b,c), centroid (cx, cy, cz) and
	 * Eigenvectors
	 * 
	 * @param a
	 * @param b
	 * @param c
	 * @param cx
	 * @param cy
	 * @param cz
	 * @param eigenVectors
	 */
	public Ellipsoid(double a, double b, double c, double cx, double cy,
			double cz, double[][] eigenVectors) {
		this.ra = a;
		this.rb = b;
		this.rc = c;
		this.cx = cx;
		this.cy = cy;
		this.cz = cz;
		setEigenVectors(eigenVectors);
		setVolume();
	}

	public double getVolume() {
		return (new Double(volume)).doubleValue();
	}

	private void setVolume() {
		volume = Math.PI * ra * rb * rc * 4 / 3;
	}

	public double[] getRadii() {
		double[] radii = { ra, rb, rc };
		return radii.clone();
	}

	public double[] getEquation() {
		double[] equation = { a, b, c, d, e, f, g, h, i };
		return equation.clone();
	}

	public boolean contains(double x, double y, double z) {
		if (solve(x, y, z) <= 1)
			return true;
		else
			return false;
	}

	public double solve(double x, double y, double z) {
		return a * x * x + b * y * y + c * z * z + 2
				* (d * x * y + e * x * z + f * y * z + g * x + h * y + i * z);
	}

	public double getMajorRadius() {
		double val = ra;
		return val;
	}

	public double[] getCentre() {
		double[] centre = { cx, cy, cz };
		return centre.clone();
	}

	public double[][] getSurfacePoints(final int nPoints) {

		// get regularly-spaced points on the unit sphere
		double[][] points = Vectors.regularVectors(nPoints);

		for (int p = 0; p < nPoints; p++) {
			// stretch the unit sphere into an ellipsoid
			final double x = ra * points[p][0];
			final double y = rb * points[p][1];
			final double z = rc * points[p][2];
			// rotate and translate the ellipsoid into position
			points[p][0] = x * eV00 + y * eV01 + z * eV02 + cx;
			points[p][1] = x * eV10 + y * eV11 + z * eV12 + cy;
			points[p][2] = x * eV20 + y * eV21 + z * eV22 + cz;
		}
		return points;
	}

	private void setEigenVectors(double[][] eigenVectors) {
		this.eV00 = eigenVectors[0][0];
		this.eV01 = eigenVectors[0][1];
		this.eV02 = eigenVectors[0][2];
		this.eV10 = eigenVectors[1][0];
		this.eV11 = eigenVectors[1][1];
		this.eV12 = eigenVectors[1][2];
		this.eV20 = eigenVectors[2][0];
		this.eV21 = eigenVectors[2][1];
		this.eV22 = eigenVectors[2][2];
	}

	/**
	 * Dilate all three axes by an increment
	 *  
	 * @param increment
	 */
	public void dilate(double increment) {
		ra += increment;
		rb += increment;
		rc += increment;
		setVolume();
	}
	
	/**
	 * Constrict all three axes by an increment
	 * 
	 * @param increment
	 */
	public void contract(double increment){
		dilate(-increment);
	}
}