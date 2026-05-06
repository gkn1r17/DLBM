#!/bin/bash
#SBATCH --mem=120000
#SBATCH --time 60:00:00
#SBATCH --nodes=1
#SBATCH --cpus-per-task=1
#SBATCH --output=blah.out
#SBATCH --error=blah.err


echo "convertFiles($1, $2, $3, $4, $5)"
ulimit -c 0
module load matlab/2024a
matlab -nodisplay -nosplash -r "clear; clc; convertFiles($1, $2, $3, $4,  $5);  exit"