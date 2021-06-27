# Process Mining Benchmark

This codebase contains the evaluation setup used in the thesis `Mining Attacker Strategy` by Geert Habben Jansen.

### Running the code

Running the code is done through the jar from the `benchmark-custom` package. This jar is constructed by
executing `mvn clean install` in this directory. This creates a new jar in `benchmark-custom/target`. The benchmark is
then started through `java -jar benchmark-custom-1.0.20180320.jar <args>`.

*NOTE:* In order to execute the benchmark, add the `link_files` directory in the root of this repo to your `PATH`.
Alternatively, use `LD_LIBRARY_PATH=path/to/link_files java -jar benchmark-custom-1.0.20180320.jar <args>`

The first argument determines which functionality is used:

- `benchmark`: perform the evaluation for process mining algorithms -> see `BenchmarkCustom.java` for all options
- `dfa`: perform the evaluation over a state machine -> see `DFAEvaluator.java` for all options
- `hybrid`: perform the evaluation using pre-defined training/evaluation sets. Used for evaluating the hybrid
  approaches. See `BenchmarkCustomData.java` for all options

### Codebase origins

All code is based on Raffaele Conforti's [ResearchCode](https://github.com/raffaeleconforti/ResearchCode) for evaluating
process mining algorithms. Except for the `benchmark-custom` directory, all code stems from this repository. Any
modifications to this codebase are permitted per the
original [LGPL-3.0 License](http://www.gnu.org/licenses/lgpl-3.0.html).

#### Disclaimer from the base Raffaele Conforti's ResearchCode

This repository contains all my research code. For more information about my research work please
visit www.raffaeleconforti.com

The code is available as open source code; you can redistribute it and/or modify it under the terms of the GNU Lesser
General Public License as published by the Free Software Foundation; either version 3 of the License, or (at your
option) any later version.

My code is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty
of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU Lesser General Public License along with this it. If not,
see <http://www.gnu.org/licenses/lgpl-3.0.html>.

#### Mining algorithms:

- Structured Miner using Heuristics Miner ProM6.5 (sHM6)
- Structured Miner using Heuristics Miner ProM5.2 (sHM)
- Split Miner (SM)
- Naive HyperParam-Optimized Structured Heuristics Miner (HPO-SHM)
- Naive HyperParam-Optimized Split Miner (HPO-SM)
- Naive HyperParam-Optimized Inductive Miner Infrequent (HPO-IMf)
- Naive HyperParam-Optimized Heuristics Miner 6.0 (HPO-HM6)
- Naive HyperParam-Optimized Fodina (HPO-FO)
- Inductive Miner IM (IM)
- Inductive Miner - life cycle (IMlc)
- Inductive Miner - infrequent - life cycle (IMflc)
- Inductive Miner - infrequent - all operators (IMfa)
- Inductive Miner - infrequent (IMf)
- Inductive Miner - incompleteness (IMc)
- Inductive Miner - all operators (IMa)
- ILP Miner (ILP)
- Hybrid ILP Miner (HILP)
- Heuristics Miner ProM6 (HM6)
- Heuristics Miner ProM5.2 (HM)
- Heuristics Dollar (HM$)
- Fodina Miner (FO)
- Evolutionary Tree Miner (ETM)
- CN Miner (CNM)
- BPMNMiner (BPMNMiner)
- Alpha Miner Sharp (A#)
- Alpha Miner Plus Plus (A++)
- Alpha Miner Plus (A+)
- Alpha Miner Classic (AM)
- Alpha Dollar (A$)
- Alpha Algorithm (AA)

#### Metrics:

- Time Performance : avg-time
- Structuredness : struct.
- Soundness : soundness
- Size : size
- Projected f-Measure : (p)f-measure
- Projected f-Measure : (p)f-measure
- Projected f-Measure : (p)f-measure
- DAFSA Alignment-Based f-Measure : (d)f-measure
- DAFSA Alignment-Based Fitness : (d)fitness
- DAFSA Alignment-Based ETC Precision : (d)precision
- Control Flow Complexity : cfc
- Complexity on BPMN Model : size, cfc, struct.
- Alignment-Based f-Measure : (a)f-measure
- Alignment-Based Fitness : (a)fitness
- Alignment-Based ETC Precision : (a)precision
- 3-Fold Alignment-Based f-Measure : (a)(3-f)f-meas.
- 3-Fold Alignment-Based Fitness : (a)(3-f)fit.
- 3-Fold Alignment-Based ETC Precision : (a)(3-f)prec.
