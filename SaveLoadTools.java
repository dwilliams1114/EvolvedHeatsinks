package heatsinkDesign;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

// This class exports the heat sink design as an OBJ file

import de.javagl.obj.Obj;
import de.javagl.obj.ObjWriter;
import de.javagl.obj.Objs;

public class SaveLoadTools {
	
	static void exportCurrentHeatsink() {
		System.out.println("Saving mesh file...");
		
		// Count the number of enabled cells
		int cellCount = 0;
		for (int x = 0; x < HeatsinkMain.cellsWide; x++) {
			for (int y = 0; y < HeatsinkMain.cellsWide; y++) {
				for (int z = 0; z < HeatsinkMain.cellsWide; z++) {
					final int idx = HeatsinkMain.idx(x, y, z);
					
					// If this cell is not enabled, then don't render it
					if (HeatsinkMain.cellEnabled[idx] == 1 && HeatsinkMain.isCellOnBoundary[idx] == 1) {
						cellCount++;
					}
				}
			}
		}
		
		final float[] vertices = new float[cellCount * 18 * 6];
		
		int i = 0;
		for (int x = 0; x < HeatsinkMain.cellsWide; x++) {
			for (int y = 0; y < HeatsinkMain.cellsWide; y++) {
				for (int z = 0; z < HeatsinkMain.cellsWide; z++) {
					final int idx = HeatsinkMain.idx(x, y, z);
					
					// If this cell is not enabled, then don't render it
					if (HeatsinkMain.cellEnabled[idx] == 1 && HeatsinkMain.isCellOnBoundary[idx] == 1) {
						
						// Front
						vertices[i++] = x+1;
						vertices[i++] = y;
						vertices[i++] = z+1;
						vertices[i++] = x+1;
						vertices[i++] = y+1;
						vertices[i++] = z+1;
						vertices[i++] = x;
						vertices[i++] = y+1;
						vertices[i++] = z+1;
						vertices[i++] = x+1;
						vertices[i++] = y;
						vertices[i++] = z+1;
						vertices[i++] = x;
						vertices[i++] = y;
						vertices[i++] = z+1;
						vertices[i++] = x;
						vertices[i++] = y+1;
						vertices[i++] = z+1;
						
						// Back
						vertices[i++] = x;
						vertices[i++] = y;
						vertices[i++] = z;
						vertices[i++] = x;
						vertices[i++] = y+1;
						vertices[i++] = z;
						vertices[i++] = x+1;
						vertices[i++] = y+1;
						vertices[i++] = z;
						vertices[i++] = x;
						vertices[i++] = y;
						vertices[i++] = z;
						vertices[i++] = x+1;
						vertices[i++] = y;
						vertices[i++] = z;
						vertices[i++] = x+1;
						vertices[i++] = y+1;
						vertices[i++] = z;
						
						// Right
						vertices[i++] = x+1;
						vertices[i++] = y;
						vertices[i++] = z;
						vertices[i++] = x+1;
						vertices[i++] = y+1;
						vertices[i++] = z;
						vertices[i++] = x+1;
						vertices[i++] = y+1;
						vertices[i++] = z+1;
						vertices[i++] = x+1;
						vertices[i++] = y;
						vertices[i++] = z;
						vertices[i++] = x+1;
						vertices[i++] = y;
						vertices[i++] = z+1;
						vertices[i++] = x+1;
						vertices[i++] = y+1;
						vertices[i++] = z+1;
						
						// Left
						vertices[i++] = x;
						vertices[i++] = y;
						vertices[i++] = z+1;
						vertices[i++] = x;
						vertices[i++] = y+1;
						vertices[i++] = z+1;
						vertices[i++] = x;
						vertices[i++] = y+1;
						vertices[i++] = z;
						vertices[i++] = x;
						vertices[i++] = y;
						vertices[i++] = z+1;
						vertices[i++] = x;
						vertices[i++] = y;
						vertices[i++] = z;
						vertices[i++] = x;
						vertices[i++] = y+1;
						vertices[i++] = z;
						
						// Bottom
						vertices[i++] = x+1;
						vertices[i++] = y;
						vertices[i++] = z+1;
						vertices[i++] = x;
						vertices[i++] = y;
						vertices[i++] = z+1;
						vertices[i++] = x;
						vertices[i++] = y;
						vertices[i++] = z;
						vertices[i++] = x+1;
						vertices[i++] = y;
						vertices[i++] = z+1;
						vertices[i++] = x+1;
						vertices[i++] = y;
						vertices[i++] = z;
						vertices[i++] = x;
						vertices[i++] = y;
						vertices[i++] = z;
						
						// Top
						vertices[i++] = x+1;
						vertices[i++] = y+1;
						vertices[i++] = z;
						vertices[i++] = x;
						vertices[i++] = y+1;
						vertices[i++] = z;
						vertices[i++] = x;
						vertices[i++] = y+1;
						vertices[i++] = z+1;
						vertices[i++] = x+1;
						vertices[i++] = y+1;
						vertices[i++] = z;
						vertices[i++] = x+1;
						vertices[i++] = y+1;
						vertices[i++] = z+1;
						vertices[i++] = x;
						vertices[i++] = y+1;
						vertices[i++] = z+1;
					}
				}
			}
		}
		i = -1;
		
		int[] indices = new int[vertices.length/3];
		for (int j = 0; j < indices.length; j++) {
			indices[j] = j;
		}
		
		final Obj obj = Objs.createFromIndexedTriangleData(
				IntBuffer.wrap(indices), 
				FloatBuffer.wrap(vertices),
				null,
				null);
		
		// Write an OBJ file
		try {
			OutputStream objOutputStream = new FileOutputStream("src/heatsinkDesign/HeatSinkMesh.obj");
			ObjWriter.write(obj, objOutputStream);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println("Done saving mesh file");
	}
	
	static void loadHeatsink(String name) {
		System.out.println("Loading heat sink...");
		
		try {
			DataInputStream os = new DataInputStream(new FileInputStream(
					"src/heatsinkDesign/" + name));
			
			final int cellsWide = os.readInt();
			if (HeatsinkMain.cellsWide != cellsWide) {
				System.err.println("Cells wide doesn't match: " + HeatsinkMain.cellsWide + ", " + cellsWide);
				System.exit(1);
			}
			final int airPadding = os.readInt();
			if (HeatsinkMain.airPadding != airPadding) {
				System.err.println("Air padding doesn't match: " + HeatsinkMain.airPadding + ", " + airPadding);
				System.exit(1);
			}
			
			for (int x = 0; x < HeatsinkMain.cellsWide; x++) {
				for (int y = 0; y < HeatsinkMain.cellsWide; y++) {
					for (int z = 0; z < HeatsinkMain.cellsWide; z += 8) {
						byte data = os.readByte();
						HeatsinkMain.cellEnabled[HeatsinkMain.idx(x, y, z+0)] = (byte)((data >> 0) & 1);
						HeatsinkMain.cellEnabled[HeatsinkMain.idx(x, y, z+1)] = (byte)((data >> 1) & 1);
						HeatsinkMain.cellEnabled[HeatsinkMain.idx(x, y, z+2)] = (byte)((data >> 2) & 1);
						HeatsinkMain.cellEnabled[HeatsinkMain.idx(x, y, z+3)] = (byte)((data >> 3) & 1);
						HeatsinkMain.cellEnabled[HeatsinkMain.idx(x, y, z+4)] = (byte)((data >> 4) & 1);
						HeatsinkMain.cellEnabled[HeatsinkMain.idx(x, y, z+5)] = (byte)((data >> 5) & 1);
						HeatsinkMain.cellEnabled[HeatsinkMain.idx(x, y, z+6)] = (byte)((data >> 6) & 1);
						HeatsinkMain.cellEnabled[HeatsinkMain.idx(x, y, z+7)] = (byte)((data >> 7) & 1);
					}
				}
			}
			
			os.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		System.out.println("Loaded heat sink");
	}
	
	static void saveCurrentHeatsinkAsBinary() {
		System.out.println("Saving heat sink binary data...");
		
		// Make sure the simulation width is divisible by 8
		int cleverTrick = HeatsinkMain.cellsWide;
		if (cleverTrick % 8 != 0) { 
			System.err.println("Simulation space must be divisible by 8");
			return;
		}
		
		try {
			DataOutputStream os = new DataOutputStream(new FileOutputStream(
					"src/heatsinkDesign/HeatSinkData.txt"));
			
			os.writeInt(HeatsinkMain.cellsWide);
			os.writeInt(HeatsinkMain.airPadding);
			
			for (int x = 0; x < HeatsinkMain.cellsWide; x++) {
				for (int y = 0; y < HeatsinkMain.cellsWide; y++) {
					for (int z = 0; z < HeatsinkMain.cellsWide; z += 8) {
						byte data = (byte)(HeatsinkMain.cellEnabled[HeatsinkMain.idx(x, y, z)] |
								(HeatsinkMain.cellEnabled[HeatsinkMain.idx(x, y, z+1)] << 1) |
								(HeatsinkMain.cellEnabled[HeatsinkMain.idx(x, y, z+2)] << 2) |
								(HeatsinkMain.cellEnabled[HeatsinkMain.idx(x, y, z+3)] << 3) |
								(HeatsinkMain.cellEnabled[HeatsinkMain.idx(x, y, z+4)] << 4) |
								(HeatsinkMain.cellEnabled[HeatsinkMain.idx(x, y, z+5)] << 5) |
								(HeatsinkMain.cellEnabled[HeatsinkMain.idx(x, y, z+6)] << 6) |
								(HeatsinkMain.cellEnabled[HeatsinkMain.idx(x, y, z+7)] << 7));
						
						os.writeByte(data);
					}
				}
			}
			
			os.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Saved heat sink data");
	}
}
