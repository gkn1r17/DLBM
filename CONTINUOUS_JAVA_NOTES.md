# Continuous Java dLBM

Entry point: `continuous.ContinuousRunner`.

The continuous model uses a fixed set of populations and dense double-precision
abundance matrices. The transport matrix remains sparse. Mutation and stochastic
drift are absent.

## Initialisation options

Configured in `[Continuous]` in `run/default.ini` or overridden in another ini /
on the command line.

- `CONT_INIT_MODE=one_per_location`: one population is founded at each location.
  In selective runs its Topt is exactly the location's initial local temperature.
- `CONT_INIT_MODE=local_phenotypes`: each location founds one population for each
  value in `CONT_PHENOTYPE_OFFSETS`. Offsets are in degrees C relative to local
  initial temperature. Initial local biomass is divided equally among phenotypes.

Examples:

    CONT_INIT_MODE=one_per_location
    CONT_PHENOTYPE_OFFSETS=0.0

or

    CONT_INIT_MODE=local_phenotypes
    CONT_PHENOTYPE_OFFSETS=-4,0,4

`TEMP_FILE=none` gives a neutral run; otherwise the existing selective thermal
response is used. `K` is retained as the continuous abundance scale; `K=1` is the
natural standard setting. `INIT_LIN_SIZE` is not used by continuous mode.

## Output

`CONT_OUTPUT_MODE=binary` is recommended. It writes every dense matrix value as a
double without thresholding. For 1972 locations and one population per location,
one state is 3,888,784 doubles = 31,110,272 bytes (~29.7 MiB) per snapshot;
active+dormant is ~59.3 MiB. Population metadata are written once to
`populations.csv`.

`CONT_OUTPUT_MODE=csv` is provided for compatibility/debugging. Because continuous
dispersal makes the matrices dense, CSV can become very large and slow.
`CONT_OUTPUT_MIN_ABUNDANCE` can threshold CSV output only; it never affects model
state or binary output.

MATLAB reader: `Continuous model/readContinuousBinary.m`.

## Running

Build with Maven as usual, then from the `run` directory invoke the continuous
entry point explicitly, for example:

    java -cp ../target/LBM.jar continuous.ContinuousRunner K 1 FILE_OUT ../output/continuous_test

If using the shaded jar produced as `target/LBM.jar`, `java -jar` still launches
the stochastic `control.Runner`; continuous mode is deliberately a separate entry
point.

Continuous mode currently requires `MUTATION=0`, `BUILD_TM=false`, and
`FILE_LOAD=none`.
