package heatsinkDesign;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.UIManager;

import com.jogamp.newt.Window;
import com.jogamp.newt.event.KeyAdapter;
import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.MouseAdapter;
import com.jogamp.newt.event.MouseEvent;
import com.jogamp.newt.event.awt.AWTKeyAdapter;
import com.jogamp.newt.event.awt.AWTMouseAdapter;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;

// This class displays an interactive render of the heat sink design

public class Renderer {
	private static float camMoveSpeed = 0.3f;
	private static Vector3 camVelocity = new Vector3();
	private static Vector3 pos = new Vector3();
	private static long lastRenderTime = System.currentTimeMillis();
	private static double fps = 30;
	
	private static float lightVec[] = {0.4f, 0.75f, 0.3f, 0.0f};
	private static float rotx = 0.5f;
	private static float roty = 0.5f;
	private static final int swapInterval = 1;
	private static float zoom = 40 + HeatsinkMain.cellsWide * 2;
	private static GLU glu;
	
	private static boolean didViewChange = false;
	private static int prevMouseX, prevMouseY;
	
	static void initialize() {
		
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (Exception e) {}
		
		java.awt.Frame frame = new java.awt.Frame("Heat Sink Render");
		
	    final GLCapabilities caps = new GLCapabilities(GLProfile.getDefault());
	    caps.setSampleBuffers(true);
	    caps.setNumSamples(4); // Enable 4x antialiasing
		final GLCanvas canvas = new GLCanvas(caps);
		canvas.setSize(1100, 700);
		
		frame.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				new Thread(new Runnable() {
					@Override
					public void run() {
						canvas.destroy();
						System.exit(0);
					}
				}).start();
			}
		});
		
		canvas.addGLEventListener(new GLEventListener() {
			public void reshape(GLAutoDrawable arg0, int arg1, int arg2, int arg3, int arg4) {
				Renderer.reshape(arg0, arg1, arg2, arg3, arg4);
			}
			public void init(GLAutoDrawable arg0) {
				Renderer.init(arg0);
			}
			public void dispose(GLAutoDrawable arg0) {
				Renderer.dispose(arg0);
			}
			public void display(GLAutoDrawable arg0) {
				Renderer.display(arg0);
			}
		});
		
		frame.add(canvas, BorderLayout.CENTER);
		
		final JButton saveMeshButton = new JButton("Save mesh");
		saveMeshButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				SaveLoadTools.exportCurrentHeatsink();
			}
		});
		
		final JButton saveGeomButton = new JButton("Save geometry");
		saveGeomButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				SaveLoadTools.saveCurrentHeatsinkAsBinary();
			}
		});
		
		final JPanel bottomPanel = new JPanel();
		bottomPanel.setBackground(Color.WHITE);
		bottomPanel.add(saveMeshButton);
		bottomPanel.add(saveGeomButton);
		
		frame.add(bottomPanel, BorderLayout.SOUTH);
		frame.pack();
		frame.setLocation(40, 40);
		frame.setVisible(true);
		
		new Thread(new Runnable() {
			public void run() {
				while (true) {
					
					// Add a delay regardless to make sure the computation threads have time
					try {
						// Sleep for a long time if the window is minimized
						if (frame.getExtendedState() == JFrame.ICONIFIED) {
							Thread.sleep(2000);
						}
						
						Thread.sleep(16);
						
						// Longer delay if the user is interacting
						for (int i = 0; i < 5; i++) {
							if (!didViewChange) {
								Thread.sleep(16);
							} else {
								didViewChange = false;
								break;
							}
						}
					} catch (Exception e) {}
					
					canvas.display();
				}
			}
		}).start();
	}
	
	public static void init(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();
		glu = new GLU();
		
		float light_ambient[] = {0.5f, 0.5f, 0.5f, 1.0f};
		float light_diffuse[] = {1.0f, 1.0f, 1.0f, 1.0f};
		float light_specular[] = { 1.0f, 1.0f, 1.0f, 1.0f };

		gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_AMBIENT, light_ambient, 0);
		gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, light_diffuse, 0);
		gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_SPECULAR, light_specular, 0);
		gl.glEnable(GL2.GL_CULL_FACE);
		gl.glEnable(GL2.GL_LIGHTING);
		gl.glEnable(GL2.GL_LIGHT0);
		gl.glEnable(GL2.GL_DEPTH_TEST);

		gl.glHint(GL2.GL_POLYGON_SMOOTH_HINT, GL2.GL_NICEST); // really nice point smoothing
		gl.glHint(GL2.GL_PERSPECTIVE_CORRECTION_HINT, GL2.GL_NICEST); // best perspective correction
		gl.glShadeModel(GL2.GL_SMOOTH); // blends colors nicely, and smoothes out lighting
		gl.glEnable(GL2.GL_LINE_SMOOTH);
		gl.glEnable(GL2.GL_BLEND); // enable blending
		
		gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_S, GL2.GL_REPEAT);
		gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_T, GL2.GL_REPEAT);
		gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR_MIPMAP_LINEAR);
		gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_NEAREST);
		
		gl.glEnable(GL2.GL_NORMALIZE);
		
		// Create the input listeners
		MouseAdapter mouseInput = new MouseAdapter() {
			public void mousePressed(MouseEvent e) {
				prevMouseX = e.getX();
				prevMouseY = e.getY();
			}
			public void mouseWheelMoved(MouseEvent e) {
				float delta = e.getRotation()[1];
				if (delta > 0) {
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
			public void mouseDragged(MouseEvent e) {
				final int x = e.getX();
				final int y = e.getY();
				float thetaY = -0.01f * (x - prevMouseX);
				float thetaX = 0.01f * (y - prevMouseY);
				
				prevMouseX = x;
				prevMouseY = y;
				
				rotx += thetaX;
				roty += thetaY;
				
				didViewChange = true;
			}
		};
		
		KeyAdapter keyInput = new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				if (e.getKeyChar() == 'w') {
					camVelocity.z = 1;
					didViewChange = true;
				} else if (e.getKeyChar() == 's') {
					camVelocity.z = -1;
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
		};
		
		// Add input listeners
		if (drawable instanceof Window) {
			Window window = (Window) drawable;
			window.addMouseListener(mouseInput);
			window.addKeyListener(keyInput);
			window.requestFocus();
		} else if (GLProfile.isAWTAvailable() && drawable instanceof java.awt.Component) {
			java.awt.Component comp = (java.awt.Component) drawable;
			new AWTMouseAdapter(mouseInput, drawable).addTo(comp);
			new AWTKeyAdapter(keyInput, drawable).addTo(comp);
			comp.requestFocus();
		}
	}
	
	public static void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
		System.out.println("Reshape " + width + " x " + height);
		GL2 gl = drawable.getGL().getGL2();
		
		gl.setSwapInterval(swapInterval); // Something with animating and v-blanks

		if (height == 0)
			height = 1; // prevent divide by zero
		float aspect = (float) width / height;
		
		// Set the view port (display area) to cover the entire window
		gl.glViewport(0, 0, width, height);

		// Setup perspective projection, with aspect ratio matches viewport
		gl.glMatrixMode(GL2.GL_PROJECTION); // choose projection matrix
		gl.glLoadIdentity(); // reset projection matrix
		glu.gluPerspective(35.0, aspect, 0.1, 1000.0); // fovy, aspect, zNear, zFar
		
		// Enable the model-view transform
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glLoadIdentity(); // reset
	}
	
	public static void dispose(GLAutoDrawable drawable) {
		System.out.println("Disposing frame");
	}
	
	public static void display(GLAutoDrawable drawable) {
		// Get the GL corresponding to the drawable we are animating
		GL2 gl = drawable.getGL().getGL2();

		gl.glLoadIdentity(); // Reset the model-view matrix
		
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
		pos = pos.sub(left.mult(camVelocity.x).sub(up.mult(camVelocity.y).
				sub(forward.mult(camVelocity.z))).mult(camMoveSpeed/fps*60));
		
		// Rotate and zoom the camera
		glu.gluLookAt(
				(float)forward.x * zoom + (float)pos.x,
				(float)forward.y * zoom + (float)pos.y,
				(float)forward.z * zoom + (float)pos.z,
				(float)pos.x, (float)pos.y, (float)pos.z,
				(float)up.x, (float)up.y, (float)up.z);
		
		gl.glClearColor(0.85f, 0.925f, 1.0f, 0.0f); // The background color
		
		// Set the light position
		gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, lightVec, 0);
		
		// Enable backface culling
		gl.glEnable(GL2.GL_CULL_FACE);
		
		// Special handling for the case where the GLJPanel is translucent
		// and wants to be composited with other Java 2D content
		if (GLProfile.isAWTAvailable() && (drawable instanceof com.jogamp.opengl.awt.GLJPanel)
				&& !((com.jogamp.opengl.awt.GLJPanel) drawable).isOpaque()
				&& ((com.jogamp.opengl.awt.GLJPanel) drawable)
						.shouldPreserveColorBufferIfTranslucent()) {
			gl.glClear(GL2.GL_DEPTH_BUFFER_BIT);
		} else {
			gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
		}
		
		
		final float width = 1.0f;
		final float cullingThreshold = 0.15f;
		
		gl.glTranslatef(-HeatsinkMain.cellsWide/2f,
				-HeatsinkMain.cellsWide/2f,
				-HeatsinkMain.cellsWide/2f);
		
		gl.glPushMatrix();
		
		/* Render the heat sink only
		gl.glMaterialfv(GL2.GL_FRONT, GL2.GL_AMBIENT_AND_DIFFUSE, new float[] {0.6f, 0.6f, 0.6f}, 0);
		for (int x = 0; x < HeatsinkMain.cellsWide; x++) {
			for (int y = 0; y < HeatsinkMain.cellsWide; y++) {
				for (int z = 0; z < HeatsinkMain.cellsWide; z++) {
					final int i = HeatsinkMain.idx(x, y, z);
					
					// If this cell is not enabled, then don't render it
					if (HeatsinkMain.cellEnabled[i] == 1 && HeatsinkMain.isCellOnBoundary[i] == 1) {
						
						gl.glPushMatrix();
						gl.glTranslatef(x, y, z);
						gl.glBegin(GL2.GL_QUADS);
						
						// Front
						if (forward.z > -cullingThreshold) {
							gl.glNormal3f(0, 0, 1);
							gl.glVertex3f(width, 0, width);
							gl.glVertex3f(width, width, width);
							gl.glVertex3f(0, width, width);
							gl.glVertex3f(0, 0, width);
						}
						
						// Back
						if (forward.z < cullingThreshold) {
							gl.glNormal3f(0, 0, -1);
							gl.glVertex3f(0, 0, 0);
							gl.glVertex3f(0, width, 0);
							gl.glVertex3f(width, width, 0);
							gl.glVertex3f(width, 0, 0);
						}
						
						// Right
						if (forward.x > -cullingThreshold) {
							gl.glNormal3f(1, 0, 0);
							gl.glVertex3f(width, 0, 0);
							gl.glVertex3f(width, width, 0);
							gl.glVertex3f(width, width, width);
							gl.glVertex3f(width, 0, width);
						}
						
						// Left
						if (forward.x < cullingThreshold) {
							gl.glNormal3f(-1, 0, 0);
							gl.glVertex3f(0, 0, width);
							gl.glVertex3f(0, width, width);
							gl.glVertex3f(0, width, 0);
							gl.glVertex3f(0, 0, 0);
						}
						
						// Bottom
						if (forward.y < cullingThreshold) {
							gl.glNormal3f(0, -1, 0);
							gl.glVertex3f(width, 0, width);
							gl.glVertex3f(0, 0, width);
							gl.glVertex3f(0, 0, 0);
							gl.glVertex3f(width, 0, 0);
						}
						
						// Top
						if (forward.y > -cullingThreshold) {
							gl.glNormal3f(0, 1, 0);
							gl.glVertex3f(width, width, 0);
							gl.glVertex3f(0, width, 0);
							gl.glVertex3f(0, width, width);
							gl.glVertex3f(width, width, width);
						}
						
						gl.glEnd();
						gl.glPopMatrix();
					}
				}
			}
		}
		//*/
		
		/* Render a cross-section of the simulation
		final int endZ = HeatsinkMain.cellsWide/2;
		for (int x = 0; x < HeatsinkMain.cellsWide; x++) {
			for (int y = 0; y < HeatsinkMain.cellsWide; y++) {
				for (int z = 0; z < endZ; z++) {
					final int i = HeatsinkMain.idx(x, y, z);
					
					// If this cell is not on the visualization edge, then don't render it
					if (x == 0 || y == 0 || z == 0 || x == HeatsinkMain.cellsWide-1
							|| y == HeatsinkMain.cellsWide-1 || z == endZ-1) {
		
		//*/
		//* Render the surface temperature of the heat sink
		for (int x = 0; x < HeatsinkMain.cellsWide; x++) {
			for (int y = 0; y < HeatsinkMain.cellsWide; y++) {
				for (int z = 0; z < HeatsinkMain.cellsWide; z++) {
					final int i = HeatsinkMain.idx(x, y, z);
					
					// If this cell is not on the visualization edge, then don't render it
					if (HeatsinkMain.isCellOnBoundary[i] == 1) {
		//*/			
		//*
						gl.glPushMatrix();
						gl.glTranslatef(x, y, z);
						
						// Set the color for this cell
						final float[] color = getHeatColor(HeatsinkMain.cellHeat[i]);
						gl.glMaterialfv(GL2.GL_FRONT, GL2.GL_AMBIENT_AND_DIFFUSE, color, 0);
						
						gl.glBegin(GL2.GL_QUADS);
						
						// Front
						if (forward.z > -cullingThreshold) {
							gl.glNormal3f(0, 0, 1);
							gl.glVertex3f(width, 0, width);
							gl.glVertex3f(width, width, width);
							gl.glVertex3f(0, width, width);
							gl.glVertex3f(0, 0, width);
						}
						
						// Back
						if (forward.z < cullingThreshold) {
							gl.glNormal3f(0, 0, -1);
							gl.glVertex3f(0, 0, 0);
							gl.glVertex3f(0, width, 0);
							gl.glVertex3f(width, width, 0);
							gl.glVertex3f(width, 0, 0);
						}
						
						// Right
						if (forward.x > -cullingThreshold) {
							gl.glNormal3f(1, 0, 0);
							gl.glVertex3f(width, 0, 0);
							gl.glVertex3f(width, width, 0);
							gl.glVertex3f(width, width, width);
							gl.glVertex3f(width, 0, width);
						}
						
						// Left
						if (forward.x < cullingThreshold) {
							gl.glNormal3f(-1, 0, 0);
							gl.glVertex3f(0, 0, width);
							gl.glVertex3f(0, width, width);
							gl.glVertex3f(0, width, 0);
							gl.glVertex3f(0, 0, 0);
						}
						
						// Bottom
						if (forward.y < cullingThreshold) {
							gl.glNormal3f(0, -1, 0);
							gl.glVertex3f(width, 0, width);
							gl.glVertex3f(0, 0, width);
							gl.glVertex3f(0, 0, 0);
							gl.glVertex3f(width, 0, 0);
						}
						
						// Top
						if (forward.y > -cullingThreshold) {
							gl.glNormal3f(0, 1, 0);
							gl.glVertex3f(width, width, 0);
							gl.glVertex3f(0, width, 0);
							gl.glVertex3f(0, width, width);
							gl.glVertex3f(width, width, width);
						}
						
						gl.glEnd();
						gl.glPopMatrix();
					}
				}
			}
		}
		//*/
		

		gl.glEnd();
		gl.glPopMatrix();

		gl.glMaterialfv(GL2.GL_FRONT, GL2.GL_AMBIENT_AND_DIFFUSE, new float[]{0.1f,0.2f,0.3f}, 0);
		
		// Draw the bounding box around the model
		gl.glBegin(GL2.GL_LINE_STRIP);
		gl.glVertex3f(0, 0, 0);
		gl.glVertex3f(0, HeatsinkMain.cellsWide, 0);
		gl.glVertex3f(HeatsinkMain.cellsWide, HeatsinkMain.cellsWide, 0);
		gl.glVertex3f(HeatsinkMain.cellsWide, 0, 0);
		gl.glVertex3f(0, 0, 0);
		gl.glVertex3f(0, 0, HeatsinkMain.cellsWide);
		gl.glVertex3f(0, HeatsinkMain.cellsWide, HeatsinkMain.cellsWide);
		gl.glVertex3f(HeatsinkMain.cellsWide, HeatsinkMain.cellsWide, HeatsinkMain.cellsWide);
		gl.glVertex3f(HeatsinkMain.cellsWide, 0, HeatsinkMain.cellsWide);
		gl.glVertex3f(0, 0, HeatsinkMain.cellsWide);
		gl.glEnd();
		gl.glBegin(GL2.GL_LINES);
		gl.glVertex3f(0, HeatsinkMain.cellsWide, 0);
		gl.glVertex3f(0, HeatsinkMain.cellsWide, HeatsinkMain.cellsWide);
		gl.glVertex3f(HeatsinkMain.cellsWide, HeatsinkMain.cellsWide, 0);
		gl.glVertex3f(HeatsinkMain.cellsWide, HeatsinkMain.cellsWide, HeatsinkMain.cellsWide);
		gl.glVertex3f(HeatsinkMain.cellsWide, 0, 0);
		gl.glVertex3f(HeatsinkMain.cellsWide, 0, HeatsinkMain.cellsWide);
		gl.glEnd();
		

		
		fps = 1000.0 / (System.currentTimeMillis() - lastRenderTime);
		lastRenderTime = System.currentTimeMillis();
	}
	
	// Return the render color associated with a certain heat value
	private static float[] getHeatColor(float heat) {
		//heat *= 3;
		heat = Math.max(Math.min(heat * 0.5f, 1), 0);
		//heat = (float)(Math.sin(heat * 4 * Math.PI + Math.PI)/4/Math.PI + heat);
		//heat = (heat * heat) / (heat * heat + 0.2f);
		
		if (heat < 0.5f) {
			return new float[] {0, heat*2*0.6f + 0.2f, 1-heat*2};
		} else {
			return new float[] {heat*2-1, 1.6f-heat*2*0.8f, 0};
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
	}
	
	public static void print(Object o) {
		System.out.println(o);
	}
}
