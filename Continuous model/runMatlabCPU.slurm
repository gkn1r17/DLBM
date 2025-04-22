#!/bin/bash

#SBATCH --mem=150000
#SBATCH --nodes=1
#SBATCH --ntasks-per-node=40
#SBATCH --time=60:00:00

module load matlab
#ContinuousModel(folder, TMfile, neutral, seasonal, dispHours, growthHours)
echo "ContinuousModel(\"$1\", \"$2\", $3, $4, $5, $6, 0, $7)"
matlab -nodisplay -nosplash -r "clear; clc; ContinuousModel(\"$1\", \"$2\", $3, $4, $5, $6, 0, $7);  exit"