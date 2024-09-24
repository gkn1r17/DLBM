************** Dispersed Lineage Based Model ****************
Geoff Neumann, 2024

To run on distributed cluster (assuming SLURM environment):
1) Move run folder onto server
2) Modify "settings" file as required or copy and create new settings file.
3) Navigate to run folder on server
4) Submit jobs with below command. NUMNODES refers to the number of computers you wish to run distributed across,
   which should always match the number of 2nd level clusters in your cluster file.
   For UVic (6386 box) TM running on Iridis 6 it is optimal to use the "clusters6386.csv" cluster file which is set up for 2 computers, hence "NUMNODES 2"
   
      sbatch runLBM.slurm LBM.jar NUMNODES 2 SETTINGS settings

Output:
In output folder:
 - Abundance of every lineage in every location - CSV for each regular time interval (default = 100 years, see settings file for details)
 - Copy of settings
 - Empty file beginning "seed" with name providing random seed used for each cluster in case need to recreate results
In run folder (outside output file) 
 - SLURM output file: Total number of lineages in every location at regular time intervals (default = 1 year, see settings) in (will add more details when uploaded R processing code).

Troubleshooting:
Output while running will appear in the run folder a file "slurm-[job id].out" where [job id] is a numeric code that you will see printed to console after you start each job. 
If jobs end immediately and you see the below error (with different numbers) in the output file then this appears to be a random error with slurm (or the Iridis cluster?) - just rerun the job. It will probably be necessary to rerun each job multiple times before one successfully runs.

red6,069: Starting fmpjd in port 11030...is not possible! (Host red6,069 is unknown)
red6,070: Starting fmpjd in port 11030...is not possible! (Host red6,070 is unknown)


################################ CLUSTERING/ PARALLELIZATION - note this is just for computational efficiency w/ no effect on actual model behaviour #######################################

If using a new TM or running on a new distributed cluster if will be necessary to change the distribution/parallelization configuration. To do this:
1) Add "CLUST_FILE:[clusterfile]" to your settings file - pointing to your new cluster file (
2) modify the following two lines in runLBM.slurm.
   "nodes" should be changed from 2 to the number of computers you wish to run across, which should be equal to the number of 2nd level clusters in your cluster file
   "cpus-per-task" should be the maximum number of CPUs available on each computer which, if you've set up your cluster file optimally,
                 should ideally be similar to the number of 1st level clusters in each 2nd level cluster.
   
     #SBATCH --nodes=2
     #SBATCH --cpus-per-task=192

4) Run, ensuring you change the number after "NUMNODES" to your number of computers/top level clusters

To create a new cluster file:
1) Each line is a location, ordered by index in the TM.
2) The first column is locations with a high degree of movement between them (1st level clusters). Each will be housed on a separate core.
3) The second column organizes 1st level clusters into a small number of large 2nd level clusters with very little movement between them. Each will be housed on a separate computer.
(I can at some point upload an R script for producing both types of clusters)

Note: When running on a single compter simply set all second level clusters to "0", you can even fully serialize application by setting all first level clusters to "0" to. The one computer cluster file for the UVic TM is called "nondist6386.csv" and is available in this folder, simply add CLUST_FILE:nondist6386.csv to settings and set NUMNODES in your command argument and nodes in runLBM.slurm to 1.

