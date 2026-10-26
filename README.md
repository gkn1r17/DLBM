See "instructions" for documentation


Reference experiment: Global plankton biogeography

The model can qualitatively reproduce the global biogeographical structure reported by Milke et al. using continuous lineage abundances, daily ocean transport and seasonal temperature-dependent selection.

A reproducible reference configuration is provided in run/benchmarks/Milke23_benchmark.sh.

The experiment initialises one geographically distinct lineage in each of 1,972 ECCO provinces. Thermal optima are assigned from integer-rounded local annual mean SST and dormant cells are included. Lineages subsequently disperse through the global ocean under seasonally varying physical transport and temperature forcing.

With continuous abundances and no extinction threshold, lineages sharing identical thermal optima eventually converge towards proportional geographical distributions, despite their different origins. Distinct thermal phenotypes nevertheless retain geographical structure through environmental selection.

The experiment provides a limiting case demonstrating that the model can reproduce the principle “everything is everywhere, but the environment selects”.

The central research challenge is to determine whether comparable patterns emerge under more realistic assumptions about dispersal, demographic stochasticity and evolutionary dynamics.

This configuration is intended as a qualitative reference experiment rather than a quantitatively validated reproduction of the observations.