package heatsinkDesign;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

// Created by Daniel Williams
// Created on November 13, 2020
// Last updated on November 16, 2020
// Designed to visualize a voxel grid using ray tracing

public class VoxelRayTracer {
	private static int X = 800;
	private static int Y = 600;
	private static float aspect = (float)Y/X;
	private static float camMoveSpeed = 0.15f;
	private static Vector3 camVelocity = new Vector3();
	private static Vector3 camFocus = new Vector3(
			HeatsinkMain.cellsWide/2d, HeatsinkMain.cellsWide/5d, HeatsinkMain.cellsWide/2d);
	private static long lastRenderTime = System.currentTimeMillis();
	private static double fps = 30;
	private static Vector3 lightVec = new Vector3(-0.4, -0.6, 0.3).normalized();
	private static float rotx = -0.4f;
	private static float roty = -1.9f;
	private static float zoom = 86 + HeatsinkMain.cellsWide * 0.66f;
	private static int ambientOcclusionSamples = 32;
	private static final double maxOccludeDist = 12;
	private static final double ambientBrightness = 0.14;
	private static final int numThreads = 6;
	private static int numThreadsRunning = 0;
	private static String saveDir = null;

	private static JFrame frame;
	private static JLabel renderLabel;
	private static BufferedImage renderImage;
	private static byte[] oldImage1;
	private static byte[] oldImage2;
	private static byte[] oldImage3;
	private static float[] depthMap;
	private static byte[] normalIdMap;
	private static boolean didViewChange = false;
	private static int prevMouseX, prevMouseY;
	
	// Called from another application to start the render loop
	static void initialize(String savePath) {
		
		saveDir = savePath;
		if (saveDir != null && !saveDir.endsWith("/")) {
			saveDir += "/";
		}
		
		frame = new JFrame("Heat Sink Render");
		renderImage = new BufferedImage(X, Y, BufferedImage.TYPE_3BYTE_BGR);
		oldImage1 = new byte[X * Y * 3];
		oldImage2 = new byte[X * Y * 3];
		oldImage3 = new byte[X * Y * 3];
		depthMap = new float[X * Y];
		normalIdMap = new byte[X * Y];
		renderLabel = new JLabel(new ImageIcon(renderImage));
		
		addInputListeners();
		
		frame.add(renderLabel);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setResizable(false);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		
		// Start the rendering in a new thread
		new Thread(new Runnable() {
			public void run() {
				int iteration = 0;
				while (true) {
					
					// Add a delay regardless to make sure the computation threads have time
					try {
						// Sleep for a long time if the window is minimized
						if (frame.getExtendedState() == JFrame.ICONIFIED) {
							Thread.sleep(6 * 1000);
						}
						
						Thread.sleep(16);
						for (int i = 0; i < 5; i++) {
							if (!didViewChange) {
								Thread.sleep(16);
							} else {
								didViewChange = false;
								break;
							}
						}
					} catch (Exception e) {}
					
					render(iteration);
					iteration++;
				}
			}
		}).start();
	}
	
