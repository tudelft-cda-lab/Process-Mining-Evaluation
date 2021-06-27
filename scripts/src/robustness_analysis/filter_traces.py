from src.standalone.read_write import read_traces, write_traces

events = {
    "resHJ|wireless",
    "remoteexp|hostingServer",
    "acctManip|wireless",
    "ACE|http(s)",
    "ACE|wireless",
    "dManip|http(s)",
    "exfil|http(s)",
    "delivery|hostingServer",
    "netDOS|http(s)",
    "delivery|http(s)",
    "resHJ|http(s)",
    "exfil|hostingServer",
    "dManip|hostingServer",
    "resHJ|hostingServer",
    "ACE|hostingServer",
    "acctManip|hostingServer",
    "CnC|hostingServer",
    "privEsc|http(s)",
    "delivery|wireless",
    "exfil|wireless",
    "dManip|wireless",
    "remoteexp|wireless",
    "rPrivEsc|wireless",
}


if __name__ == '__main__':
    trace_file = "C:/Users/Geert/Desktop/Thesis/Datasets/Final/FF_traces/cptc_18_reversed.txt"
    base_traces = read_traces(trace_file)

    new_traces = []
    for trace in base_traces:
        new_trace = []
        for event in trace:
            if event in events:
                new_trace.append(event)
            else:
                break
        if len(new_trace) > 0:
            new_traces.append(new_trace)

    write_traces("traces_small/small/traces_small.txt", new_traces)
