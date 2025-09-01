package heatsinkDesign;

// Created by Daniel Williams
// Created on September 11, 2020
// Last updated on December 4, 2020
// Created for Many-Core-Computing course.
// Created to automatically design near-optimal heat sinks.

public class HeatsinkMain {
	
	// Configurations
	static final int cellsWide = 80;	// Must be divisible by 8
	static private boolean restrictTo60FPS = false;
	static private final int numThreads = 6;
	static final int airPadding = (int)(cellsWide*0.11 + 2); // Number of cells of padding air around the heat sink
	static final float heatSourceHeatPerCell = cellsWide * 0.02f / (float)Math.pow(cellsWide - airPadding - 4, 1.5);
	static final boolean useGPU = true;
	
	// Internal variables
	static final int cellsWide3 = cellsWide * cellsWide * cellsWide;
	static final byte[] cellEnabled = new byte[cellsWide3];
	static final float[] cellHeat = new float[cellsWide3];
	static final byte[] isCellOnBoundary = new byte[cellsWide3]; // Whether each metal cell is on the metal-air boundary
	static final float[] cellDeltaHeat = new float[cellsWide3]; // Change in cell heat on each iteration
	static final boolean[][][] disconnectedComponentTable = new boolean[cellsWide][cellsWide][cellsWide];// Scratch space
	static CUDAProgram gpuProgram;
	
	// Rate of heat conduction
	// Aluminum: k = 205
	// Static air: k = 0.1
	// Free convection: k = ~5
	// Forced air: k = 30
	static private final float conductivity = 0.16666f;		// Maximum possible conductivity = 1/6
	static private final int airIterationSkips = 30;		// k = conductivity / this number
	static private final int boundaryIterationSkips = 60;	// k = conductivity / this number (must be multiple of above)
	
	public static void main(String[] args) {
		if (useGPU) {
			CUDAProgram.initializeGPU();
			gpuProgram = new CUDAProgram("thermalDiffusionStep", "src/heatsinkDesign/ThermalSimCUDA.cu");
			gpuProgram.setBlockSize(4, 4, 4);
			gpuProgram.setIterations(cellsWide, cellsWide, cellsWide);
			
			// Set the constant arguments for the CUDA program
			gpuProgram.setInputArg(0, cellsWide);
			gpuProgram.setInputArg(1, airPadding);
			gpuProgram.setInputArg(8, heatSourceHeatPerCell);
		}
		
		setInitialDesign();
		//SaveLoadTools.loadHeatsink("HeatSinkData2.txt");
		//setDesignIteration(1);
		preprocessDesign();
		
		// Which renderer to use:
		Renderer.initialize();
		//VoxelRayTracer.initialize("src/heatsinkDesign/imagesRandom");
		//VoxelRayTracer.initialize(null); // No saving
		
		
		//*
		double previousScore = 999999;
		double initialScore = 0;
		final byte[] previousCellEnabled = new byte[cellsWide3];
		long startTime = 0; // Initialized after the first iteration
		int iteration = 0;
		while (true) {
			final double newScore = runSimulation(iteration == 0);
			if (iteration == 0) {
				initialScore = newScore;
			}
			//print("Score: " + newScore);
			
			// If the new score is worse than the previous, then revert
			if (newScore > previousScore) {
				// Revert to the previous design
				for (int i = 0; i < cellsWide3; i++) {
					cellEnabled[i] = previousCellEnabled[i];
				}
			} else {
				print("BETTER!");
				// This design was better, so save it.
				for (int i = 0; i < cellsWide3; i++) {
					previousCellEnabled[i] = cellEnabled[i];
				}
				previousScore = newScore;
			}
			
			if (iteration == 0) {
				startTime = System.currentTimeMillis();
			}
			
			// Randomize a few times (no disconnected components are allowed)
			//evolveDesignExtruded();
			evolveDesignForged(true);
			//evolveDesign3D(true);
			
			if (Math.random() < 0.4) {
				//evolveDesignExtruded();
				evolveDesignForged(true);
				//evolveDesign3D(true);
			}
			if (Math.random() < 0.1) {
				//evolveDesignExtruded();
				evolveDesignForged(true);
				//evolveDesign3D(true);
			}
			
			preprocessDesign();
			iteration++;
			if (iteration % 20 == 0) {
				final long time = System.currentTimeMillis();
				final String ipsString = String.format("%.2f", iteration*1000f/(time - startTime));
				print("Score: " + newScore + ", Iteration: " + iteration + ", IPS: " + ipsString);
				print("Initial score: " + initialScore + ", Time: " + (time - startTime)/1000/60 + " minutes");
			}
		}
		//*/
		
		//runSimulation(true);
	}
	