	// Allow the user to control the scene
	private static void addInputListeners() {
		
		// Create the input listeners
		renderLabel.addMouseListener(new MouseListener() {
			
			public void mouseReleased(MouseEvent e) {}
			
			public void mousePressed(MouseEvent e) {
				prevMouseX = e.getX();
				prevMouseY = e.getY();
			}
			
			public void mouseExited(MouseEvent e) {}
			
			public void mouseEntered(MouseEvent e) {}
			
			public void mouseClicked(MouseEvent e) {}
		});
		
		renderLabel.addMouseMotionListener(new MouseMotionListener() {
			public void mouseMoved(MouseEvent e) {}
			
			public void mouseDragged(MouseEvent e) {
				final int x = e.getX();
				final int y = e.getY();
				float thetaY = 0.01f * (x - prevMouseX);
				float thetaX = -0.01f * (y - prevMouseY);
				
				prevMouseX = x;
				prevMouseY = y;
				
				rotx += thetaX;
				roty += thetaY;
				
				didViewChange = true;
			}
		});
		
		renderLabel.addMouseWheelListener(new MouseWheelListener() {
			public void mouseWheelMoved(MouseWheelEvent e) {
				float delta = e.getWheelRotation();
				if (delta < 0) {
					zoom *= 0.9;
					if (zoom < 5) {
						zoom = 5;
					}
				} else {
					zoom /= 0.9;
					if (zoom > 500) {
						zoom = 500;
					}
				}
				didViewChange = true;
			}
		});
		
		frame.addKeyListener(new KeyListener() {
			public void keyPressed(KeyEvent e) {
				if (e.getKeyChar() == 'w') {
					camVelocity.z = -1;
					didViewChange = true;
				} else if (e.getKeyChar() == 's') {
					camVelocity.z = 1;
					didViewChange = true;
				} else if (e.getKeyChar() == 'a') {
					camVelocity.x = -1;
					didViewChange = true;
				} else if (e.getKeyChar() == 'd') {
					camVelocity.x = 1;
					didViewChange = true;
				} else if (e.getKeyChar() == 'e') {
					camVelocity.y = 1;
					didViewChange = true;
				} else if (e.getKeyChar() == 'q') {
					camVelocity.y = -1;
					didViewChange = true;
				}
			}
			
			public void keyReleased(KeyEvent e) {
				if (e.getKeyChar() == 'w') {
					camVelocity.z = 0;
				} else if (e.getKeyChar() == 's') {
					camVelocity.z = 0;
				} else if (e.getKeyChar() == 'a') {
					camVelocity.x = 0;
				} else if (e.getKeyChar() == 'd') {
					camVelocity.x = 0;
				} else if (e.getKeyChar() == 'e') {
					camVelocity.y = 0;
				} else if (e.getKeyChar() == 'q') {
					camVelocity.y = 0;
				}
			}
			
			public void keyTyped(KeyEvent e) {}
		});
	}
	
