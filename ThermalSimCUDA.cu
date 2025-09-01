
#define conductivity 0.1666666f

// Convert an xyz coordinate to an index in the array
__device__ __forceinline__ int toIndex(int x, int y, int z, int cellsWide) {
	//return x * cellsWide * cellsWide + y * cellsWide + z;
	return (x * cellsWide + y) * cellsWide + z;
}

// Compute one step of thermal diffusion in 3D
extern "C"
__global__ void thermalDiffusionStep(const int cellsWide,
		const int airPadding, const char shouldComputeAir, const char shouldComputeBoundary,
		const char* cellEnabled, float* cellHeat, float* cellDeltaHeat, const char copyMode,
		const float heatSourceHeatPerCell) {
	
    const int x = blockIdx.x * blockDim.x + threadIdx.x;
	const int y = blockIdx.y * blockDim.y + threadIdx.y;
	const int z = blockIdx.z * blockDim.z + threadIdx.z;
	
	if (x >= cellsWide || y >= cellsWide || z >= cellsWide) {
		return;
	}
	
	const int idx = toIndex(x, y, z, cellsWide);
	
	// If we only want to copy the results from the previous iteration
	if (copyMode) {
		cellHeat[idx] += cellDeltaHeat[idx];
	} else {
		// Array to represent the 6 adjacent cells
		const char axes[] = {
				1, 0, 0,
				-1, 0, 0,
				0, 1, 0,
				0, -1, 0,
				0, 0, 1,
				0, 0, -1};
		
		// If this cell gets heat directly from the artificial heat source
		if (y == 0 && x > airPadding*1.3f-1 && x < cellsWide-airPadding*1.3f &&
					z > airPadding*1.3f-1 && z < cellsWide-airPadding*1.3f) {
			cellDeltaHeat[idx] = heatSourceHeatPerCell;
		} else {
			cellDeltaHeat[idx] = 0;
		}
		
		// Iterate over the adjacent cells
		for (int i = 0; i < 6 * 3; i += 3) {
			// Check if this cell is in bounds
			if (x + axes[i+0] >= 0 && x + axes[i+0] < cellsWide &&
					y + axes[i+1] >= 0 && y + axes[i+1] < cellsWide &&
					z + axes[i+2] >= 0 && z + axes[i+2] < cellsWide) {
				
				const int idx2 = toIndex(x + axes[i+0], y + axes[i+1], z + axes[i+2], cellsWide);
				
				if (cellEnabled[idx] != cellEnabled[idx2]) {
					// Air to metal boundary (exclude the heat source area)
					if (!shouldComputeBoundary) {
						continue;
					}
				} else if (cellEnabled[idx] == 0) {
					// Air to air boundary
					if (!shouldComputeAir) {
						continue;
					}
				} // Metal to metal boundary is always computed
				
				cellDeltaHeat[idx] += (cellHeat[idx2] - cellHeat[idx]) * conductivity;
			} else if (shouldComputeAir && !(y == 0 &&
						x > airPadding*1.3f-1 && x < cellsWide-airPadding*1.3f &&
						z > airPadding*1.3f-1 && z < cellsWide-airPadding*1.3f)) {
				// This cell is not in bounds, so apply border conditions.
				// The border heat is assumed to be ambient temperature air (0 degrees).
				cellDeltaHeat[idx] -= cellHeat[idx] * conductivity;
			}
		}
	}
}