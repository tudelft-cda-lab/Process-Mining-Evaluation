# BPMN Replay

Replay tool to evaluate a BPMN model or Process Tree given a set of event sequences.

## Usage

See the comments in `src/main.py` for an overview of how to use the tool.

The general workflow consists of three steps:

- Load the process model, which is either an `.bpmn` (BPMN) or `.ptml` (Process tree) file.
  Using the provided methods, both these types are converted to an internal representation of a BPMN model.
- Load the traces. This can be either a trace file in the FlexFringe format, or a custom `.json`.
  The JSON files contain additional information for each trace as computed by the `AD-Attack-Graph` code with
  the goal of allowing for filtering traces before replaying.
  These files are provided for CPTC'17 and CPTC'18 in the `data` in this project.
- Replay the (possibly filtered) traces on the model using `replay_traces`.
  This method computes a possible path for each of the traces in the given model, and counts how often all nodes and edges are transversed for all _perfectly fitting_ traces.
  If a trace does not fit in the model, the counts are not updated: information is only given in the program terminal output.
  Afterwards, the model with the updated counts can be visualized using the `save` and `render` methods for the model.

## Requirements

All requirements are defined in `requirements.txt` and can be installed using `pip install -r requirements.txt`

- Graphviz
