package heatsinkDesign;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;

import jcuda.Pointer;
import jcuda.Sizeof;
import jcuda.driver.CUcontext;
import jcuda.driver.CUdevice;
import jcuda.driver.CUdeviceptr;
import jcuda.driver.CUfunction;
import jcuda.driver.CUmodule;
import jcuda.driver.JCudaDriver;
import jcuda.runtime.JCuda;

// Created by Daniel Williams
// Created on September 10, 2020
// Last updated on April 3, 2022
// Created as a quick Java to CUDA interface.

// cl.exe can be found in:
// C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Tools\MSVC\14.31.31103\bin\Hostx64\x64
// C:\Program Files (x86)\Microsoft Visual Studio\2019\Professional\VC\Tools\MSVC\14.29.30133\bin\Hostx64\x64

public class CUDAProgram {
	
	private final String filePath;
	private String compiledFilePath = null;
	private CUfunction function;
	private Object[] outputArgs = new Object[20];
	private int numIterationsX = -1;
	private int numIterationsY = 1;
	private int numIterationsZ = 1;
	private int blockSizeX = 8; // Default value
	private int blockSizeY = 1; // Default value
	private int blockSizeZ = 1; // Default value
	private static boolean alreadyInitialized = false;
	private int lastOutputArgSavedIndex = -1;
	private static CUcontext context;
	
	private CUdeviceptr[] inputDeviceData = new CUdeviceptr[20];
	private Pointer[] inputArgPointers = new Pointer[20];
	
	private CUdeviceptr[] outputDeviceData = new CUdeviceptr[20];
	private Pointer[] outputArgPointers = new Pointer[20];
	
