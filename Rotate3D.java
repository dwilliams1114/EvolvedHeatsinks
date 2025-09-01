package heatsinkDesign;

// Created by Daniel Williams
// Created on February 20, 2017
// Last updated on July 29, 2018
// Library for convenient rotation and angle-vector related functions

public class Rotate3D {
	
	public final static Vector3 rotatePointX(final Vector3 p, final double a) {
		final Vector3 newPos = new Vector3();
		newPos.x = p.x;
		newPos.y = p.y * Math.cos(a) - p.z * Math.sin(a);
		newPos.z = p.y * Math.sin(a) + p.z * Math.cos(a);
		return newPos;
	}
	
	public final static Vector3 rotatePointY(final Vector3 p, final double a) {
		final Vector3 newPos = new Vector3();
		newPos.x = p.x * Math.cos(a) + p.z * Math.sin(a);
		newPos.y = p.y;
		newPos.z = -p.x * Math.sin(a) + p.z * Math.cos(a);
		return newPos;
	}
	
	public final static Vector3 rotatePointZ(final Vector3 p, final double a) {
		final Vector3 newPos = new Vector3();
		newPos.x = p.x * Math.cos(a) - p.y * Math.sin(a);
		newPos.y = p.x * Math.sin(a) + p.y * Math.cos(a);
		newPos.z = p.z;
		return newPos;
	}
	
	public final static Vector3 rotatePoint(final Vector3 p, final Vector3 rotation) {
		return rotatePoint(p, rotation.x, rotation.y, rotation.z);
	}
	
	public final static Vector3 rotatePoint(final double x, final double y, final double z, final Vector3 rotation) {
		return rotatePoint(new Vector3(x, y, z), rotation.x, rotation.y, rotation.z);
	}
	
	// Rotates on order of: z, y, x
	public final static Vector3 rotatePoint(final Vector3 p, final double x, final double y, final double z) {
		final Vector3 newPos = new Vector3();
		newPos.x = Math.cos(y)*(Math.sin(z)*p.y +
				Math.cos(z)*p.x) - Math.sin(y)*p.z;
		newPos.y = Math.sin(x)*(Math.cos(y)*p.z + Math.sin(y)*
				(Math.sin(z)*p.y + Math.cos(z)*p.x)) +
				Math.cos(x)*(Math.cos(z)*p.y - Math.sin(z)*p.x);
		newPos.z = Math.cos(x)*(Math.cos(y)*p.z + Math.sin(y)*
				(Math.sin(z)*p.y + Math.cos(z)*p.x)) -
				Math.sin(x)*(Math.cos(z)*p.y - Math.sin(z)*p.x);
		return newPos;
	}
	
	public final static Vector3 getVectorFromRotation(final Vector3 rot) {
		return getVectorFromRotation(rot.x, rot.y, rot.z);
	}
	
	// Returns a forward vector for the given rotation.
	// y rotation is around the global vertical axis.
	// x rotation is around the local screen horizontal axis
	// z rotation is not used (because the resulting vector
	//    is along the local z axis into the screen).
	public final static Vector3 getVectorFromRotation(double x, double y, double z) {
		final Vector3 vec = new Vector3();
		
		vec.x = Math.sin(y) * Math.cos(x);
		vec.z = Math.cos(y) * Math.cos(x);
		vec.y = Math.sin(x);
		
		return vec;
	}
}
