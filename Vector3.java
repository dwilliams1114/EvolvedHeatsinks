package heatsinkDesign;

public class Vector3 {
	// Members
	public double x;
	public double y;
	public double z;
	public final static Vector3 zero = new Vector3(0, 0, 0);
	public final static Vector3 one = new Vector3(1, 1, 1);
	public final static Vector3 up = new Vector3(0, 1, 0);
	public final static Vector3 down = new Vector3(0, -1, 0);
	
	public Vector3() {}
	
	public Vector3(final Vector3 other) {
		x = other.x;
		y = other.y;
		z = other.z;
	}
	
	public Vector3(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	// Compare two vectors
	public boolean equals(Vector3 other) {
		return (x == other.x && y == other.y && z == other.z);
	}
	
	// Compare two vectors with some margin of error
	public boolean equals(Vector3 other, double epsilon) {
		return Math.abs(x - other.x) < epsilon &&
				Math.abs(y - other.y) < epsilon &&
				Math.abs(z - other.z) < epsilon;
	}
	
	// Add two vectors
	public Vector3 add(Vector3 other) {
		return new Vector3(x + other.x, y + other.y, z + other.z);
	}

	// Add two vectors by components
	public Vector3 add(double xOffset, double yOffset, double zOffset) {
		return new Vector3(x + xOffset, y + yOffset, z + zOffset);
	}
	
	// Subtract two vectors
	public Vector3 sub(Vector3 other) {
		 return new Vector3(x - other.x, y - other.y, z - other.z);
	}
	
	// Multiply vector by number
	public Vector3 mult(double num) {
		return new Vector3(x * num, y * num, z * num);
	}
	
	// Divide vector by number
	public Vector3 divide(double num) {
		return new Vector3(x / num, y / num, z / num);
	}
	
	//Average of two vectors
	public Vector3 average(Vector3 other) {
		return new Vector3((x+other.x)/2.0, (y+other.y)/2.0, (z+other.z)/2.0);
	}
	
	// Add components to this vector
	public void xAdd(double x, double y, double z) {
		this.x += x;
		this.y += y;
		this.z += z;
	}
	
	// Add a vector to this vector
	public void xAdd(final Vector3 vec) {
		x += vec.x;
		y += vec.y;
		z += vec.z;
	}
	
	// Subtract a vector from this vector
	public void xSub(final Vector3 vec) {
		x -= vec.x;
		y -= vec.y;
		z -= vec.z;
	}
	
	// Divide this vector by a number
	public void xDivide(double num) {
		x /= num;
		y /= num;
		z /= num;
	}
	
	// Multiply this vector by a number
	public void xMult(double num) {
		x *= num;
		y *= num;
		z *= num;
	}
	
	// Weighted average of this vector and another
	public void xMix(final Vector3 other, final double a) {
		x = x*a + other.x*(1-a);
		y = y*a + other.y*(1-a);
		z = z*a + other.z*(1-a);
	}
	
	// Dot product of two vectors
	public double dot(Vector3 other) {
		//if one of the vectors are zero, if compensates for error in the
		//normalized() and normalize() method
		if ((other.x == 0 && other.y == 0 && other.z == 0) || 
				(x == 0 && y == 0 && z == 0)) {
			return 1;
		}else{
			return (x*other.x + y*other.y + z*other.z);
		}
	}
	
	// Return the distance between two points
	public double dist(final Vector3 other) {
		final double tempX = x - other.x;
		final double tempY = y - other.y;
		final double tempZ = z - other.z;
		return Math.sqrt(tempX * tempX + tempY * tempY + tempZ * tempZ);
	}
	
	// Return the distance of this vector to another in Manhattan distance
	public double manhattanDist(final Vector3 other) {
		return Math.abs(x - other.x) +
				Math.abs(y - other.y) +
				Math.abs(z - other.z);
	}
	
	// Return the squared distance between two points
	public double squareDist(final Vector3 other) {
		final double tempX = x - other.x;
		final double tempY = y - other.y;
		final double tempZ = z - other.z;
		return tempX * tempX + tempY * tempY + tempZ * tempZ;
	}
	
	// Quick calculation of distance function
	public double distFast(final Vector3 other) {
		final double tempX = x - other.x;
		final double tempY = y - other.y;
		final double tempZ = z - other.z;
		return sqrt(tempX * tempX + tempY * tempY + tempZ * tempZ);
	}
	
	// Make the length of this vector 1.0
	public void normalize() {
		double mag = magnitude();
		if (mag != 0) {
			x /= mag;
			y /= mag;
			z /= mag;
		}
	}
	
	// Reflect this vector over the given normal vector
	public final Vector3 reflectOverNorm(final Vector3 norm) {
		//norm.normalize();
		//normalize();
		return sub(norm.mult(dot(norm)*2));
	}
	
	// Weighted average of this vector and another
	public final Vector3 mix(final Vector3 other, final double a) {
		// TODO consider: x + (y - x) * a
		return new Vector3(
				x*a + other.x*(1-a),
				y*a + other.y*(1-a),
				z*a + other.z*(1-a));
	}
	
	// Return the angle between vectors
	public final double angle(final Vector3 other) {
		return Math.acos(dot(other));
	}
	
	// Multiply component by component
	public final Vector3 multVec(final Vector3 other) {
		return new Vector3(x * other.x, y * other.y, z * other.z);
	}
	
	// Get a vector in the same direction with a length of on
	public Vector3 normalized() {
		double mag = magnitude();
		if (mag != 0) {
			return new Vector3(x/mag, y/mag, z/mag);
		} else {
			return new Vector3();
		}
	}
	
	// Set the length of this vector
	public Vector3 setLength(double length) {
		final double newLength = length / magnitude();
		return new Vector3(x * newLength, y * newLength, z * newLength);
	}
	
	// Return the cross product
	public Vector3 cross(final Vector3 other) {
		return new Vector3(
				this.y * other.z - this.z * other.y,
				this.z * other.x - this.x * other.z,
				this.x * other.y - this.y * other.x);
	}
	
	// Rotate this vector around the given vector by the given angle in radians
	public Vector3 rotateAround(final Vector3 axis, final double angle) {
		axis.normalize();
		final Vector3 axisParallel = axis.mult(this.dot(axis));
		final Vector3 axisOrthog = this.sub(axisParallel);
		
		final Vector3 w = axis.cross(axisOrthog);
		
		final double x1 = Math.cos(angle) / axisOrthog.magnitude();
		final double x2 = Math.sin(angle) / w.magnitude();
		
		final Vector3 rotated = axisOrthog.mult(x1).add(w.mult(x2)).mult(axisOrthog.magnitude());
		
		return rotated.add(axisParallel);
	}
	
	// Super fast square root calculation.
	// 99% accurate from 0.063 to 5.7
	// 90% accurate from 0.006 to 14
	// 80% accurate from 0.003 to 23
	private static double sqrt(double x) {
		double guess = 2.4 * (1 - 1/(x + 1));
		double a = (x - guess * guess)/2/guess;
		double c = 2.8 / (x + 0.04);
		
		return guess + a - a * a / c / (x + a) / (x + 1/c);
	}
	
	// Set the vector length to the given length if the
	//	given length is larger than the vector length
	public Vector3 normalizedToMax(double unit) {
		
		final double mag = magnitude();
		if (mag > unit) {
			final double newLength = unit / mag;
			return new Vector3(x * newLength, y * newLength, z * newLength);
		} else {
			return this;
			//return new Vector3(this);
		}
	}
	
	// Set the vector length to the given length if the
	//	given length is larger than the vector length
	public void normalizeToMax(double unit) {
		double mag = magnitude();
		if (mag > unit) {
			x *= unit/mag;
			y *= unit/mag;
			z *= unit/mag;
		}
	}
	
	// Return the magnitude of this vector
	public double magnitude() {
		return Math.sqrt((x*x) + (y*y) + (z*z));
	}
	
	// Return the squared magnitude of this vector
	public double squareMag() {
		return (x*x) + (y*y) + (z*z);
	}
	
	public String toString() {
		return "[" + x+", "+y + ", " + z + "]";
	}
	
	// Return true if this vector is non-zero
	public boolean isNonZero() {
		return x != 0 || y != 0 || z != 0;
	}
}