	// Perform all of the raytracing
	public static void render(int iteration) {
		
		// Camera forward vector
		final Vector3 forward = Rotate3D.getVectorFromRotation(rotx, roty, 0);
		
		// Camera left vector
		float leftX = (float)(-Math.cos(roty));
		float leftZ = (float)(Math.sin(roty));
		final Vector3 left = new Vector3(leftX, 0, leftZ).normalized();
		
		// Camera up vector
		final Vector3 up = left.cross(forward).normalized();
		
		// Make sure the graphics update rapidly when the camera is moving
		if (camVelocity.isNonZero()) {
			didViewChange = true;
		}
		
		// Update the camera to its new position
		camFocus = camFocus.sub(left.mult(camVelocity.x).sub(up.mult(camVelocity.y).
				sub(forward.mult(camVelocity.z))).mult(camMoveSpeed/fps*60));
		
		final Vector3 camPos = camFocus.sub(forward.mult(zoom));
		final double tempRotX = rotx;
		final double tempRotY = roty;
		
		final byte[] pixels = ((DataBufferByte)renderImage.getRaster().getDataBuffer()).getData();
		
		numThreadsRunning = numThreads;
		for (int k = 0; k < numThreads; k++) {
			final int threadOffset = k;
			new Thread(new Runnable() {
				public void run() {
					for (int x = threadOffset * 2; x < X; x += numThreads * 2) {
						for (int y = 0; y < Y; y += 2) {
							
							// Offsets for temporal anti-aliasing
							float temporalX = (iteration % 2) / 2f;
							float temporalY = (iteration/2 % 2) / 2f;
							
							// Calculate the color at this coordinate
							final RayResult hitInfo = calcPixelColor(
										x + temporalX, y + temporalY, camPos, tempRotX, tempRotY);
							
							// Write to the depth map
							depthMap[y * X + x] = (float)hitInfo.depth;
							depthMap[(y+1) * X + x] = (float)hitInfo.depth;
							depthMap[y * X + (x+1)] = (float)hitInfo.depth;
							depthMap[(y+1) * X + (x+1)] = (float)hitInfo.depth;
							
							// Write to the screen-space normal map
							byte normalId = -1;
							if (hitInfo.norm != null) {
								normalId = (byte)((int)(hitInfo.norm.x+1.1) +
										(int)(hitInfo.norm.y+1.1)*3 + (int)(hitInfo.norm.z+1.1)*9);
							}
							normalIdMap[y * X + x] = normalId;
							normalIdMap[(y+1) * X + x] = normalId;
							normalIdMap[y * X + (x+1)] = normalId;
							normalIdMap[(y+1) * X + (x+1)] = normalId;
							
							pixels[(y * X + x) * 3 + 0] = (byte)hitInfo.color.z;
							pixels[(y * X + x) * 3 + 1] = (byte)hitInfo.color.y;
							pixels[(y * X + x) * 3 + 2] = (byte)hitInfo.color.x;
							
							pixels[((y+1) * X + x) * 3 + 0] = (byte)hitInfo.color.z;
							pixels[((y+1) * X + x) * 3 + 1] = (byte)hitInfo.color.y;
							pixels[((y+1) * X + x) * 3 + 2] = (byte)hitInfo.color.x;
							
							pixels[(y * X + (x+1)) * 3 + 0] = (byte)hitInfo.color.z;
							pixels[(y * X + (x+1)) * 3 + 1] = (byte)hitInfo.color.y;
							pixels[(y * X + (x+1)) * 3 + 2] = (byte)hitInfo.color.x;
							
							pixels[((y+1) * X + (x+1)) * 3 + 0] = (byte)hitInfo.color.z;
							pixels[((y+1) * X + (x+1)) * 3 + 1] = (byte)hitInfo.color.y;
							pixels[((y+1) * X + (x+1)) * 3 + 2] = (byte)hitInfo.color.x;
						}
					}
					
					synchronized (VoxelRayTracer.class) {
						numThreadsRunning--;
					}
				}
			}).start();
		}
		
		// Wait for all the threads to finish
		try {
			while (numThreadsRunning > 0) {
				Thread.sleep(0);
			}
		} catch (Exception e) {}
		
		denoiseImage(pixels);
		
		for (int x = 0; x < X; x += 2) {
			for (int y = 0; y < Y; y += 2) {
				updateMovingAverageColor(pixels, x, y);
				updateMovingAverageColor(pixels, x, y+1);
				updateMovingAverageColor(pixels, x+1, y);
				updateMovingAverageColor(pixels, x+1, y+1);
			}
		}
		
		renderLabel.paintImmediately(0, 0, X, Y);
		
		long timeDelay = System.currentTimeMillis() - lastRenderTime;
		fps = 1000.0 / timeDelay;
		lastRenderTime = System.currentTimeMillis();
		//print("Render time: " + timeDelay + "ms");
		
		if (saveDir != null) {
			try {
				ImageIO.write(renderImage, "png", new File(saveDir + "frame" + iteration + ".png"));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	// Contribute this color to the image at this pixel
	private static void updateMovingAverageColor(byte[] pixels, int x, int y) {
		/*
		pixels[(y * X + x) * 3 + 0] = (byte)color.z;
		pixels[(y * X + x) * 3 + 1] = (byte)color.y;
		pixels[(y * X + x) * 3 + 2] = (byte)color.x;
		*/
		
		/*
		double a = 0.8;
		pixels[(y * X + x) * 3 + 0] = (byte)(fixByte(pixels[(y * X + x) * 3 + 0]) * a + color.z * (1-a));
		pixels[(y * X + x) * 3 + 1] = (byte)(fixByte(pixels[(y * X + x) * 3 + 1]) * a + color.y * (1-a));
		pixels[(y * X + x) * 3 + 2] = (byte)(fixByte(pixels[(y * X + x) * 3 + 2]) * a + color.x * (1-a));
		*/
		
		final int i = (y * X + x) * 3;
		
		final byte newPixelColorB = pixels[i + 0];
		final byte newPixelColorG = pixels[i + 1];
		final byte newPixelColorR = pixels[i + 2];
		
		if (didViewChange) {
			
		} else {
			pixels[i + 0] = (byte)((fixByte(oldImage1[i + 0]) + fixByte(oldImage2[i + 0])
							+ fixByte(oldImage3[i + 0]) + fixByte(newPixelColorB))/4d);
			pixels[i + 1] = (byte)((fixByte(oldImage1[i + 1]) + fixByte(oldImage2[i + 1])
							+ fixByte(oldImage3[i + 1]) + fixByte(newPixelColorG))/4d);
			pixels[i + 2] = (byte)((fixByte(oldImage1[i + 2]) + fixByte(oldImage2[i + 2])
							+ fixByte(oldImage3[i + 2]) + fixByte(newPixelColorR))/4d);
		}
		
		oldImage1[i + 0] = oldImage2[i + 0];
		oldImage1[i + 1] = oldImage2[i + 1];
		oldImage1[i + 2] = oldImage2[i + 2];
		
		oldImage2[i + 0] = oldImage3[i + 0];
		oldImage2[i + 1] = oldImage3[i + 1];
		oldImage2[i + 2] = oldImage3[i + 2];
		
		oldImage3[i + 0] = newPixelColorB;
		oldImage3[i + 1] = newPixelColorG;
		oldImage3[i + 2] = newPixelColorR;
	}
	
	// Calculate the color of a single pixel
	private static RayResult calcPixelColor(float screenX, float screenY, Vector3 origin, double rotX, double rotY) {
		// Create the ray that projects from the camera at this screen coordinate
		Vector3 rayVec = new Vector3((double)screenX/X-0.5, -((double)screenY/X-0.5*aspect), 1).normalized();
		rayVec = Rotate3D.rotatePointX(rayVec, -rotX);
		rayVec = Rotate3D.rotatePointY(rayVec, rotY);
		
		final Vector3 norm = new Vector3();
		final Vector3 hit = new Vector3();
		final RayResult hitResult = rayHeatSinkIntersection(rayVec, origin, hit, norm, 999);
		
		// If there was a hit
		if (hitResult != null) {
			
			// Illuminate using sunlight
			double lightDot = norm.dot(lightVec);
			if (lightDot < ambientBrightness) {
				lightDot = ambientBrightness;
			} else if (lightDot > 1) {
				lightDot = 1;
			}
			
			// A nice looking ramp function
			double a = Math.tanh(lightDot) * 1.313034 * (1 - ambientBrightness) + ambientBrightness;
			
			if (ambientOcclusionSamples != 0) {
				// Compute ambient occlusion
				int skyHits = 0;
				for (int i = 0; i < ambientOcclusionSamples; i++) {
					Vector3 randVec = new Vector3(Math.random()-0.5, Math.random()-0.5, Math.random()-0.5).normalized();
					
					// Make sure the new vector is within +-90 degrees of the hit normal
					double dot = randVec.dot(norm);
					if (dot > 0) {
						randVec.xMult(-1);
					}
					
					final Vector3 newNorm = new Vector3();
					final Vector3 newHit = new Vector3();
					final Vector3 startPoint = randVec.mult(0.001);
					startPoint.xAdd(hit);
					final RayResult newHitResult = rayHeatSinkIntersection(
								randVec, startPoint, newHit, newNorm, maxOccludeDist);
					
					// If this was a hit
					if (newHitResult == null) {
						skyHits++;
					}
				}
				
				final double occlusionMult = 0.1 + 0.9 * skyHits / ambientOcclusionSamples;
				
				// Add the ambient occlusion
				a *= occlusionMult;
			}
			
			// Color of the material
			Vector3 color = new Vector3(255, 255, 255);
			
			// Shade according to angle from sun
			color.xMult(a);
			
			return new RayResult(hit, norm, color, hitResult.depth);
		} else { // No hit
			
			// Sky color
			final Vector3 skyColor = new Vector3(220, 242, 255);
			
			return new RayResult(null, null, skyColor, 9999);
		}
	}
	
	// Compute a ray to heat sink intersection.
	// Hit and normal are returned in outHit and outNorm.
	// Return the hit distance (approximate), or 0 for no hit.
	private static RayResult rayHeatSinkIntersection(Vector3 rayVec,
			Vector3 rayOrigin, Vector3 outHit, Vector3 outNorm, double maxDist) {
		final double e = 1e-8f; // Epsilon error
		
		final double minX = HeatsinkMain.airPadding;
		final double minY = 0;
		final double minZ = HeatsinkMain.airPadding;
		final double maxX = HeatsinkMain.cellsWide - HeatsinkMain.airPadding + e*2;
		final double maxY = HeatsinkMain.cellsWide - HeatsinkMain.airPadding + e*2;
		final double maxZ = HeatsinkMain.cellsWide - HeatsinkMain.airPadding + e*2;
		
		Vector3 closestHit = null;
		Vector3 closestNorm = null;
		double minSquareDist = 9999999;
		
		Vector3 norm = new Vector3(rayVec.x > 0 ? 1 : -1, 0, 0);
		for (int x = HeatsinkMain.airPadding; x <= HeatsinkMain.cellsWide - HeatsinkMain.airPadding; x++) {
			
			// Compute the intersection between the ray and the plane
			final double newX = rayVec.x > 0 ? x : HeatsinkMain.cellsWide - x;
			final double multiplier = (newX - rayOrigin.x) / rayVec.x;
			if (multiplier < 0) { // If this hit is not going in the right direction
				continue;
			}
			final Vector3 hit = rayVec.mult(multiplier);
			hit.xAdd(rayOrigin.x + e, rayOrigin.y + e, rayOrigin.z + e);
			
			if (hit.x > minX && hit.y > minY && hit.z > minZ && hit.x < maxX && hit.y < maxY && hit.z < maxZ) {
				
				// If this hit is already farther than the best, then skip all other intersections.
				// They can only get farther away.
				final double squareDist = hit.squareDist(rayOrigin);
				if (squareDist > minSquareDist || squareDist > maxDist * maxDist) {
					break;
				}
				
				// If this cell is enabled (rendered from either side)
				final int index = HeatsinkMain.idx((int)hit.x, (int)hit.y, (int)hit.z);
				final int index2 = HeatsinkMain.idx((int)(hit.x - 1), (int)hit.y, (int)hit.z);
				if (HeatsinkMain.cellEnabled[index] == 1 || HeatsinkMain.cellEnabled[index2] == 1) {
					
					// If this is a closer intersection
					if (squareDist < minSquareDist) {
						minSquareDist = squareDist;
						closestHit = hit;
						closestNorm = norm;
					}
					
					// The next iterations can only be farther away
					break;
				}
			}
		}
		
		norm = new Vector3(0, rayVec.y > 0 ? 1 : -1, 0);
		for (int y = 0; y <= HeatsinkMain.cellsWide - HeatsinkMain.airPadding; y++) {
			
			// Compute the intersection between the ray and the plane
			final double newY = rayVec.y > 0 ? y : HeatsinkMain.cellsWide - HeatsinkMain.airPadding - y;
			final double multiplier = (newY - rayOrigin.y) / rayVec.y;
			if (multiplier < 0) { // If this hit is not going in the right direction
				continue;
			}
			final Vector3 hit = rayVec.mult(multiplier);
			hit.xAdd(rayOrigin.x + e, rayOrigin.y + e, rayOrigin.z + e);
			
			if (hit.x > minX && hit.y > minY && hit.z > minZ && hit.x < maxX && hit.y < maxY && hit.z < maxZ) {
				
				// If this hit is already farther than the best, then skip all other intersections.
				// They can only get farther away.
				final double squareDist = hit.squareDist(rayOrigin);
				if (squareDist > minSquareDist || squareDist > maxDist * maxDist) {
					break;
				}
				
				// If this cell is enabled
				final int index = HeatsinkMain.idx((int)hit.x, (int)hit.y, (int)hit.z);
				final int index2 = HeatsinkMain.idx((int)hit.x, (int)(hit.y - 1), (int)hit.z);
				if (HeatsinkMain.cellEnabled[index] == 1 || HeatsinkMain.cellEnabled[index2] == 1) {
					
					// If this is a closer intersection
					if (squareDist < minSquareDist) {
						minSquareDist = squareDist;
						closestHit = hit;
						closestNorm = norm;
					}
					
					// The next iterations can only be farther away
					break;
				}
			}
		}
		
		norm = new Vector3(0, 0, rayVec.z > 0 ? 1 : -1);
		for (int z = HeatsinkMain.airPadding; z <= HeatsinkMain.cellsWide - HeatsinkMain.airPadding; z++) {
			
			// Compute the intersection between the ray and the plane
			final double newZ = rayVec.z > 0 ? z : HeatsinkMain.cellsWide - z;
			final double multiplier = (newZ - rayOrigin.z) / rayVec.z;
			if (multiplier < 0) { // If this hit is not going in the right direction
				continue;
			}
			final Vector3 hit = rayVec.mult(multiplier);
			hit.xAdd(rayOrigin.x + e, rayOrigin.y + e, rayOrigin.z + e);
			
			if (hit.x > minX && hit.y > minY && hit.z > minZ && hit.x < maxX && hit.y < maxY && hit.z < maxZ) {
				
				// If this hit is already farther than the best, then skip all other intersections.
				// They can only get farther away.
				final double squareDist = hit.squareDist(rayOrigin);
				if (squareDist > minSquareDist || squareDist > maxDist * maxDist) {
					break;
				}
				
				// If this cell is enabled
				final int index = HeatsinkMain.idx((int)hit.x, (int)hit.y, (int)hit.z);
				final int index2 = HeatsinkMain.idx((int)hit.x, (int)hit.y, (int)(hit.z - 1));
				if (HeatsinkMain.cellEnabled[index] == 1 || HeatsinkMain.cellEnabled[index2] == 1) {
					
					// If this is a closer intersection
					if (squareDist < minSquareDist) {
						minSquareDist = squareDist;
						closestHit = hit;
						closestNorm = norm;
					}
					
					// The next iterations can only be farther away
					break;
				}
			}
		}
		
		if (closestHit != null) {
			outHit.x = closestHit.x;
			outHit.y = closestHit.y;
			outHit.z = closestHit.z;
			outNorm.x = closestNorm.x;
			outNorm.y = closestNorm.y;
			outNorm.z = closestNorm.z;
			
			// Perform a fast square root on minSquareDist
			double guess = 2.4 * (1 - 1/(minSquareDist + 1));
			double a = (minSquareDist - guess * guess)/2/guess;
			double c = 2.8 / (minSquareDist + 0.04);
			double dist = guess + a - a * a / c / (minSquareDist + a) / (minSquareDist + 1/c);
			
			return new RayResult(closestHit, closestNorm, null, dist);
		}
		return null;
	}
	
	/*
	// Line-plane intersection
	private static Vector3 linePlaneIntersection(Vector3 planePoint,
			Vector3 planeNormal, Vector3 lineVec, Vector3 linePoint) {
		double a = planePoint.sub(linePoint).dot(planeNormal);
		double b = lineVec.dot(planeNormal);
		
		Vector3 tempVec = lineVec.mult(a / b);
		
		return linePoint.add(tempVec);
	}
	*/
	
	// Denoise the given image using a simple blur that
	//    conditionally depends on depth map and normal id.
	private static void denoiseImage(byte[] pixels) {
		
		// List of adjacent pixels to blur
		final int[] offsets = {
				-1, -1,
				-1, 0,
				-1, 1,
				0, -1,
				0, 0,
				0, 1,
				1, -1,
				1, 0,
				1, 1,
				0, 2,
				0, -2,
				2, 0,
				-2, 0
		};
		
		for (int x = 2; x < X-2; x += 2) {
			for (int y = 2; y < Y-2; y += 2) {
				
				// Add the central pixel
				float b = 0;
				float g = 0;
				float r = 0;
				int sampleCount = 0;
				
				float d = depthMap[y * X + x];
				byte n = normalIdMap[y * X + x];
				
				// Don't deblur sky
				if (n == -1) {
					continue;
				}
				
				// Iterate over the adjacent pixels
				for (int i = 0; i < offsets.length; i += 2) {
					int newX = x + offsets[i];
					int newY = y + offsets[i + 1];
					// Add the surrounding pixels if they are valid
					if (isPixelGeometricallyAdjacent(depthMap[newY * X + newX], d, normalIdMap[newY * X + newX], n)) {
						b += fixByte(pixels[(newY * X + newX) * 3 + 0]);
						g += fixByte(pixels[(newY * X + newX) * 3 + 1]);
						r += fixByte(pixels[(newY * X + newX) * 3 + 2]);
						sampleCount++;
					}
				}
				
				b /= sampleCount;
				g /= sampleCount;
				r /= sampleCount;
				
				// Set a square of four pixels
				pixels[(y * X + x) * 3 + 0] = (byte)b;
				pixels[(y * X + x) * 3 + 1] = (byte)g;
				pixels[(y * X + x) * 3 + 2] = (byte)r;
				
				pixels[((y+1) * X + x) * 3 + 0] = (byte)b;
				pixels[((y+1) * X + x) * 3 + 1] = (byte)g;
				pixels[((y+1) * X + x) * 3 + 2] = (byte)r;
				
				pixels[(y * X + (x+1)) * 3 + 0] = (byte)b;
				pixels[(y * X + (x+1)) * 3 + 1] = (byte)g;
				pixels[(y * X + (x+1)) * 3 + 2] = (byte)r;
				
				pixels[((y+1) * X + (x+1)) * 3 + 0] = (byte)b;
				pixels[((y+1) * X + (x+1)) * 3 + 1] = (byte)g;
				pixels[((y+1) * X + (x+1)) * 3 + 2] = (byte)r;
			}
		}
	}
	
	// Convert a byte in range [-128, 127] into a float in range [0, 255]
	private static float fixByte(byte val) {
		float x = val;
		if (x < 0) {
			x += 256;
		}
		return x;
	}
	
	// Determine if these two pixels are adjacent in the actual scene (and whether they should be blurred)
	private static boolean isPixelGeometricallyAdjacent(float depth1, float depth2, int norm1, int norm2) {
		
		// If the normals are not the same, then they are not adjacent
		if (norm1 != norm2) {
			return false;
		}
		
		return Math.abs(depth1 / depth2 - 1) < 0.04f;
	}
	
	/*
	// Return the render color associated with a certain heat value
	private static float[] getHeatColor(float heat) {
		heat = Math.max(Math.min(heat * 0.5f, 1), 0);
		//heat = (float)(Math.sin(heat * 4 * Math.PI + Math.PI)/4/Math.PI + heat);
		//heat = (heat * heat) / (heat * heat + 0.2f);
		if (heat < 0.5f) {
			return new float[] {0, heat*2*0.8f + 0.2f, 1-heat*2};
		} else {
			return new float[] {heat*2-1, 2-heat*2, 0};
		}
		
		/*
		if (heat < 0.33333333f) {
			return new float[] {0, heat*3, 1-heat*3};
		} else if (heat < 0.666666666f){
			return new float[] {heat*3-1, 2-heat*3, 0};
		} else {
			return new float[] {1, heat*3-2, heat*3-2};
		}
		*/
	//}
	
	public static void print(Object o) {
		System.out.println(o);
	}
}
