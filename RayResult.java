package heatsinkDesign;

// This class is a data structure that stores information about
//    a result from raycasting.

public class RayResult {
	public Vector3 hit;
	public Vector3 norm;
	public Vector3 color;
	public double depth;
	
	public RayResult(Vector3 hit, Vector3 norm, Vector3 color, double depth) {
		this.hit = hit;
		this.norm = norm;
		this.color = color;
		this.depth = depth;
	}
}
