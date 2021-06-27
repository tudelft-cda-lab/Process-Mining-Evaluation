import os

from src.main import load_model


def render_dir(bpmn_directory: str, out_directory: str, flatten=True):
    """
    Helper to render all process models in a directory. The structure of nested directories is maintained in the results.
    :param bpmn_directory: current directory containing process models
    :param out_directory: current directory for the rendered models
    :param flatten: True to flatten the models
    """
    for filename in os.listdir(bpmn_directory):
        full_name = f"{bpmn_directory}/{filename}"
        if os.path.isdir(full_name):
            render_dir(full_name, f"{out_directory}/{filename}")
            continue
        elif filename.endswith(".bpmn") or filename.endswith(".ptml"):
            try:
                print(f"Rendering {filename}")
                model = load_model(full_name)
                if flatten:
                    model.flatten()
                model_name = filename[:-5]
                model.save(model_name, out_directory, format="svg", view=False, simple=True,
                           rank="LR")
                model.save(model_name, out_directory, format="png", view=False, simple=True,
                           rank="LR")
            except:
                print("ERR - could not render model")
        else:
            print(f"Unknown file type: {filename}, skipping")


if __name__ == '__main__':
    bpmn_root = "thesis_presentation"
    out_root = "thesis_presentation"
    render_dir(bpmn_root, out_root)