	// Reset the cells to their default configuration
	static void setInitialDesign() {
		
		/*
		// Make a V-shape heat sink
		for (int x = 0; x < cellsWide; x++) {
			for (int y = 0; y < cellsWide; y++) {
				for (int z = 0; z < cellsWide; z++) {
					final int i = idx(x, y, z);
					cellEnabled[i]  = (x > cellsWide - y/2-cellsWide/3-4 && x < y/2+cellsWide/3+3) ? 0 : (byte)1;
				}
			}
		}
		//*/
		
		//*
		// Make a little box heat sink at the bottom
		for (int x = 0; x < cellsWide; x++) {
			for (int y = 0; y < cellsWide; y++) {
				for (int z = 0; z < cellsWide; z++) {
					final int i = idx(x, y, z);
					cellEnabled[i]  = (y < cellsWide*0.05 + 1) ? (byte)1 : 0;
				}
			}
		}
		//*/
		
		/*
		// Make a thick base
		for (int x = 0; x < cellsWide; x++) {
			for (int y = 0; y < cellsWide; y++) {
				for (int z = 0; z < cellsWide; z++) {
					final int i = idx(x, y, z);
					cellEnabled[i]  = (y < cellsWide*0.2 + 1) ? (byte)1 : 0;
				}
			}
		}
		//*/
		
		/* Add a rectangle heat sink sticking up in the center
		final int width = cellsWide-airPadding*2;
		for (int x = airPadding; x < cellsWide-airPadding; x++) {
			for (int y = 0; y < cellsWide*0.8-airPadding; y++) {
				for (int z = (int)(cellsWide/2 - width*0.2); z < cellsWide/2 + width*0.2; z++) {
					final int i = idx(x, y, z);
					cellEnabled[i] = 1;
				}
			}
		}
		//*/
		
		//* Add a column sticking up in the center
		final int width = cellsWide-airPadding*2;
		for (int y = 0; y < cellsWide-airPadding; y++) {
			for (int x = (int)(cellsWide/2 - width*0.22); x < cellsWide/2 + width*0.22; x++) {
				for (int z = (int)(cellsWide/2 - width*0.22); z < cellsWide/2 + width*0.22; z++) {
					final int i = idx(x, y, z);
					cellEnabled[i] = 1;
				}
			}
		}
		//*/
		
		/* Add a triangle heat sink sticking up in the center
		for (int x = airPadding; x < cellsWide-airPadding; x++) {
			for (int y = 0; y < cellsWide-airPadding; y++) {
				final double width = (cellsWide-airPadding*2)*0.25 - y/5;
				for (int z = (int)(cellsWide/2 - width); z < cellsWide/2 + width; z++) {
					final int i = idx(x, y, z);
					cellEnabled[i] = 1;
				}
			}
		}
		//*/
		
		/* Add horizontal fins in a Christmas tree shape
		for (int y = 0; y < cellsWide*0.95-airPadding; y++) {
			if (y/4%2 == 0) {
				final int width = y/3;
				for (int z = airPadding+width; z < cellsWide-airPadding-width; z++) {
					for (int x = airPadding; x < cellsWide-airPadding; x++) {
						final int i = idx(x, y, z);
						cellEnabled[i] = 1;
					}
				}
			}
		}
		//*/
		
		/* Make a classic simple finned shape heat sink
		final int increment = cellsWide/18+1;
		for (int x = airPadding; x < cellsWide-airPadding; x++) {
			for (int y = 0; y < cellsWide-airPadding; y++) {
				for (int z = airPadding; z < cellsWide-airPadding; z++) {
					if ((z+3) % increment < increment/2) {
						final int idx = idx(x, y, z);
						cellEnabled[idx] = 1;
					}
				}
			}
		}
		//*/
		
		// Clear out all cells that are part of the surrounding air
		for (int x = 0; x < cellsWide; x++) {
			for (int y = 0; y < cellsWide; y++) {
				for (int z = 0; z < cellsWide; z++) {
					final int i = idx(x, y, z);

					cellHeat[i] = 0;
					
					// If this cell is part of the surrounding air, then clear it
					if (x < airPadding || x >= cellsWide-airPadding ||
							y >= cellsWide-airPadding ||
							z < airPadding || z >= cellsWide-airPadding) {
						
						cellEnabled[i] = 0;
					}
				}
			}
			
		}
		
		//cellHeat[cellsWide3-1] = 80000;
		//cellHeat[idx(cellsWide/2, 0, cellsWide/2)] = 9000;
	}
	