	public CUDAProgram(final String kernelMethodName, final String pathToFile) {
		final File file = new File(pathToFile);
		filePath = file.getAbsolutePath();
		final String fileNameOnly = filePath.substring(filePath.lastIndexOf('\\')+1);
		final String extensionlessPath = filePath.substring(0, filePath.lastIndexOf('.'));
		compiledFilePath = extensionlessPath + ".ptx";
		final long lastModifiedDate = file.lastModified();
		
		final String pathToReplace = filePath.replace('\\', '/');
		
		// Read the last compilation date of the file
		long lastCompiledDate = 0;
		try {
			DataInputStream inputStream = new DataInputStream(new FileInputStream(extensionlessPath + ".dat"));
			lastCompiledDate = inputStream.readLong();
			inputStream.close();
		} catch (Exception e) {}
		
		// If the file has been saved since the last compilation, then recompile it.
		if (lastCompiledDate < lastModifiedDate || !Files.exists(Paths.get(compiledFilePath))) {
			boolean gotError = false;
			
			try {
				// Compile the CUDA file
				print("Recompiling ...");
				ProcessBuilder ps = new ProcessBuilder("nvcc",
						"-ptx", "\"" + filePath + "\" -o \"" + compiledFilePath + "\"");
				ps.redirectErrorStream(true);
				Process process = ps.start();
				
				InputStreamReader in = new InputStreamReader(process.getInputStream());
				int c;
				String lineText = "";
				while ((c = in.read()) != -1) {
					if ((char)c == '\n') {
						if (lineText.contains("error:")) {
							System.out.println(fileNameOnly + ":");
							gotError = true;
						}
						System.out.println(lineText.replace(pathToReplace, ""));
						lineText = "";
					} else if ((char)c != '\r') {
						lineText += (char)c;
					}
				}
				process.waitFor();
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
			
			if (gotError) {
				System.err.println("\nFailed to compile " + fileNameOnly);
				System.exit(1);
			}
			
			try {
				// Save the last compilation date of the file
				DataOutputStream out = new DataOutputStream(new FileOutputStream(extensionlessPath + ".dat"));
				out.writeLong(lastModifiedDate);
				out.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		// Load the kernel file
		CUmodule module = new CUmodule();
		JCudaDriver.cuModuleLoad(module, compiledFilePath);
		
		// Get the function from the kernel to execute
		function = new CUfunction();
		JCudaDriver.cuModuleGetFunction(function, module, kernelMethodName);
	}
	
	// Set an input argument in for the kernel
	public void setInputArg(int argNum, Object arg) {
		
		boolean foundArgType = true;
		
		// Only do the allocation step if this is a new argument
		if (inputDeviceData[argNum] == null) {
			if (arg instanceof float[]) {
				float[] arr = (float[])arg;
				CUdeviceptr deviceInputA = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(deviceInputA, arr.length * Sizeof.FLOAT);
				JCudaDriver.cuMemcpyHtoD(deviceInputA, Pointer.to(arr), arr.length *  Sizeof.FLOAT);
				inputArgPointers[argNum] = Pointer.to(deviceInputA);
				inputDeviceData[argNum] = deviceInputA;
			} else if (arg instanceof double[]) {
				double[] arr = (double[])arg;
				CUdeviceptr deviceInputA = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(deviceInputA, arr.length * Sizeof.DOUBLE);
				JCudaDriver.cuMemcpyHtoD(deviceInputA, Pointer.to(arr), arr.length *  Sizeof.DOUBLE);
				inputArgPointers[argNum] = Pointer.to(deviceInputA);
				inputDeviceData[argNum] = deviceInputA;
			} else if (arg instanceof long[]) {
				long[] arr = (long[])arg;
				CUdeviceptr deviceInputA = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(deviceInputA, arr.length * Sizeof.LONG);
				JCudaDriver.cuMemcpyHtoD(deviceInputA, Pointer.to(arr), arr.length *  Sizeof.LONG);
				inputArgPointers[argNum] = Pointer.to(deviceInputA);
				inputDeviceData[argNum] = deviceInputA;
			} else if (arg instanceof int[]) {
				int[] arr = (int[])arg;
				CUdeviceptr deviceInputA = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(deviceInputA, arr.length * Sizeof.INT);
				JCudaDriver.cuMemcpyHtoD(deviceInputA, Pointer.to(arr), arr.length *  Sizeof.INT);
				inputArgPointers[argNum] = Pointer.to(deviceInputA);
				inputDeviceData[argNum] = deviceInputA;
			} else if (arg instanceof char[]) {
				char[] arr = (char[])arg;
				CUdeviceptr deviceInputA = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(deviceInputA, arr.length * Sizeof.CHAR);
				JCudaDriver.cuMemcpyHtoD(deviceInputA, Pointer.to(arr), arr.length *  Sizeof.CHAR);
				inputArgPointers[argNum] = Pointer.to(deviceInputA);
				inputDeviceData[argNum] = deviceInputA;
			} else if (arg instanceof byte[]) {
				byte[] arr = (byte[])arg;
				CUdeviceptr deviceInputA = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(deviceInputA, arr.length * Sizeof.BYTE);
				JCudaDriver.cuMemcpyHtoD(deviceInputA, Pointer.to(arr), arr.length *  Sizeof.BYTE);
				inputArgPointers[argNum] = Pointer.to(deviceInputA);
				inputDeviceData[argNum] = deviceInputA;
			} else if (arg instanceof BufferedImage) {
				BufferedImage image = (BufferedImage)arg;
				DataBufferInt dataBuffer = (DataBufferInt)image.getRaster().getDataBuffer();
				final int[] arr = dataBuffer.getData();
				inputDeviceData[argNum] = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(inputDeviceData[argNum], arr .length* Sizeof.INT);
				JCudaDriver.cuMemcpyHtoD(inputDeviceData[argNum], Pointer.to(arr), arr.length * Sizeof.INT);
				inputArgPointers[argNum] = Pointer.to(inputDeviceData[argNum]);
			} else {
				foundArgType = false;
			}
		} else {
			
			// This input argument was already allocated, so only perform the memory copy
			if (arg instanceof float[]) {
				float[] arr = (float[])arg;
				JCudaDriver.cuMemcpyHtoD(inputDeviceData[argNum], Pointer.to(arr), arr.length *  Sizeof.FLOAT);
			} else if (arg instanceof double[]) {
				double[] arr = (double[])arg;
				JCudaDriver.cuMemcpyHtoD(inputDeviceData[argNum], Pointer.to(arr), arr.length *  Sizeof.DOUBLE);
			} else if (arg instanceof long[]) {
				long[] arr = (long[])arg;
				JCudaDriver.cuMemcpyHtoD(inputDeviceData[argNum], Pointer.to(arr), arr.length *  Sizeof.LONG);
			} else if (arg instanceof int[]) {
				int[] arr = (int[])arg;
				JCudaDriver.cuMemcpyHtoD(inputDeviceData[argNum], Pointer.to(arr), arr.length *  Sizeof.INT);
			} else if (arg instanceof char[]) {
				char[] arr = (char[])arg;
				JCudaDriver.cuMemcpyHtoD(inputDeviceData[argNum], Pointer.to(arr), arr.length *  Sizeof.CHAR);
			} else if (arg instanceof byte[]) {
				byte[] arr = (byte[])arg;
				JCudaDriver.cuMemcpyHtoD(inputDeviceData[argNum], Pointer.to(arr), arr.length *  Sizeof.BYTE);
			} else if (arg instanceof BufferedImage) {
				BufferedImage image = (BufferedImage)arg;
				DataBufferInt dataBuffer = (DataBufferInt)image.getRaster().getDataBuffer();
				final int[] arr = dataBuffer.getData();
				JCudaDriver.cuMemcpyHtoD(inputDeviceData[argNum], Pointer.to(arr), arr.length * Sizeof.INT);
			} else {
				foundArgType = false;
			}
		}
		
		// If no argument type was already matched, then try the primitive types
		if (!foundArgType) {
			if (arg instanceof Integer) {
				int val = (int)arg;
				inputArgPointers[argNum] = Pointer.to(new int[] {val});
			} else if (arg instanceof Long) {
				long val = (long)arg;
				inputArgPointers[argNum] = Pointer.to(new long[] {val});
			} else if (arg instanceof Float) {
				float val = (float)arg;
				inputArgPointers[argNum] = Pointer.to(new float[] {val});
			} else if (arg instanceof Double) {
				double val = (double)arg;
				inputArgPointers[argNum] = Pointer.to(new double[] {val});
			} else if (arg instanceof Byte) {
				byte val = (byte)arg;
				inputArgPointers[argNum] = Pointer.to(new byte[] {val});
			} else {
				System.err.println("CUDAProgram unsupported input type");
				System.exit(1);
			}
		}
	}
	
	// Set an output argument in for the kernel
	public void setOutputArg(int argNum, Object arg) {
		
		outputArgs[argNum] = arg;
		
		// Only do the allocation step if this is a new argument
		if (outputDeviceData[argNum] == null) {
			//JCudaDriver.cuMemFree(outputDeviceData[argNum]);
			
			if (arg instanceof float[]) {
				float[] arr = (float[])arg;
				outputDeviceData[argNum] = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(outputDeviceData[argNum], arr.length * Sizeof.FLOAT);
				outputArgPointers[argNum] = Pointer.to(outputDeviceData[argNum]);
			} else if (arg instanceof double[]) {
				double[] arr = (double[])arg;
				outputDeviceData[argNum] = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(outputDeviceData[argNum], arr.length * Sizeof.DOUBLE);
				outputArgPointers[argNum] = Pointer.to(outputDeviceData[argNum]);
			} else if (arg instanceof long[]) {
				long[] arr = (long[])arg;
				outputDeviceData[argNum] = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(outputDeviceData[argNum], arr.length * Sizeof.LONG);
				outputArgPointers[argNum] = Pointer.to(outputDeviceData[argNum]);
			} else if (arg instanceof int[]) {
				int[] arr = (int[])arg;
				outputDeviceData[argNum] = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(outputDeviceData[argNum], arr.length * Sizeof.INT);
				outputArgPointers[argNum] = Pointer.to(outputDeviceData[argNum]);
			} else if (arg instanceof char[]) {
				char[] arr = (char[])arg;
				outputDeviceData[argNum] = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(outputDeviceData[argNum], arr.length * Sizeof.CHAR);
				outputArgPointers[argNum] = Pointer.to(outputDeviceData[argNum]);
			} else if (arg instanceof byte[]) {
				byte[] arr = (byte[])arg;
				outputDeviceData[argNum] = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(outputDeviceData[argNum], arr.length * Sizeof.BYTE);
				outputArgPointers[argNum] = Pointer.to(outputDeviceData[argNum]);
			} else if (arg instanceof BufferedImage) {
				BufferedImage image = (BufferedImage)arg;
				outputDeviceData[argNum] = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(outputDeviceData[argNum], image.getWidth() * image.getHeight() * Sizeof.INT);
				outputArgPointers[argNum] = Pointer.to(outputDeviceData[argNum]);
			} else {
				System.err.println("CUDAProgram unsupported output type");
				System.exit(1);
			}
		}
	}
	
	// Set a read/write argument for the kernel
	public void setInOutArg(int argNum, Object arg) {
		outputArgs[argNum] = arg;

		// Only do the allocation step if this is a new argument
		if (outputDeviceData[argNum] == null) {
			if (arg instanceof float[]) {
				float[] arr = (float[])arg;
				outputDeviceData[argNum] = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(outputDeviceData[argNum], arr.length * Sizeof.FLOAT);
				JCudaDriver.cuMemcpyHtoD(outputDeviceData[argNum], Pointer.to(arr), arr.length * Sizeof.FLOAT);
				outputArgPointers[argNum] = Pointer.to(outputDeviceData[argNum]);
			} else if (arg instanceof double[]) {
				double[] arr = (double[])arg;
				outputDeviceData[argNum] = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(outputDeviceData[argNum], arr.length * Sizeof.DOUBLE);
				JCudaDriver.cuMemcpyHtoD(outputDeviceData[argNum], Pointer.to(arr), arr.length * Sizeof.DOUBLE);
				outputArgPointers[argNum] = Pointer.to(outputDeviceData[argNum]);
			} else if (arg instanceof long[]) {
				long[] arr = (long[])arg;
				outputDeviceData[argNum] = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(outputDeviceData[argNum], arr.length * Sizeof.LONG);
				JCudaDriver.cuMemcpyHtoD(outputDeviceData[argNum], Pointer.to(arr), arr.length * Sizeof.LONG);
				outputArgPointers[argNum] = Pointer.to(outputDeviceData[argNum]);
			} else if (arg instanceof int[]) {
				int[] arr = (int[])arg;
				outputDeviceData[argNum] = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(outputDeviceData[argNum], arr.length * Sizeof.INT);
				JCudaDriver.cuMemcpyHtoD(outputDeviceData[argNum], Pointer.to(arr), arr.length * Sizeof.INT);
				outputArgPointers[argNum] = Pointer.to(outputDeviceData[argNum]);
			} else if (arg instanceof char[]) {
				char[] arr = (char[])arg;
				outputDeviceData[argNum] = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(outputDeviceData[argNum], arr.length * Sizeof.CHAR);
				JCudaDriver.cuMemcpyHtoD(outputDeviceData[argNum], Pointer.to(arr), arr.length * Sizeof.CHAR);
				outputArgPointers[argNum] = Pointer.to(outputDeviceData[argNum]);
			} else if (arg instanceof byte[]) {
				byte[] arr = (byte[])arg;
				outputDeviceData[argNum] = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(outputDeviceData[argNum], arr.length * Sizeof.BYTE);
				JCudaDriver.cuMemcpyHtoD(outputDeviceData[argNum], Pointer.to(arr), arr.length * Sizeof.BYTE);
				outputArgPointers[argNum] = Pointer.to(outputDeviceData[argNum]);
			} else if (arg instanceof BufferedImage) {
				BufferedImage image = (BufferedImage)arg;
				DataBufferInt dataBuffer = (DataBufferInt)image.getRaster().getDataBuffer();
				final int[] arr = dataBuffer.getData();
				outputDeviceData[argNum] = new CUdeviceptr();
				JCudaDriver.cuMemAlloc(outputDeviceData[argNum], arr .length* Sizeof.INT);
				JCudaDriver.cuMemcpyHtoD(outputDeviceData[argNum], Pointer.to(arr), arr.length * Sizeof.INT);
				outputArgPointers[argNum] = Pointer.to(outputDeviceData[argNum]);
			} else {
				System.err.println("CUDAProgram unsupported read-write input type");
				System.exit(1);
			}
		} else {
			// This read-write argument was already allocated, so only perform the memory copy
			if (arg instanceof float[]) {
				float[] arr = (float[])arg;
				JCudaDriver.cuMemcpyHtoD(outputDeviceData[argNum], Pointer.to(arr), arr.length *  Sizeof.FLOAT);
			} else if (arg instanceof double[]) {
				double[] arr = (double[])arg;
				JCudaDriver.cuMemcpyHtoD(outputDeviceData[argNum], Pointer.to(arr), arr.length *  Sizeof.DOUBLE);
			} else if (arg instanceof long[]) {
				long[] arr = (long[])arg;
				JCudaDriver.cuMemcpyHtoD(outputDeviceData[argNum], Pointer.to(arr), arr.length *  Sizeof.LONG);
			} else if (arg instanceof int[]) {
				int[] arr = (int[])arg;
				JCudaDriver.cuMemcpyHtoD(outputDeviceData[argNum], Pointer.to(arr), arr.length *  Sizeof.INT);
			} else if (arg instanceof char[]) {
				char[] arr = (char[])arg;
				JCudaDriver.cuMemcpyHtoD(outputDeviceData[argNum], Pointer.to(arr), arr.length *  Sizeof.CHAR);
			} else if (arg instanceof byte[]) {
				byte[] arr = (byte[])arg;
				JCudaDriver.cuMemcpyHtoD(outputDeviceData[argNum], Pointer.to(arr), arr.length *  Sizeof.BYTE);
			} else if (arg instanceof BufferedImage) {
				BufferedImage image = (BufferedImage)arg;
				DataBufferInt dataBuffer = (DataBufferInt)image.getRaster().getDataBuffer();
				final int[] arr = dataBuffer.getData();
				JCudaDriver.cuMemcpyHtoD(outputDeviceData[argNum], Pointer.to(arr), arr.length * Sizeof.INT);
			} else {
				System.err.println("CUDAProgram unsupported read-write input type");
				System.exit(1);
			}
		}
	}
	
	// Set the number of iterations to perform
	public void setIterations(int numIterationsX) {
		this.numIterationsX = numIterationsX;
	}
	
	// Set the number of iterations to perform
	public void setIterations(int numIterationsX, int numIterationsY) {
		this.numIterationsX = numIterationsX;
		this.numIterationsY = numIterationsY;
	}
	
	// Set the number of iterations to perform
	public void setIterations(int numIterationsX, int numIterationsY, int numIterationsZ) {
		this.numIterationsX = numIterationsX;
		this.numIterationsY = numIterationsY;
		this.numIterationsZ = numIterationsZ;
	}
	
	// Set the block size for execution
	public void setBlockSize(int blockSizeX) {
		this.blockSizeX = blockSizeX;
	}
	
	// Set the block size for execution
	public void setBlockSize(int blockSizeX, int blockSizeY) {
		this.blockSizeX = blockSizeX;
		this.blockSizeY = blockSizeY;
	}
	
	// Set the block size for execution
	public void setBlockSize(int blockSizeX, int blockSizeY, int blockSizeZ) {
		this.blockSizeX = blockSizeX;
		this.blockSizeY = blockSizeY;
		this.blockSizeZ = blockSizeZ;
	}
	
	// Initialize the GPU for computation
	public static void initializeGPU() {
		if (alreadyInitialized) {
			System.err.println("GPU 0 has already been initialized");
			System.exit(1);
		}
		alreadyInitialized = true;
		
		JCudaDriver.setExceptionsEnabled(true);
		JCuda.setExceptionsEnabled(true);
		
		// GPU Initialization
		JCudaDriver.cuInit(0);
		CUdevice device = new CUdevice();
		JCudaDriver.cuDeviceGet(device, 0);
		context = new CUcontext();
		JCudaDriver.cuCtxCreate(context, 0, device);
	}
	
	// Return the name of the compute device being used for the computation
	public static String getDeviceName() {
		CUdevice device = new CUdevice();
		JCudaDriver.cuCtxGetDevice(device);
		byte[] name = new byte[100];
		JCudaDriver.cuDeviceGetName(name, 100, device);
		return new String(name, java.nio.charset.StandardCharsets.UTF_8);
	}
	
	// Execute the kernel and copy the output arguments back to the host memory
	public void executeKernel() {
		// Do the whole kernel execution
		executeKernelNoWriteback();
		
		// Copy the output back to the host memory
		copyOutputsFromGPU();
	}
	
	// Execute the kernel only
	public void executeKernelNoWriteback() {
		
		if (numIterationsX == -1) {
			System.err.println("Iteration lengths have not been set");
			System.exit(1);
		}
		
		final Pointer[] argPointers = getArrayOfArguments();
		
		final int gridSizeX = (int)Math.ceil((double)numIterationsX / blockSizeX);
		final int gridSizeY = (int)Math.ceil((double)numIterationsY / blockSizeY);
		final int gridSizeZ = (int)Math.ceil((double)numIterationsZ / blockSizeZ);
		
		JCudaDriver.cuCtxSetCurrent(context);

		// Execute the GPU kernel
		JCudaDriver.cuLaunchKernel(function,
				gridSizeX, gridSizeY, gridSizeZ,
				blockSizeX, blockSizeY, blockSizeZ,
				0, null,
				Pointer.to(argPointers), null);
		
		// Yield the thread until the computation finishes (not necessary though)
		JCudaDriver.cuCtxSynchronize();
	}
	
	// Prepare an array of input and arguments to pass to the kernel
	private Pointer[] getArrayOfArguments() {
		// Count the number of input arguments and output arguments
		int numInputArgs = 0;
		int lastInputArgIndex = 0;
		for (int i = 0; i < inputArgPointers.length; i++) {
			if (inputArgPointers[i] != null) {
				numInputArgs++;
				lastInputArgIndex = i;
			}
		}
		int numOutputArgs = 0;
		int lastOutputArgIndex = 0;
		for (int i = 0; i < outputArgPointers.length; i++) {
			if (outputArgPointers[i] != null) {
				numOutputArgs++;
				lastOutputArgIndex = i;
			}
		}
		
		// Trim off the excess argument pointers
		final Pointer[] argPointers = new Pointer[numInputArgs + numOutputArgs];
		for (int i = 0; i <= lastInputArgIndex; i++) {
			if (inputArgPointers[i] != null) {
				argPointers[i] = inputArgPointers[i];
			}
		}
		for (int i = 0; i <= lastOutputArgIndex; i++) {
			if (outputArgPointers[i] != null) {
				argPointers[i] = outputArgPointers[i];
			}
		}
		lastOutputArgSavedIndex = lastOutputArgIndex;
		
		return argPointers;
	}
	
	// Copy all output variables from the GPU memory to host memory
	public void copyOutputsFromGPU() {
		// Copy the output back to the host memory
		for (int i = 0; i <= lastOutputArgSavedIndex; i++) {
			if (outputArgs[i] == null) {
				continue;
			}
			
			Pointer hostOutputPointer = null;
			long dataSize = -1;
			
			if (outputArgs[i] instanceof float[]) {
				float[] arr = (float[])outputArgs[i];
				dataSize = arr.length * Sizeof.FLOAT;
				hostOutputPointer = Pointer.to(arr);
			} else if (outputArgs[i] instanceof double[]) {
				double[] arr = (double[])outputArgs[i];
				dataSize = arr.length * Sizeof.DOUBLE;
				hostOutputPointer = Pointer.to(arr);
			} else if (outputArgs[i] instanceof long[]) {
				long[] arr = (long[])outputArgs[i];
				dataSize = arr.length * Sizeof.LONG;
				hostOutputPointer = Pointer.to(arr);
			} else if (outputArgs[i] instanceof int[]) {
				int[] arr = (int[])outputArgs[i];
				dataSize = arr.length * Sizeof.INT;
				hostOutputPointer = Pointer.to(arr);
			} else if (outputArgs[i] instanceof char[]) {
				char[] arr = (char[])outputArgs[i];
				dataSize = arr.length * Sizeof.CHAR;
				hostOutputPointer = Pointer.to(arr);
			} else if (outputArgs[i] instanceof byte[]) {
				byte[] arr = (byte[])outputArgs[i];
				dataSize = arr.length * Sizeof.BYTE;
				hostOutputPointer = Pointer.to(arr);
			} else if (outputArgs[i] instanceof BufferedImage) {
				BufferedImage image = (BufferedImage)outputArgs[i];
				DataBufferInt dataBuffer = (DataBufferInt)image.getRaster().getDataBuffer();
				final int[] imageData = dataBuffer.getData();
				dataSize = image.getWidth() * image.getHeight() * Sizeof.INT;
				hostOutputPointer = Pointer.to(imageData);
			} else {
				System.err.println("CUDAProgram unsupported output type (after kernel)");
				System.exit(1);
			}
			
			// Copy the output data back to the host
			JCudaDriver.cuMemcpyDtoH(hostOutputPointer, outputDeviceData[i], dataSize);
		}
	}
	
	// Free all of the input and output memory from the GPU
	public void dispose() {
		// Free all of the input data
		for (int i = 0; i < inputDeviceData.length; i++) {
			if (inputDeviceData[i] != null) {
				JCudaDriver.cuMemFree(inputDeviceData[i]);
				inputDeviceData[i] = null;
				inputArgPointers[i] = null;
			}
		}
		
		// Free all of the output data
		for (int i = 0; i < outputDeviceData.length; i++) {
			if (outputDeviceData[i] != null) {
				JCudaDriver.cuMemFree(outputDeviceData[i]);
				outputDeviceData[i] = null;
				outputArgPointers[i] = null;
				outputArgs[i] = null;
			}
		}
	}
	
	private static void print(Object o) {
		System.out.println(o);
	}
}
