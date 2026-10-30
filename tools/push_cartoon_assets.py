import os
import sys
import subprocess

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(REPO_ROOT, "tools"))
if "TV_HOST" not in os.environ:
    print("Please set TV_HOST environment variable (e.g. $env:TV_HOST = '192.168.1.50')")
    sys.exit(1)

import tv
dev = tv.connect()
remote_dir = "/storage/emulated/0/Android/data/com.gathin.tvhome/files/favourites/cartoon"
print(f"Creating remote directory {remote_dir}...")
out = dev.shell(f"mkdir -p {remote_dir}")
print("mkdir output:", out)
dev.close()

local_dir = os.path.join(REPO_ROOT, "tv-assets", "export", "cartoon")
files = [f for f in os.listdir(local_dir) if f.endswith(".png") or f.endswith(".json")]

for f in files:
    local_path = os.path.join(local_dir, f)
    remote_path = f"{remote_dir}/{f}"
    print(f"\n--- Pushing {f} ({os.path.getsize(local_path)} bytes) ---")
    cmd = [sys.executable, os.path.join(REPO_ROOT, "tools", "b64push.py"), local_path, remote_path]
    res = subprocess.run(cmd)
    if res.returncode != 0:
        print(f"Error pushing {f}!")
        sys.exit(1)

print("\nAll cartoon assets pushed successfully!")
