def convert_traces(in_filename: str, out_filename: str, trace_converter=None):
    out_file = open(out_filename, "w+")
    out_file.write('"case","event"\n')

    if trace_converter is None:
        trace_converter = [trace_copy]

    in_file = open(in_filename, "r")

    header = [int(x) for x in in_file.readline().split()]
    n_traces = header[0]
    n_symbols = header[1]

    # Skip if the file is empty
    if n_traces == 0 or n_symbols == 0:
        out_file.close()
        in_file.close()
        return

    line = in_file.readline()
    class_label = line.split()[0]  # Used for verification, we only want one-class problems
    case_number = 1
    while line is not None and line != "":
        data = line.split()
        n_events = int(data[1])

        # Sanity check
        assert class_label == data[0]
        assert (n_events + 2) == len(data)

        events = data[2:]
        for conv in trace_converter:
            events = conv(events)

        # Write one entry for each event
        for index, event in enumerate(events):
            out_file.write(f'{case_number},"{event}"\n')

        # Move to the next trace, hence the next case number
        case_number += 1
        line = in_file.readline()

    # Close the outputs
    out_file.close()
    in_file.close()


def trace_copy(trace: [str]) -> [str]:
    return trace


def bigram(trace: [str]) -> [str]:
    result = [f"start->{trace[0]}"]
    for i in range(len(trace) - 1):
        result.append(f"{trace[i]}->{trace[i + 1]}")
    result.append(f"{trace[-1]}->end")
    return result


def reverse(trace: [str]) -> [str]:
    trace.reverse()
    return trace


def split_stage(trace: [str]) -> [str]:
    return [event.split("|")[0] for event in trace]


def split_service(trace: [str]) -> [str]:
    return [event.split("|")[1] for event in trace]


if __name__ == '__main__':
    # input_data = "../data/traces/cptc_17_reversed_min_frequency=2.txt"
    # convert_traces(input_data, "C:\\Users\\Geert\\Desktop\\Thesis\\Datasets\\Traces\\CSV\\cptc_17_reversed_min_frequency=2_reversed.csv")
    # convert_traces(input_data, "C:\\Users\\Geert\\Desktop\\Thesis\\Datasets\\Traces\\CSV\\cptc_17_reversed_min_frequency=2_chronological.csv",  trace_converter=[reverse])
    # convert_traces(input_data, output_data, trace_converter=[split_stage, bigram])
    # input_data = "C:\\Users\\Geert\\Desktop\\Thesis\\Scripts\\src\\replay\\data\\test_2.txt"
    input_data = "../robustness_analysis/traces_small/small/insert_ex_10.txt"
    convert_traces(input_data,
                   "insert_ex_10.csv",
                   trace_converter=[])
    # convert_traces(input_data,
    #                "C:/Users/Geert/Desktop/Thesis/Datasets/Final/FF_cptc_18_mcat/cptc_18_chronological_stage.csv",
    #                trace_converter=[reverse, split_stage])
