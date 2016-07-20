/**
 * Entry point into the vSphere Fault Tolerance API usage sample
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

public class RunApp
{
    /**
     * Usage method - how to use/invoke the script, reveals the options supported through this script
     */
    public static void usage()
    {
        System.out.println(
            "Usage: java -jar ftconfig.jar --vsphereip <vc/esxi server IP> --username <uname> --password <pwd> --clusterName <cluster name> [--vmName <vmName>]");
        System.out.println("\nExample : To Enable/Disable FT on any VM from Cluster");
        System.out.println(
            "\"java -jar ftconfig.jar --vsphereip 10.1.2.3 --username adminUser --password dummy --clusterName TestCluster\"");
        System.out.println("\nExample : To Enable/Disable FT on specific VM from Cluster");
        System.out.println(
            "\"java -jar ftconfig.jar --vsphereip 10.1.2.3 --username adminUser --password dummy --clusterName TestCluster --vmName TestVM\"");
     }

    /**
     * Main entry point into the Script
     */
    public static void main(String[] args) {

        System.out
            .println("######################### Fault Tolerance Script execution STARTED #########################");

        // Read command line arguments
        if (args.length > 0 && args.length >= 8) {
            FTOps ftOpSample = new FTOps(args);

            // validate arguments
            if (ftOpSample.validateProperties()) {

                // perform FT Operations
                ftOpSample.performFTOps();
            }
        } else {
            usage();
        }

        try {
            Thread.sleep(1000 * 2);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println(
            "######################### Fault Tolerance Script execution completed #########################");
    }
}