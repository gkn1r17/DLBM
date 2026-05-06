#!/bin/bash
#SBATCH --mem=120000
#SBATCH --nodes=1
#SBATCH --cpus-per-task=40
#SBATCH --ntasks-per-node=1
#SBATCH --time=60:00:00
module load jdk

java -Xmx100g -Xms100g -jar collatePhylo.jar $@

#SBATCH --output=10000k.out