	// Slightly change the heat sink design by swapping any pair of cells on the horizontal cross section.
	// This mimics the design of heat sinks by extrusion.
	// This is done symmetrically
	static void evolveDesignExtruded() {
		
		// Array to represent the 4 adjacent cells (exclude x direction)
		final byte[] axes = {
				0, 1, 0,
				0, -1, 0,
				0, 0, 1,
				0, 0, -1
		};
		
		// Find random cell on the surface of the heat sink and invert it
		byte wasFirstCellEnabled;
		outerLoop:
		do {
			
			final int x = airPadding;
			final int y = (int)(Math.random() * (cellsWide - airPadding - 1) + 1);
			final int z = (int)(Math.random() * (cellsWide - airPadding*2) + airPadding);
			
			final int index1 = idx(x, y, z);
			final int index2 = idx(x, y, cellsWide-z-1);
			wasFirstCellEnabled = cellEnabled[index1];
			
			// Determine if this is a cell on the metal-air boundary
			for (int i = 0; i < 4 * 3; i += 3) {
				final int idx2 = idx(x, y + axes[i+1], z + axes[i+2]);
				if (cellEnabled[idx2] != wasFirstCellEnabled) {
					// Invert the cells
					cellEnabled[index1] ^= 1;
					cellEnabled[index2] ^= 1;
					extrudeDesignInX();
					
					// Check if it creates a disconnected component
					if (hasDisconnectedComponents()) {
						// Put the cells back
						cellEnabled[index1] ^= 1;
						cellEnabled[index2] ^= 1;
					} else {
						break outerLoop;
					}
				}
			}
		} while (true);
		
		// Find second random cell on the surface of the heat sink opposite of the first cell
		outerLoop:
		do {
			final int x = airPadding;
			final int y = (int)(Math.random() * (cellsWide - airPadding - 1) + 1);
			final int z = (int)(Math.random() * (cellsWide - airPadding*2) + airPadding);
			
			final int index1 = idx(x, y, z);
			final int index2 = idx(x, y, cellsWide-z-1);
			final byte isEnabled = cellEnabled[index1];
			
			// Skip this if both cells are part of the heat sink or part of the air.
			// This maintains constant volume.
			if (isEnabled == wasFirstCellEnabled) {
				continue;
			}
			
			// Determine if this is a cell on the metal-air boundary
			for (int i = 0; i < 4 * 3; i += 3) {
				final int idx2 = idx(x, y + axes[i+1], z + axes[i+2]);
				if (cellEnabled[idx2] != isEnabled) {
					// Invert the cells
					cellEnabled[index1] ^= 1;
					cellEnabled[index2] ^= 1;
					extrudeDesignInX();
					
					// Check if it creates a disconnected component
					if (hasDisconnectedComponents()) {
						// Put the cells back
						cellEnabled[index1] ^= 1;
						cellEnabled[index2] ^= 1;
					} else {
						break outerLoop;
					}
				}
			}
		} while (true);
		
		extrudeDesignInX();
	}
	
