import os
import sys
from pathlib import Path

os.environ["JIRADASH_MODE"] = "demo"
os.environ["DATABASE_URL"] = "sqlite:///" + str(Path(__file__).parent / "test.db")
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
