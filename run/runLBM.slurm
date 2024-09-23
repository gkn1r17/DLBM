#!/bin/bash

#SBATCH --mem=730000
#SBATCH --nodes=2
#SBATCH --cpus-per-task=192
#SBATCH --ntasks-per-node=1
#SBATCH --time=60:00:00

module load jdk
export FMPJ_HOME=/home/gkn1r17/FastMPJ
export PATH=$FMPJ_HOME/bin:$PATH


 if [ $# -lt 2 ]; then
 	echo "Sintaxis: $0 MPJ_PROGRAM DEVICE [PROGRAM_ARGS]"
 	exit -1
 fi

 PROGRAM=$1
 shift 1

 $FMPJ_HOME/bin/fmpjrun -Xmx650g -Xms650g -psl 1494514800 -np $SLURM_NTASKS -dmap -jar $PROGRAM $@
