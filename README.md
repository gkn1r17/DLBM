To run on distributed cluster (assuming SLURM environment):
1) Move run folder onto server
2) Modify "settings" file as required or copy and create new settings file.
3) Navigate to run folder on server
4) Submit jobs with below command. NUMNODES refers to the number of computers you wish to run distributed across,
   which should always match the number of 2nd level clusters in your cluster file.
   For UVic (6386 box) TM running on Iridis 6 it is optimal to use the "clusters6386.csv" cluster file which is set up for 2 computers, hence "NUMNODES 2"
   
      sbatch runLBM.slurm LBM.jar NUMNODES 2 SETTINGS settings


################################

If using a new TM or running on a new distributed cluster if will be necessary to change the distribution/parallelization configuration. To do this:
1) Add "CLUST_FILE:[clusterfile]" to your settings file - pointing to your new cluster file (
2) modify the following two lines in runLBM.slurm.
   "nodes" should be changed from 2 to the number of computers you wish to run across, which should be equal to the number of 2nd level clusters in your cluster file
   "cpus-per-task" should be the maximum number of CPUs available on each computer which, if you've set up your cluster file optimally,
                 should ideally be similar to the number of 1st level clusters in each 2nd level cluster.
   
     #SBATCH --nodes=2
     #SBATCH --cpus-per-task=192

4) Run, ensuring you change the number after "NUMNODES" to your number of computers/top level clusters

################################



