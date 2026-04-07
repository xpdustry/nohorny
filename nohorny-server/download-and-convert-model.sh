#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VENV_DIR="$ROOT_DIR/.venv"

if [ ! -d "$VENV_DIR" ]; then
  uv python install 3.14
  uv venv -p 3.14 "$VENV_DIR"
fi

source "$VENV_DIR/bin/activate"
uv pip install --index-url https://download.pytorch.org/whl/cpu torch
uv pip install transformers
"$VENV_DIR/bin/python" "$ROOT_DIR/download-and-convert-model.py" "$@"
