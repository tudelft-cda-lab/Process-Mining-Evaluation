from pathlib import Path

from numpy.random import randint

from src.standalone.read_write import read_traces, write_traces


def split_traces(in_file: str, out_dir: str, k: int, dataset_name: str):
    all_traces = read_traces(in_file)
    n_traces = len(all_traces)
    trace_labels = randint(k, size=(n_traces,))

    Path(f"{out_dir}/train").mkdir(parents=True)
    Path(f"{out_dir}/eval").mkdir(parents=True)

    write_traces(f"{out_dir}/train/{dataset_name}.txt", all_traces)
    write_traces(f"{out_dir}/eval/{dataset_name}.txt", all_traces)

    for i in range(k):
        out_train_file = f"{out_dir}/train/{dataset_name}_{i}.txt"
        out_test_file = f"{out_dir}/eval/{dataset_name}_{i}.txt"

        train_traces = []
        test_traces = []
        for label, trace in zip(trace_labels, all_traces):
            if label == i:
                test_traces.append(trace)
            else:
                train_traces.append(trace)

        print(f"{len(train_traces)} training, {len(test_traces)} testing")

        write_traces(out_train_file, train_traces)
        write_traces(out_test_file, test_traces)

    print("Done")


if __name__ == '__main__':
    trace_file = "C:/Users/Geert/Desktop/Thesis/Datasets/Final/FF_traces/cptc_17_chronological.txt"
    output_dir = "C:/Users/Geert/Desktop/sm_eval/cptc_17_chronological"
    split_traces(trace_file, output_dir, 5, "cptc_17_chronological")