	// Slightly change the heat sink design by swapping two columns of cells.
	// This mimics the design of heat sinks by cold forging.
	static void evolveDesignForged(boolean enforceSymmetry) {
		
		final int bottomMargin = (int)(cellsWide*0.05 + 1);
		
		// Array to represent the 4 adjacent cells (exclude y direction)
		final byte[] axes = {
				1, 0, 0,
				-1, 0, 0,
				0, 0, 1,
				0, 0, -1
		};
		
		// Find random cell on the surface of the heat sink and invert it
		byte wasFirstCellEnabled;
		outerLoop:
		do {
			final int x;
			final int y = cellsWide - airPadding - 1;
			final int z;
			if (enforceSymmetry) {
				x = (int)(Math.random() * (cellsWide/2 - airPadding) + airPadding);
				z = (int)(Math.random() * (x - airPadding) + airPadding);
			} else {
				x = (int)(Math.random() * (cellsWide - airPadding*2) + airPadding);
				z = (int)(Math.random() * (cellsWide - airPadding*2) + airPadding);
			}
			
			final int index1 = idx(x, y, z);
			wasFirstCellEnabled = cellEnabled[index1];
			
			// Determine if this is a cell on the metal-air boundary
			for (int i = 0; i < 4 * 3; i += 3) {
				final int idx2 = idx(x + axes[i], y, z + axes[i+2]);
				if (cellEnabled[idx2] != wasFirstCellEnabled) {
					// Invert the cell
					cellEnabled[index1] ^= 1;
					break outerLoop;
				}
			}
		} while (true);
		
		// Find second random cell on the surface of the heat sink opposite of the first cell
		outerLoop:
		do {
			final int x;
			final int y = cellsWide - airPadding - 1;
			final int z;
			if (enforceSymmetry) {
				x = (int)(Math.random() * (cellsWide/2 - airPadding) + airPadding);
				z = (int)(Math.random() * (x - airPadding) + airPadding);
			} else {
				x = (int)(Math.random() * (cellsWide - airPadding*2) + airPadding);
				z = (int)(Math.random() * (cellsWide - airPadding*2) + airPadding);
			}
			
			final int index1 = idx(x, y, z);
			final byte isEnabled = cellEnabled[index1];
			
			// Skip this if both cells are part of the heat sink or part of the air.
			// This maintains constant volume.
			if (isEnabled == wasFirstCellEnabled) {
				continue;
			}
			
			// Determine if this is a cell on the metal-air boundary
			for (int i = 0; i < 4 * 3; i += 3) {
				final int idx2 = idx(x + axes[i], y, z + axes[i+2]);
				if (cellEnabled[idx2] != isEnabled) {
					// Invert the cell
					cellEnabled[index1] ^= 1;
					break outerLoop;
				}
			}
		} while (true);
		
		// If we are enforcing symmetry, then reflect whole design 8x.
		if (enforceSymmetry) {
			final int y = cellsWide - airPadding - 1;
			for (int x = 0; x < cellsWide/2; x++) {
				for (int z = 0; z < x; z++) {
					final int index1 = idx(x, y, z);
					final int index2 = idx(cellsWide - x - 1, y, z);
					final int index3 = idx(x, y, cellsWide - z - 1);
					final int index4 = idx(cellsWide - x - 1, y, cellsWide - z - 1);
					final int index5 = idx(z, y, x);
					final int index6 = idx(z, y, cellsWide - x - 1);
					final int index7 = idx(cellsWide - z - 1, y, x);
					final int index8 = idx(cellsWide - z - 1, y, cellsWide - x - 1);
					cellEnabled[index2] = cellEnabled[index1];
					cellEnabled[index3] = cellEnabled[index1];
					cellEnabled[index4] = cellEnabled[index1];
					cellEnabled[index5] = cellEnabled[index1];
					cellEnabled[index6] = cellEnabled[index1];
					cellEnabled[index7] = cellEnabled[index1];
					cellEnabled[index8] = cellEnabled[index1];
				}
			}
		}
		
		extrudeDesignInY(bottomMargin);
	}
	
	// Extrude the whole design to have a uniform cross-section in the y-direction,
	//   excluding the bottom few layers
	static void extrudeDesignInY(int bottomMargin) {
		
		// Extrude the whole design in the x direction
		for (int z = airPadding; z < cellsWide - airPadding; z++) {
			for (int x = airPadding; x < cellsWide - airPadding; x++) {
				// Copy the cell states along the y axis
				final byte state = cellEnabled[idx(x, cellsWide-airPadding-1, z)];
				for (int y = bottomMargin; y < cellsWide-airPadding-1; y++) {
					final int i = idx(x, y, z);
					cellEnabled[i] = state;
				}
			}
		}
	}
	
	// Extrude the whole design to have a uniform cross-section in the x-direction
	static void extrudeDesignInX() {
		
		// Extrude the whole design in the x direction
		for (int z = airPadding; z < cellsWide - airPadding; z++) {
			for (int y = 0; y < cellsWide-airPadding; y++) {
				// Copy the cell states along the x axis
				final byte state = cellEnabled[idx(airPadding, y, z)];
				for (int x = airPadding+1; x < cellsWide-airPadding; x++) {
					final int i = idx(x, y, z);
					cellEnabled[i] = state;
				}
			}
		}
	}
	
