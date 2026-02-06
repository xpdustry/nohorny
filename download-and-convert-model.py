import torch, transformers

if __name__ == "__main__":
    class ExtractingLogits(torch.nn.Module):
        def __init__(self, model):
            super().__init__()
            self.model = model

        def forward(self, x):
            return self.model(x).logits

    stage_1 = transformers.AutoModelForImageClassification.from_pretrained("Falconsai/nsfw_image_detection")
    stage_1.eval()

    stage_2 = ExtractingLogits(stage_1)
    stage_2.eval()

    random = torch.randn(1, 3, 224, 224)
    result = torch.jit.trace(stage_2, random)

    torch.jit.save(result, "falconsai_nsfw_image_detection.pt")