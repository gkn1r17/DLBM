#!/usr/bin/env bash
set -euo pipefail

# Reproduction of Milke et al. global plankton biogeography.
#
# Reference experiment demonstrating that geographically distinct
# founding lineages can converge towards globally distributed
# assemblages structured by environmental selection.
#
# Key assumptions:
#   - Continuous abundance without demographic stochasticity
#   - One founding lineage per ECCO province
#   - Integer thermal optima based on local annual mean SST
#   - Daily transport without dispersal holding
#   - Seasonally varying SST
#   - No mutation
#   - 10% dormant fraction, 3600-day dormancy timescale
#   - 100,000-year integration (360 days per model year)
#
# This is a qualitative reproduction, not a calibrated model fit.

cd "$(dirname "$0")/.."

./runLBM.local.sh ../target/LBM.jar \
  MODEL continuous \
  NUMNODES 1 \
  INIT_MODE local_dominant \
  K 1 \
  MUTATION 0 \
  DORMANT_FRAC 0.1 \
  DORMANT_TAU_DAY 3600 \
  DURATION_DAY 36000000 \
  DISP_HOURS 24 \
  GROWTH_HOURS 24 \
  UNAVAILABLE_DAYS 0 \
  DISP_SCALER 1 \
  TM_FILE '1day_upper_50m_1972/monthly/TM_1day_upper_50m_1972_month%02d.txt' \
  TEMP_FILE '1day_upper_50m_1972/monthly/TEMP_upper_50m_1972_month%02d.csv'