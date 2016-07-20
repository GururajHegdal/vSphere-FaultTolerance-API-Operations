# vSphere Fault Tolerance Feature API operations utility
### 1. Details
Utility to illustrate vSphere Fault Tolerance operations on provided Cluster and VM 
Illustrates how to 
 * Enable, Poweron FT VM
 * Disable, Enable Secondary VM
 * Make Primary VM / Test Failover
 
Flow through of the solution:
 * Connect to provided vCenter Server and Retrieve all Clusters. Check if user provided cluster exists and if vSphere HA is enabled on cluster.
 * Search for user provided VM or Pick a VM from one of the clustered host
 * Enable FT, monitor for secondary VM creation
 * Power on FT VM, monitor for Secondary VM power state and FT Pair protection state
 * Disable Secondary VM
 * Enable Secondary VM, monitor for FT Pair protection state to restore (i.e enabled and running)
 * Promote Secondary to Primary VM ('Test Failover' : Kill primary and check Secondary VM gets promoted to Primary and a new secondary VM gets created), monitor FT Pair protection state
 * Revert the inventory state: Power off VM and Turn off FT
  
### 2. How to run the Utility?
##### Run from Dev IDE

 * Import files under the src/faulttolerance/ folder into your IDE.
 * Required libraries are embedded within Runnable-Jar/ftops.jar, extract & import the libraries into the project.
 *  Run the utility from 'RunApp' program by providing arguments like:  
 _--vsphereip 192.168.10.1 --username adminUser --password dummyPasswd --clusterName GuruCluster --vmName TestVM_


##### Run from Pre-built Jars
 * Copy/Download the ftops.jar from Runnable-jar folder (from the uploaded file) and unzip on to local drive folder say c:\ftops
 * Open a command prompt and cd to the folder, lets say cd ftops
 * Run a command like shown below to see various usage commands:  
 _C:\ftops>java -jar ftops.jar --help_
