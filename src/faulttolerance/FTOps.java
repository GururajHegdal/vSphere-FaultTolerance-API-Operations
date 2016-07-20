/**
 * Utility class to illustrate FT operations on provided Cluster VM
 * -- On a specified Cluster - Check if (i) HA is enabled (ii) exist atleast 2 ESXi hosts
 * -- Find a VM or use VM specified by user for performing following FT operations,
 * ---- Enable FT, monitor for secondary vm creation
 * ---- Power on FT VM, monitor for Secondary VM power state and FT Pair protection state
 * ---- Disable Secondary VM
 * ---- Enable Secondary VM, monitor for FT Pair protection state to restore (i.e enabled and running)
 * ---- Promote Secondary to Primary VM ('Test Failover' : Kill primary and check Secondary VM gets promoted to
 *      Primary and a new secondary VM gets created), monitor FT Pair protection state
 * -- Revert the inventory state
 * ---- Power off the VM
 * ---- Turn off FT on VM
 *
 * Copyright (c) 2016
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * @author Gururaja Hegdal (ghegdal@vmware.com)
 * @version 1.0
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package faulttolerance;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vmware.vim25.ClusterConfigInfo;
import com.vmware.vim25.ClusterDasConfigInfo;
import com.vmware.vim25.HostRuntimeInfo;
import com.vmware.vim25.HostSystemConnectionState;
import com.vmware.vim25.TaskInfoState;
import com.vmware.vim25.VirtualMachineFaultToleranceState;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.mo.ClusterComputeResource;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;

public class FTOps
{
    // VC inventory related objects
    public static final String DC_MOR_TYPE = "Datacenter";
    public static final String CLUSTER_COMPRES_MOR_TYPE = "ClusterComputeResource";
    public static final String VC_ROOT_TYPE = "VCRoot";
    public static final String HOST_MOR_TYPE = "HostSystem";
    public static final String VM_MOR_TYPE = "VirtualMachine";

    private final int TASK_TIMEOUT = 240; //seconds

    private String vsphereIp;
    private String userName;
    private String password;
    private String url;
    private ServiceInstance si;
    private String clusterName;
    private String primaryVmName;
    private HostSystem primaryHostSys;
    private VirtualMachine ftVmObj;

    /**
     * Constructor
     */
    public FTOps(String[] cmdProps)
    {
        makeProperties(cmdProps);
    }

    /**
     * Default constructor
     */
    public FTOps()
    {
        //Placeholder
    }

    /**
     * Read properties from command line arguments
     */
    private void
    makeProperties(String[] cmdProps)
    {
        // get the property value and print it out
        System.out.println("Reading vSphere IP and Credentials information from command line arguments");
        System.out.println("-------------------------------------------------------------------");

        for (int i = 0; i < cmdProps.length; i++) {
            if (cmdProps[i].equals("--vsphereip")) {
                vsphereIp = cmdProps[i + 1];
                System.out.println("vSphere IP:" + vsphereIp);
            } else if (cmdProps[i].equals("--username")) {
                userName = cmdProps[i + 1];
                System.out.println("Username:" + userName);
            } else if (cmdProps[i].equals("--password")) {
                password = cmdProps[i + 1];
                System.out.println("password: ******");
            } else if (cmdProps[i].equals("--clusterName")) {
                clusterName = cmdProps[i + 1];
                System.out.println("Cluster Name:" + clusterName);
            }  else if (cmdProps[i].equals("--vmName")) {
                primaryVmName = cmdProps[i + 1];
                System.out.println("VM Name:" + primaryVmName);
            }
        }
        System.out.println("-------------------------------------------------------------------\n");
    }

    /**
     * Validate property values
     */
    boolean
    validateProperties()
    {
        boolean val = false;
        if (vsphereIp != null) {
            url = "https://" + vsphereIp + "/sdk";

            try {
                System.out.println("Logging into vSphere : " + vsphereIp + ", with provided credentials");
                si = loginTovSphere(url);

                if (si != null) {
                    System.out.println("Succesfully logged into vSphere: " + vsphereIp);
                    val = true;
                } else {
                    System.err.println(
                        "Service Instance object for vSphere:" + vsphereIp + " is null, probably we failed to login");
                    printFailedLoginReasons();
                }
            } catch (Exception e) {
                System.err.println(
                    "Caught an exception, while logging into vSphere :" + vsphereIp + " with provided credentials");
                printFailedLoginReasons();
            }
        } else {
            System.err.println("vSphere IP is null. See below the usage of script");
            RunApp.usage();
        }

        return val;
    }

    /**
     * Login method to VC/ESXi
     */
    private ServiceInstance
    loginTovSphere(String url)
    {
        try {
            si = new ServiceInstance(new URL(url), userName, password, true);
        } catch (Exception e) {
            System.out.println("Caught exception while logging into vSphere server");
            e.printStackTrace();
        }
        return si;
    }

    /**
     * Method prints out possible reasons for failed login
     */
    private void
    printFailedLoginReasons()
    {
        System.err.println(
            "Possible reasons:\n1. Provided username/password credentials are incorrect\n"
                + "2. If username/password or other fields contain special characters, surround them with double "
                + "quotes and for non-windows environment with single quotes (Refer readme doc for more information)\n"
                + "3. vCenter Server/ESXi server might not be reachable");
    }

    /**
     * Check and apply Advanced option - "das.heartbeatDsPerHost" on HA Enabled Cluster
     */
    void
    performFTOps()
    {
        try {

            // check and retrieve HA Enabled Cluster and its hosts
            Map<ManagedEntity, List<HostSystem>> clusterNHostsMap = retrieveHAClusterNHosts(clusterName);

            if (clusterNHostsMap != null && clusterNHostsMap.size() > 0) {
                ManagedEntity haCluster = clusterNHostsMap.keySet().iterator().next();
                List<HostSystem> clusteredHosts = clusterNHostsMap.get(haCluster);

                if (primaryVmName != null) {
                    ftVmObj = findVm(clusterNHostsMap, primaryVmName);
                } else {
                    // Get a VM from any of the clustered host
                    for (HostSystem hostSys : clusteredHosts) {
                        VirtualMachine[] allVms = hostSys.getVms();

                        if (allVms != null && allVms.length > 0) {
                            primaryHostSys = hostSys;
                            ftVmObj = allVms[0];
                            primaryVmName = ftVmObj.getName();
                            System.out.println("Taking VM: " + primaryVmName + " for FT operations");
                            break;
                        }
                    }
                }

                // Get Secondary host
                HostSystem secondaryHostSys = null;
                for (HostSystem hostSys : clusteredHosts) {
                    if (!hostSys.getName().equals(primaryHostSys.getName())) {
                        secondaryHostSys = hostSys;
                    }
                }

                if (ftVmObj != null) {
                    boolean ftTurnedOn = false;
                    System.out.println("\n* * * * Turning on FT on VM: " + primaryVmName + " * * * *");
                    Task createSecVmTask = ftVmObj.createSecondaryVM_Task(secondaryHostSys);

                    if (monitorTask(createSecVmTask, TASK_TIMEOUT)) {
                        System.out.println("Successfully Turned on FT");
                        // Power on the Primary VM
                        if (monitorTask(ftVmObj.powerOnVM_Task(null), TASK_TIMEOUT)) {
                            System.out.println("FT Primary VM successfully powered on");
                            System.out.println("Now Monitor for its secondary VM");
                            if(waitForFTProtectionState(ftVmObj, TASK_TIMEOUT)) {
                                System.out.println("FT Pair is successfully powered on");
                                System.out.println("VM FT State: " + ftVmObj.getRuntime().getFaultToleranceState());
                                ftTurnedOn = true;
                            } else {
                                System.err.println("Failed to Power on Secondary VM");
                            }
                        } else {
                            System.err.println("Failed to Power on FT VM");
                        }
                    } else {
                        System.out.println("Failed to Turn on FT");
                    }

                    if (ftTurnedOn) {
                        System.out.println("\n* * * * Disable Secondary VM of FT VM " + primaryVmName + " * * * *");
                        // Get Secondary VM reference
                        VirtualMachine secondaryVMObj = null;
                        VirtualMachine[] secHostVms = secondaryHostSys.getVms();
                        for (VirtualMachine tempVm : secHostVms) {
                            if (tempVm.getName().equals(primaryVmName)) {
                                secondaryVMObj = tempVm;
                            }
                        }

                        if (secondaryVMObj != null) {
                            Task disableSecTask = ftVmObj.disableSecondaryVM_Task(secondaryVMObj);
                            if (monitorTask(disableSecTask, 120)) {
                                System.out.println("Successfully disabled Secondary VM");
                                System.out.println("VM FT State: " + ftVmObj.getRuntime().getFaultToleranceState());

                                System.out.println("\n* * * * Enable Secondary VM of FT VM " + primaryVmName + " * * * *");
                                Task enableSecTask = ftVmObj.enableSecondaryVM_Task(secondaryVMObj, secondaryHostSys);
                                if (monitorTask(enableSecTask, TASK_TIMEOUT)) {
                                    if(waitForFTProtectionState(ftVmObj, TASK_TIMEOUT)) {
                                        System.out.println("Successfully enabled Secondary VM");
                                        System.out.println("VM FT State: " + ftVmObj.getRuntime().getFaultToleranceState());
                                        ftTurnedOn = true;
                                    } else {
                                        System.err.println("Failed to Enable Secondary VM");
                                    }
                                }
                            }

                            System.out.println("\n* * * * Promote Secondary VM to Primary VM  (Test Failover) * * * *");
                            Task makePrimary = ftVmObj.makePrimaryVM_Task(secondaryVMObj);
                            if (monitorTask(makePrimary, TASK_TIMEOUT)) {
                                // Check if failover started
                                if (waitForFailover(ftVmObj, TASK_TIMEOUT)) {
                                    System.out.println("Now, wait for secondary to come up");
                                    if (waitForFTProtectionState(ftVmObj, TASK_TIMEOUT)) {
                                        System.out.println("Successfully Promoted Secondary to Primary VM");
                                        System.out
                                            .println("VM FT State: " + ftVmObj.getRuntime().getFaultToleranceState());
                                    } else {
                                        System.err.println("Failed to Promote Secondary VM");
                                    }
                                } else {
                                    System.err.println(
                                        "FT VM state did not enter into NeedSecondary/Starting, after 'Test Failover'");
                                }
                            }
                        } else {
                            System.out.println("Could not obtain Secondary VM's reference object");
                        }
                    }

                    System.out.println("\n* * * * Turn off FT on VM  * * * *");
                    VirtualMachineFaultToleranceState vmFtState = ftVmObj.getRuntime().getFaultToleranceState();
                    if (vmFtState.equals(VirtualMachineFaultToleranceState.enabled)
                        || vmFtState.equals(VirtualMachineFaultToleranceState.running)) {
                        Task turnOffFtTask = ftVmObj.turnOffFaultToleranceForVM_Task();
                        if (monitorTask(turnOffFtTask, TASK_TIMEOUT)) {
                            System.out.println("Successfully Turned off FT on VM");
                        } else {
                            System.err.println("Failed to turn off FT");
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Caught an exception while performing FT Operations on " + clusterName);
            e.printStackTrace();
            restoreInventoryState(ftVmObj);
        }

        restoreInventoryState(ftVmObj);
    }

    /**
     * Restore VM's State
     */
    private void
    restoreInventoryState(VirtualMachine vmObj)
    {
        try {
            if (vmObj != null) {
                System.out.println("\n* * * * Restore VM State  * * * *");
                Thread.sleep(1000 * 5);

                // Check power state and Power off VM
                if (vmObj.getRuntime().getPowerState().equals(VirtualMachinePowerState.poweredOn)) {
                    if (monitorTask(vmObj.powerOffVM_Task(), TASK_TIMEOUT)) {
                        System.out.println("Successfully powered off the VM");
                        Thread.sleep(1000 * 5);
                    }
                }
                // Turn off FT
                VirtualMachineFaultToleranceState vmFtState = vmObj.getRuntime().getFaultToleranceState();
                if (vmFtState.equals(VirtualMachineFaultToleranceState.enabled)
                    || vmFtState.equals(VirtualMachineFaultToleranceState.running)) {
                    if (monitorTask(vmObj.turnOffFaultToleranceForVM_Task(), TASK_TIMEOUT)) {
                        System.out.println("Successfully Turned off FT on VM");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Caught exception while restoring VM state. Pls check and restore the state");
        }
    }

    /**
     * Monitor the task state
     */
    private boolean
    monitorTask(Task taskRef, int timeoutSecs)
    {
        boolean taskSucceeded = false;
        try {
            // Monitor the task status
            int loopDelay = 5; //seconds
            int count = timeoutSecs / loopDelay;
            if (taskRef != null) {
                while (count > 0) {
                    TaskInfoState taskState = taskRef.getTaskInfo().getState();
                    if (taskState.equals(TaskInfoState.queued) || taskState.equals(TaskInfoState.running)) {
                        System.out
                            .println("Task is still running, wait for the task to complete");
                        Thread.sleep(1000 * 2);
                        --count;
                    } else if (taskState.equals(TaskInfoState.success)) {
                        System.out.println("Task succeeded");
                        taskSucceeded = true;
                        break;
                    } else if (taskState.equals(TaskInfoState.error)) {
                        System.out.println("Task Failed");
                        break;
                    }
                }
            } else {
                System.err.println("Task reference is null");
            }

        } catch (Exception e) {
            System.err.println("Caught an exception while monitoring the task");
        }

        return taskSucceeded;
    }

    /**
     * Monitor the task state
     */
    private boolean
    waitForFTProtectionState(VirtualMachine vmObj, int timeoutSecs)
    {
        boolean ftProtected = false;
        try {
            // Monitor the task status
            int loopDelay = 5; //seconds
            int count = timeoutSecs / loopDelay;
            if (vmObj != null) {
                while (count > 0) {
                    VirtualMachineFaultToleranceState vmFTState = vmObj.getRuntime().getFaultToleranceState();
                    if (vmFTState.equals(VirtualMachineFaultToleranceState.starting)
                        || vmFTState.equals(VirtualMachineFaultToleranceState.needSecondary)) {
                        System.out.println("Secondary VM is still starting up, wait for the power on to complete");
                        Thread.sleep(1000 * 2);
                        --count;
                    } else if (vmFTState.equals(VirtualMachineFaultToleranceState.running)) {
                        System.out.println("Secondary VM is running now");
                        ftProtected = true;
                        break;
                    }
                }
            } else {
                System.err.println("VirtualMachine reference is null");
            }

        } catch (Exception e) {
            System.err.println("Caught an exception while monitoring the FT Secondary VM state");
        }

        return ftProtected;
    }

    /**
     * Wait until VM's FT state turns to NotRunning / NeedSecondary
     */
    private boolean
    waitForFailover(VirtualMachine vmObj, int timeoutSecs)
    {
        boolean ftUnProtected = false;
        try {
            // Monitor the task status
            int loopDelay = 5; //seconds
            int count = timeoutSecs / loopDelay;
            if (vmObj != null) {
                while (count > 0) {
                    VirtualMachineFaultToleranceState vmFTState = vmObj.getRuntime().getFaultToleranceState();
                    if (vmFTState.equals(VirtualMachineFaultToleranceState.starting)
                        || vmFTState.equals(VirtualMachineFaultToleranceState.needSecondary)) {
                        System.out.println("Secondary VM is not in running state now");
                        ftUnProtected = true;
                        break;
                    } else if (vmFTState.equals(VirtualMachineFaultToleranceState.running)) {
                        System.out.println(
                            "Secondary VM is still in running state, wait for it to go-into 'starting/needSecondary' state");
                        Thread.sleep(1000 * 2);
                        --count;
                    }
                }
            } else {
                System.err.println("VirtualMachine reference is null");
            }

        } catch (Exception e) {
            System.err.println("Caught an exception while monitoring the FT Secondary VM state");
        }

        return ftUnProtected;
    }

    /**
     * Find VM from the clustered hosts and return its VirtualMachine object
     */
    private VirtualMachine
    findVm(Map<ManagedEntity, List<HostSystem>> clusterHostMap, String userRequestedVM)
    {
        VirtualMachine vmObj = null;

        try {
            List<HostSystem> allHosts = clusterHostMap.get(clusterHostMap.keySet().iterator().next());
            outerLoop:
            for (HostSystem tempHs : allHosts){
                for (VirtualMachine tempVmObj : tempHs.getVms()) {
                    if (tempVmObj.getName().equals(userRequestedVM)) {
                        System.out.println("Found VM: " + userRequestedVM + " on Host: " + tempHs.getName());
                        primaryHostSys = tempHs;
                        vmObj = tempVmObj;
                        break outerLoop;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Caught while searching for VM: " + userRequestedVM);
        }

        return vmObj;
    }

    /**
     * All hosts from HA Enabled Cluster
     */
    private Map<ManagedEntity, List<HostSystem>>
    retrieveHAClusterNHosts(String userRequestedClusterName)
    {
        boolean foundHAEnabledOnCluster = false;

        Map<ManagedEntity, List<HostSystem>> userClusterHostsMap = new HashMap<ManagedEntity, List<HostSystem>>();

        try {
            InventoryNavigator navigator = new InventoryNavigator(si.getRootFolder());

            ManagedEntity[] allClusters = navigator.searchManagedEntities(CLUSTER_COMPRES_MOR_TYPE);

            if (allClusters.length > 0) {
                System.out.println("Found Clusters in inventory. Check and retrieve HA Enabled Cluster");

                /*
                 * Traverse through each Cluster and find the user requested cluster
                 */
                for (ManagedEntity tempCluME : allClusters) {
                    if (tempCluME.getName().equals(userRequestedClusterName)) {
                        ClusterComputeResource ccr = new ClusterComputeResource(si.getServerConnection(),
                            tempCluME.getMOR());

                        // Check if HA is enabled on Cluster
                        ClusterConfigInfo tempCluConfigInfo = ccr.getConfiguration();
                        ClusterDasConfigInfo fdmConfigInfo = tempCluConfigInfo.getDasConfig();

                        if (fdmConfigInfo != null && fdmConfigInfo.enabled) {
                            System.out.println("HA is enabled on Cluster: " + tempCluME.getName());
                            foundHAEnabledOnCluster = true;

                            // retrieve all hosts from the cluster
                            HostSystem[] allHosts = ccr.getHosts();
                            if (allHosts.length > 0) {
                                System.out.println("Found ESXi host(s). Check for all connected hosts");
                                List<HostSystem> activeHosts = new ArrayList<HostSystem>();
                                for (ManagedEntity tempHost : allHosts) {
                                    HostSystem tempHostSys = (HostSystem) tempHost;
                                    HostRuntimeInfo hostruntimeInfo = tempHostSys.getRuntime();
                                    if ((hostruntimeInfo.getConnectionState()
                                        .equals(HostSystemConnectionState.connected))) {
                                        System.out.println(
                                            "Found ESXi host: " + tempHostSys.getName() + " in connected state");
                                        activeHosts.add(tempHostSys);
                                    }
                                }
                                if (activeHosts.size() >= 2) {
                                    userClusterHostsMap.put(tempCluME, activeHosts);
                                } else {
                                    System.err.println(
                                        "Could not find minimum number (2) of ESXi hosts in connected state, for this cluster: "
                                            + tempCluME.getName());
                                }
                            }
                        } else {
                            System.err
                                .println("HA is not enabled on the user provided cluster: " + userRequestedClusterName);
                        }
                    }
                } // End of clusters loop

                if (!(userClusterHostsMap != null && userClusterHostsMap.size() > 0)) {
                    if (!foundHAEnabledOnCluster) {
                        System.err.println(
                            "Could not find Cluster: \"" + userRequestedClusterName + " \"in vCenter Server inventory");
                    }
                }
            } else {
                System.err.println("Could not find any clusters in vCenter Server");
            }

        } catch (Exception e) {
            System.err.println("[Error] Unable to retrieve Clusters from inventory");
            e.printStackTrace();
        }

        return userClusterHostsMap;
    }
}