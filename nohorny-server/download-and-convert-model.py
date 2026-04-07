import argparse
import tempfile

# pip install torch --index-url https://download.pytorch.org/whl/cpu
# pip install transformers

def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "model",
        nargs="?",
        default="Falconsai/nsfw_image_detection",
        help="Hugging Face model id to download and convert.",
    )
    parser.add_argument(
        "--token",
        help="Hugging Face token for private/gated models.",
    )
    parser.add_argument(
        "--output",
        help="Output .pt file path. Defaults to a sanitized model id.",
    )
    return parser.parse_args()


def default_output_path(model_id):
    model_name = model_id.lower().replace("/", "-").replace("_", "-")
    return f"{model_name}.pt"


if __name__ == "__main__":
    args = parse_args()
    output_path = args.output or default_output_path(args.model)
    import torch
    import transformers

    class ExtractingLogits(torch.nn.Module):
        def __init__(self, model):
            super().__init__()
            self.model = model

        def forward(self, x):
            return self.model(x).logits

    with tempfile.TemporaryDirectory(prefix="nohorny-model-") as temp_dir:
        stage_1 = transformers.AutoModelForImageClassification.from_pretrained(
            args.model,
            token=args.token,
            cache_dir=temp_dir,
        )
        stage_1.eval()

        stage_2 = ExtractingLogits(stage_1)
        stage_2.eval()

        random = torch.randn(1, 3, 224, 224)
        result = torch.jit.trace(stage_2, random)

    torch.jit.save(result, output_path)
