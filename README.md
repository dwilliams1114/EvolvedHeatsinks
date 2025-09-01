This is my project for the 2020 Scalable Many Core Computing course.

Final presentation:

[SMC2020f_Final_DanielWilliams_heatsink.pptx](https://danielwilliams.tech/Heatsinks/SMC2020f)

Poster:

[Heat Sink Design Poster.png](https://danielwilliams.tech/Heatsinks/Heat_Sink_Design_Poster.png>)

Example heat sinks produced by this project:

<video src="https://danielwilliams.tech/Heatsinks/Heatsinks4.mp4" width="320" height="240" controls></video>

# Abstract

Heat sinks are typically designed by hand.  Recently, computational methods have been applied to optimize the shape of heat sinks to maximize their effectiveness.  However, these designs are typically impractical for real-world manufacturing.  In this paper, we describe an evolution-inspired method for automatically designing heat sinks within the constraints of common manufacturing processes. The resulting heat sink designs are presented.

# Introduction

Heat sinks are typically designed by hand.  Recently, automated computational methods have been applied to optimize the geometry of heat sinks to maximize cooling effectiveness.  However, these designs typically have small features, overhangs, hollow portions, or other features rendering them impractical for real-world manufacturing.

This prompts us to design a similar heat sink optimization method, but constrained to realistic solutions for current mass manufacturing processes, such as extrusion or forging.  We propose an optimization technique that uses a greedy evolution algorithm, then use that algorithm to generate a number of high-performing heat sinks geometries that are subject to the constraints of common manufacturing processes.  Of those processes, we focus on extrusion and forging because they are relatively simple to model computationally.

# Computational Methods

Designing an efficient heat sink is an optimization problem that may be solved using a form of gradient descent. Our objective function is the thermal performance of the heat sink.  We use a greedy evolutionary approach I shown below:

![Heat Sink Optimization Process](https://danielwilliams.tech/Heatsinks/Heat_Sink_Optimization_Process.png)

This method allows us to easily add or remove constraints for various manufacturing techniques without having to modify the optimization algorithm or objective function.

Our thermal simulation is modeled simply using heat diffusion with different conductivity constants (k).  For aluminum, k=205.  For air-metal boundary, k=4.  For air, k=6.  The simulation is performed using GPU accelerated finite element method with cubic elements where heat is conducted to the six adjacent cells.  This gives a rough approximation to passive air cooling without suffering the performance loss of solving fluid flow equations.

![Heat Sink Diffusion Equation](https://danielwilliams.tech/Heatsinks/Heat_Sink_Diffusion.png)

The simulation is subject to multiple constraints:

- Bounded simulation volume
- Bounded heat sink material volume
- Symmetry is enforced
- Constraints to specific manufacturing methods

We could expect the resulting heat sink designs to be near optimal for passive cooling.

# Results

The generated heat sinks generally outperform their human-designed counterparts by about 3%, while occupying the same space and material.  The heat sinks below were generated through 6700 evolution steps over 11 hours in a 643 simulation space.  These designs were constrained to a uniform cross section for the extrusion manufacturing process, but were simulated in three dimensions.

<p float="left" align="middle">
  <img src="https://danielwilliams.tech/Heatsinks/Heatsink_Design_16.png" width="48%" />
  <img src="https://danielwilliams.tech/Heatsinks/Heatsink_Design_18.png" width="48%" />
</p>

# Acknowledgements

F. Lange, F, C. Hein, G. Li , C. Emmelmann, “Numerical Optimization of Active Heat Sinks Considering Restrictions of Selective Laser Melting”, Fraunhofer Institution for Additive Production Technologies. 2018.

M2DO Research: “Heat Transfer Optimization”, http://m2do.ucsd.edu/research/

J. Alexandersen, O. Sigmund, K. E. Meyer, B. S. Lazarov, “Design of passive coolers for light-emitting diode lamps using topology optimization”, International Journal of Heat and Mass Transfer. February 2018

“Convective Heat Transfer.” Engineering ToolBox, (2003). https://www.engineeringtoolbox.com/convective-heat-transfer-d_430.html Accessed 14 Nov. 2020.

“Thermal Conductivity of selected Materials and Gases.” Engineering ToolBox, (2003). https://www.engineeringtoolbox.com/thermal-conductivity-d_429.html Accessed 14 Nov. 2020.

# Project Dependencies

JCuda and OBJ dependencies are optional, although some changes may be needed to get it to compile without them:

1. JogAmp JOGL (For 3D visualization)
   - https://jogamp.org/jogl/www/
1. JCuda (If you want to run it on the GPU. Otherwise, parallel CPU is still available.)
   - http://www.jcuda.org/jcuda/JCuda.html
1. Visual Studio (This is needed for the cl.exe CUDA compiler used in CUDAProgram.java)
1. obj-0.3.0 (For saving meshes as OBJ format.)
   - https://github.com/javagl/Obj