	// Slightly change the heat sink design by swapping any pair of cells.
	// Then extrude the design into the X dimension to create a uniform cross section.
	static void evolveDesign3D(boolean enforceSymmetry) {
		{
			// Array to represent the 6 adjacent cells
			final byte[] axes = {
					1, 0, 0,
					-1, 0, 0,
					0, 1, 0,
					0, -1, 0,
					0, 0, 1,
					0, 0, -1
			};
			
			// Find random cell on the surface of the heat sink and invert it
			int index1 = -1;
			outerLoop:
			do {
				final int x;
				final int y;
				final int z;
				if (enforceSymmetry) {
					x = (int)(Math.random() * (cellsWide/2 - airPadding) + airPadding);
					y = (int)(Math.random() * (cellsWide - airPadding));
					z = (int)(Math.random() * (x - airPadding) + airPadding);
				} else {
					x = (int)(Math.random() * (cellsWide - airPadding*2) + airPadding);
					y = (int)(Math.random() * (cellsWide - airPadding));
					z = (int)(Math.random() * (cellsWide - airPadding*2) + airPadding);
				}
				index1 = idx(x, y, z);
				
				final byte isEnabled = cellEnabled[index1];
				
				// If this cell is part of the surrounding air, then just skip it
				if (x < airPadding || x >= cellsWide-airPadding ||
						y >= cellsWide-airPadding ||
						z < airPadding || z >= cellsWide-airPadding) {
					continue;
				}
				
				// Skip if this cell is within the heat source
				if (y == 0 && x > airPadding*1.3 && x < cellsWide - airPadding*1.3 - 1 &&
						z > airPadding*1.3 && z < cellsWide - airPadding*1.3 - 1) {
					continue;
				}
				
				// Determine if this is a cell on the metal-air boundary
				for (int i = 0; i < 6 * 3; i += 3) {
					if (x + axes[i+0] >= 0 && x + axes[i+0] < cellsWide &&
							y + axes[i+1] >= 0 && y + axes[i+1] < cellsWide &&
							z + axes[i+2] >= 0 && z + axes[i+2] < cellsWide) {
						
						final int idx2 = idx(x + axes[i+0], y + axes[i+1], z + axes[i+2]);
						if (cellEnabled[idx2] != isEnabled) {
							break outerLoop;
						}
					}
				}
			} while (true);
			
			// Find second random cell on the surface of the heat sink opposite of the first cell
			int index2 = -1;
			outerLoop:
			do {
				final int x;
				final int y;
				final int z;
				if (enforceSymmetry) {
					x = (int)(Math.random() * (cellsWide/2 - airPadding) + airPadding);
					y = (int)(Math.random() * (cellsWide - airPadding));
					z = (int)(Math.random() * (x - airPadding) + airPadding);
				} else {
					x = (int)(Math.random() * (cellsWide - airPadding*2) + airPadding);
					y = (int)(Math.random() * (cellsWide - airPadding));
					z = (int)(Math.random() * (cellsWide - airPadding*2) + airPadding);
				}
				index2 = idx(x, y, z);
				
				// Skip this if both cells are part of the heat sink or part of the air.
				// This maintains constant volume.
				if (cellEnabled[index2] == cellEnabled[index1]) {
					continue;
				}
				
				final byte isEnabled = cellEnabled[index2];
				
				// If this cell is part of the surrounding air, then just skip it
				if (x < airPadding || x >= cellsWide-airPadding ||
						y >= cellsWide-airPadding ||
						z < airPadding || z >= cellsWide-airPadding) {
					continue;
				}
				
				// Skip if this cell is within the heat source
				if (y == 0 && x > airPadding*1.3 && x < cellsWide - airPadding*1.3 - 1 &&
						z > airPadding*1.3 && z < cellsWide - airPadding*1.3 - 1) {
					continue;
				}
				
				// Determine if this is a cell on the metal-air boundary
				for (int i = 0; i < 6 * 3; i += 3) {
					if (x + axes[i+0] >= 0 && x + axes[i+0] < cellsWide &&
							y + axes[i+1] >= 0 && y + axes[i+1] < cellsWide &&
							z + axes[i+2] >= 0 && z + axes[i+2] < cellsWide) {
						
						final int idx2 = idx(x + axes[i+0], y + axes[i+1], z + axes[i+2]);
						if (cellEnabled[idx2] != isEnabled) {
							break outerLoop;
						}
					}
				}
			} while (true);
			
			cellEnabled[index1] ^= 1;
			cellEnabled[index2] ^= 1;
		}

		// If we are enforcing symmetry, then reflect whole design 8x.
		if (enforceSymmetry) {
			for (int y = 0; y < cellsWide - airPadding; y++) {
				for (int x = 0; x < cellsWide/2; x++) {
					for (int z = 0; z < x; z++) {
						final int index1 = idx(x, y, z);
						final int index2 = idx(cellsWide - x - 1, y, z);
						final int index3 = idx(x, y, cellsWide - z - 1);
						final int index4 = idx(cellsWide - x - 1, y, cellsWide - z - 1);
						final int index5 = idx(z, y, x);
						final int index6 = idx(z, y, cellsWide - x - 1);
						final int index7 = idx(cellsWide - z - 1, y, x);
						final int index8 = idx(cellsWide - z - 1, y, cellsWide - x - 1);
						cellEnabled[index2] = cellEnabled[index1];
						cellEnabled[index3] = cellEnabled[index1];
						cellEnabled[index4] = cellEnabled[index1];
						cellEnabled[index5] = cellEnabled[index1];
						cellEnabled[index6] = cellEnabled[index1];
						cellEnabled[index7] = cellEnabled[index1];
						cellEnabled[index8] = cellEnabled[index1];
					}
				}
			}
		}
	}
	
