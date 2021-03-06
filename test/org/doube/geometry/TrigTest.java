package org.doube.geometry;

import static org.junit.Assert.*;

import org.junit.Test;

public class TrigTest {

	double[] p0 = {1, 2, 3};
	double[] p1 = {4, 5, 6};
	
	@Test
	public void testDistance3DDoubleArrayDoubleArray() {
		double result = Trig.distance3D(p0, p1);
		double expected = Math.sqrt(27);
		assertEquals(expected, result, 1e-12);
	}

	@Test
	public void testDistance3DDoubleDoubleDoubleDoubleDoubleDouble() {
		double result = Trig.distance3D(1, 2, 3, 4, 5, 6);
		double expected = Math.sqrt(27);
		assertEquals(expected, result, 1e-12);
	}

	@Test
	public void testDistance3DDoubleDoubleDouble() {
		double result = Trig.distance3D(1, 2, 3);
		double expected = Math.sqrt(14);
		assertEquals(expected, result, 1e-12);
	}

	@Test
	public void testDistance3DDoubleArray() {
		double result = Trig.distance3D(p1);
		double expected = Math.sqrt(77);
		assertEquals(expected, result, 1e-12);
	}
	
	@Test
	public void testAngle3D(){
		double result = Trig.angle3D(1, 2, 3, 4, 5, 6, 7, 8, 9);
		double expected = 0;
		assertEquals(expected, result, 1e-12);
		result = Trig.angle3D(1, 2, 3, 4, 5, 6, 0, 0, 0);
		expected = Math.acos(32 / (Math.sqrt(14) * Math.sqrt(77)));
		assertEquals(expected, result, 1e-12);
	}

}
