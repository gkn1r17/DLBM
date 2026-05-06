#!/bin/bash
#SBATCH --mem=120000
#SBATCH --time 60:00:00
#SBATCH --nodes=1
#SBATCH --cpus-per-task=40
#SBATCH --output=blah2.out
#SBATCH --error=blah2.err


echo "convertFiles($1, $2)"
ulimit -c 0
module load matlab/2024a
matlab -nodisplay -nosplash -r "clear; clc; convertFiles2(\"$1\", \"$2\");  exit"