	// Check the heat sink for disconnected components
	static boolean hasDisconnectedComponents() {
		
		for (int x = 0; x < cellsWide; x++) {
			for (int y = 0; y < cellsWide; y++) {
				for (int z = 0; z < cellsWide; z++) {
					disconnectedComponentTable[x][y][z] = false;
				}
			}
		}
		
		// Starting cell at the bottom center
		disconnectedComponentTable[cellsWide/2][0][cellsWide/2] = true;
		
		// Use breadth-first search to find disconnected components
		boolean foundNewCell = false;
		do {
			foundNewCell = false;
			for (int x = airPadding; x < cellsWide-airPadding; x++) {
				for (int y = 0; y < cellsWide-airPadding; y++) {
					for (int z = airPadding; z < cellsWide-airPadding; z++) {
						if (cellEnabled[idx(x, y, z)] == 1 && disconnectedComponentTable[x][y][z]) {
							// Mark the adjacent cells as being searched
							if (cellEnabled[idx(x+1, y, z)] == 1 && !disconnectedComponentTable[x+1][y][z]) {
								disconnectedComponentTable[x+1][y][z] = true;
								foundNewCell = true;
							}
							if (cellEnabled[idx(x, y+1, z)] == 1 && !disconnectedComponentTable[x][y+1][z]) {
								disconnectedComponentTable[x][y+1][z] = true;
								foundNewCell = true;
							}
							if (cellEnabled[idx(x, y, z+1)] == 1 && !disconnectedComponentTable[x][y][z+1]) {
								disconnectedComponentTable[x][y][z+1] = true;
								foundNewCell = true;
							}
							if (cellEnabled[idx(x-1, y, z)] == 1 && !disconnectedComponentTable[x-1][y][z]) {
								disconnectedComponentTable[x-1][y][z] = true;
								foundNewCell = true;
							}
							if (cellEnabled[idx(x, y-1, z)] == 1 && !disconnectedComponentTable[x][y-1][z] && y > 0) {
								disconnectedComponentTable[x][y-1][z] = true;
								foundNewCell = true;
							}
							if (cellEnabled[idx(x, y, z-1)] == 1 && !disconnectedComponentTable[x][y][z-1]) {
								disconnectedComponentTable[x][y][z-1] = true;
								foundNewCell = true;
							}
						}
					}
				}
			}
		} while (foundNewCell);
		
		// Now check if there are any disconnected components left
		for (int x = airPadding; x < cellsWide-airPadding; x++) {
			for (int y = 0; y < cellsWide-airPadding; y++) {
				for (int z = airPadding; z < cellsWide-airPadding; z++) {
					// If this cell has not been reached, then it must be disconnected
					if (cellEnabled[idx(x, y, z)] == 1 && !disconnectedComponentTable[x][y][z]) {
						return true;
					}
				}
			}
		}
		
		// No disconnected cell was found
		return false;
	}
	
	// Reset the cells to a configuration based on the given iteration
	static void setDesignIteration(int iteration) {
		for (int x = 0; x < cellsWide; x++) {
			for (int y = 0; y < cellsWide; y++) {
				for (int z = 0; z < cellsWide; z++) {
					final int i = idx(x, y, z);
					
					// If this is an inner cell
					cellEnabled[i] =
							(x >= airPadding && x < cellsWide-airPadding &&
							y < cellsWide-airPadding &&
							z >= airPadding && z < cellsWide-airPadding) ? (byte)1 : 0;
					cellHeat[i] = 0;
				}
			}
		}
		
		//*
		// Make a classic simple finned shape heat sink
		for (int x = 0; x < cellsWide; x++) {
			for (int y = 3; y < cellsWide; y++) {
				for (int z = 0; z < cellsWide; z++) {
					if (x % iteration != 0) {
						final int idx = idx(x, y, z);
						cellEnabled[idx] = 0;
					}
				}
			}
		}
		//*/
		
		//cellHeat[cellsWide3-1] = 80000;
		//cellHeat[idx(cellsWide/2, 0, cellsWide/2)] = 2000;
	}
	
