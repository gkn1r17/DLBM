#!/bin/bash

#SBATCH --mem=100000
#SBATCH --nodes=1
#SBATCH --ntasks-per-node=1
#SBATCH --time=60:00:00
#SBATCH -p gpu
#SBATCH --gres=gpu:1

module load matlab
#ContinuousModel(folder, TMfile, neutral, seasonal, dispHours, growthHours)
echo "ContinuousModel(\"$1\", \"$2\", $3, $4, $5, $6, 1, $7)"
matlab -nodisplay -nosplash -r "clear; clc; ContinuousModel(\"$1\", \"$2\", $3, $4, $5, $6, 1, $7);  exit"