	// Calculate long-term information about this heat sink for later
	static void preprocessDesign() {
		
		// Array to represent the 6 adjacent cells
		final byte[] axes = {
				1, 0, 0,
				-1, 0, 0,
				0, 1, 0,
				0, -1, 0,
				0, 0, 1,
				0, 0, -1
		};
		
		// Determine which cells actually need to be rendered (are not covered on all sides)
		// Determine which cells of the heat sink are on the metal-air boundary.
		for (int x = 0; x < cellsWide; x++) {
			for (int y = 0; y < cellsWide; y++) {
				toNextCell:
				for (int z = 0; z < cellsWide; z++) {
					final int idx = idx(x, y, z);
					
					if (cellEnabled[idx] == 0) {
						isCellOnBoundary[idx] = 0;
						continue;
					}
					
					// Compute the fraction of sides that are covered with another enabled cell
					isCellOnBoundary[idx] = 0;
					for (int i = 0; i < 6 * 3; i += 3) {
						if (x + axes[i+0] >= 0 && x + axes[i+0] < cellsWide &&
								y + axes[i+1] >= 0 && y + axes[i+1] < cellsWide &&
								z + axes[i+2] >= 0 && z + axes[i+2] < cellsWide) {
							
							final int idx2 = idx(x + axes[i+0], y + axes[i+1], z + axes[i+2]);
							if (cellEnabled[idx2] == 0) {
								isCellOnBoundary[idx] = 1;
								continue toNextCell;
							}
						} else {
							isCellOnBoundary[idx] = 1;
							continue toNextCell;
						}
					}
				}
			}
		}
		
		// Set the constants that don't chance for a certain heat sink design
		if (useGPU) {
			gpuProgram.setInputArg(4, cellEnabled);
			gpuProgram.setInOutArg(5, cellHeat);
			
			// This argument is only used as scratch space on the GPU.
			// We only need to allocate the space.
			gpuProgram.setInputArg(6, cellDeltaHeat);
		}
	}
	
	// Add heat to the heat sink from the bottom
	static void addHeat() {
		for (int x = (int)(airPadding*1.3f); x < cellsWide-airPadding*1.3f; x++) {
			for (int z = (int)(airPadding*1.3f); z < cellsWide-airPadding*1.3f; z++) {
				cellHeat[idx(x, 0, z)] += heatSourceHeatPerCell;
			}
		}
	}
	
	// Score the heat sink based on the total temperature of the base
	static double scoreHeatsink() {
		
		// Add up the heat over the same area that it is being heated.
		double totalHeat = 0;
		int count = 0;
		for (int x = (int)(airPadding*1.3f)+2; x < cellsWide-airPadding*1.3f-2; x++) {
			for (int z = (int)(airPadding*1.3f)+2; z < cellsWide-airPadding*1.3f-2; z++) {
				totalHeat += cellHeat[idx(x, 0, z)];
				count++;
			}
		}
		
		return totalHeat / count;
	}
	
	// Run the thermal simulation
	static double runSimulation(boolean isFirstIteration) {
		
		final int minimumIterations = 10 * cellsWide;
		int iterationsSinceReport = 0;
		int iterations = 0;
		long lastReportTime = System.currentTimeMillis();
		double previousBaseHeat = -999;
		
		// Array to represent the 6 adjacent cells
		final byte[] axes = {
				1, 0, 0,
				-1, 0, 0,
				0, 1, 0,
				0, -1, 0,
				0, 0, 1,
				0, 0, -1
		};
		
		// Reuse the temperature data from the previous iteration
		//for (int i = 0; i < cellsWide3; i++) {
		//	cellHeat[i] = 0;
		//}
		
		// Run the simulation forever
		while (true) {
			final long computeStartTime = System.currentTimeMillis();
			
			final boolean shouldComputeAir = iterations % airIterationSkips == 0;
			final boolean shouldComputeBoundary = iterations % boundaryIterationSkips == 0;

			if (useGPU) {
				gpuProgram.setInputArg(2, shouldComputeAir ? 1 : 0);
				gpuProgram.setInputArg(3, shouldComputeBoundary ? 1 : 0);
				//gpuProgram.setInOutArg(5, cellHeat);
				gpuProgram.setInputArg(7, (byte)0);
				gpuProgram.executeKernelNoWriteback();
				
				// Add the delta heat to the cell heat
				gpuProgram.setInputArg(7, (byte)1);
				gpuProgram.executeKernelNoWriteback();
				//gpuProgram.copyOutputsFromGPU();
			} else {
				
				// Only needs to be done on CPU.  This is done on the GPU kernel.
				addHeat();
				
				// Compute the diffusion of heat through air only.
				// Dispatch all of the threads.
				final int[] threadsRunning = {numThreads};
				for (int tempThreadNum = 0; tempThreadNum < numThreads; tempThreadNum++) {
					final int threadNum = tempThreadNum; // Fix bogus compile error
					new Thread(new Runnable() {
						public void run() {
							
							// Iterate over every cell and compute the heat gradient
							for (int x = threadNum; x < cellsWide; x += numThreads) {
								for (int y = 0; y < cellsWide; y++) {
									for (int z = 0; z < cellsWide; z++) {
										final int idx = idx(x, y, z);
										
										cellDeltaHeat[idx] = 0;
										
										// Iterate over the adjacent cells
										for (int i = 0; i < 6 * 3; i += 3) {
											// Check if this cell is in bounds
											if (x + axes[i+0] >= 0 && x + axes[i+0] < cellsWide &&
													y + axes[i+1] >= 0 && y + axes[i+1] < cellsWide &&
													z + axes[i+2] >= 0 && z + axes[i+2] < cellsWide) {
												
												final int idx2 = idx(x + axes[i+0], y + axes[i+1], z + axes[i+2]);
												
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
												// This cell is not in bounds, so apply boundary conditions.
												// The border heat is assumed to be ambient temperature air (0 degrees).
												cellDeltaHeat[idx] -= cellHeat[idx] * conductivity;
											}
										}
									}
								}
							}
							
							synchronized (HeatsinkMain.class) {
								threadsRunning[0]--;
							}
						}
					}).start();
				}
				
				// Wait for the threads to finish
				try {
					while (threadsRunning[0] > 0) {
						Thread.sleep(0);
					}
				} catch (Exception e) {}
				
				// Add the delta values to the actual values.
				for (int i = 0; i < cellsWide3; i++) {
					cellHeat[i] += cellDeltaHeat[i];
				}
			}
			
			// Total up the heat for debugging
			/*
			if (iterations % 100 == 0) {
				double total = 0;
				for (int i = 0; i < cellsWide3; i++) {
					total += cellHeat[i];
				}
				print(total);
			}
			//*/
			
			final boolean shouldUpdateScore = shouldComputeAir && shouldComputeBoundary;
			
			final long currentTime = System.currentTimeMillis();
			iterationsSinceReport++;
			
			// Only update the score on iterations when everything has been updated
			if (shouldUpdateScore) {
				if (useGPU) {
					// Copy calculated outputs back to main memory (for visualization and analysis)
					gpuProgram.copyOutputsFromGPU();
				}
				
				final double baseHeat = scoreHeatsink();
				if (baseHeat < 0.00001 && isFirstIteration) {
					System.err.println("Base heat is too small: " + baseHeat);
					System.exit(1);
				}
				
				final double deltaBaseHeat = Math.abs(baseHeat - previousBaseHeat) / previousBaseHeat;
				previousBaseHeat = baseHeat;
				
				// Periodically print out statistics
				if (currentTime - lastReportTime > 2000) {
					long iterationsPerSec = 1000 * iterationsSinceReport / (currentTime - lastReportTime);
					print("IPS: " + iterationsPerSec + " Change: " + deltaBaseHeat);
					lastReportTime = currentTime;
					iterationsSinceReport = 0;
				}
				
				// Determine if the simulation has hit equilibrium, and has exceeded minimum iterations
				//final double maxError = isFirstIteration ? 2e-6 : 4e-6; // good for 96x96x96
				final double maxError = isFirstIteration ? 2e-7 : 4e-7; // good for extrusion designs
				if (iterations > minimumIterations && deltaBaseHeat < maxError) {
					if (iterations < minimumIterations*1.2) {
						System.err.println("WARNING: Convergence threshold may be too high");
					}
					return baseHeat;
				}
			}
			
			// Slow down the program to achieve 60 fps if possible
			if (restrictTo60FPS) {
				if (useGPU) {
					// Copy calculated outputs back to main memory (for visualization only)
					gpuProgram.copyOutputsFromGPU();
				}
				
				final long sleepDuration = 17 - (currentTime - computeStartTime);
				if (sleepDuration > 0) {
					sleep(sleepDuration);
				}
			}
			
			iterations++;
		}
	}
	
	// Convert an xyz coordinate to an index in the array
	static int idx(int x, int y, int z) {
		//return x * cellsWide * cellsWide + y * cellsWide + z;
		return (x * cellsWide + y) * cellsWide + z;
	}
	
	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (Exception e) {}
	}
	
	static void print(Object o) {
		System.out.println(o);
	}

}
