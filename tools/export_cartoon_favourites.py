import os
import sys
import json
import numpy as np
from psd_tools import PSDImage
from PIL import Image

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PSD_PATH = os.path.join(REPO_ROOT, "tv-assets", "my-favourites-cartoon.psd")
EXPORT_DIR = os.path.join(REPO_ROOT, "tv-assets", "export", "cartoon")
os.makedirs(EXPORT_DIR, exist_ok=True)

MAPPING = {
    # Back row (7)
    "sho-yamato": {
        "id": "sho-yamato",
        "name": "Sho Yamato",
        "franchise": "Idaten Jump",
        "title": "Idaten Jump",
        "color": "#FF3B30",
        "row": "back"
    },
    "jinga-hagane": {
        "id": "jinga-hagane",
        "name": "Gingka Hagane",
        "franchise": "Beyblade: Metal Fusion",
        "title": "Beyblade: Metal Fusion",
        "color": "#007AFF",
        "row": "back"
    },
    "ben-10-classic": {
        "id": "ben-10-classic",
        "name": "Ben Tennyson",
        "franchise": "Ben 10",
        "title": "Ben 10",
        "color": "#34C759",
        "row": "back"
    },
    "ben-10-alienforce": {
        "id": "ben-10-alienforce",
        "name": "Ben Tennyson",
        "franchise": "Ben 10: Alien Force",
        "title": "Ben 10: Alien Force",
        "color": "#30D158",
        "row": "back"
    },
    "ash-ketchem": {
        "id": "ash-ketchem",
        "name": "Ash Ketchum",
        "franchise": "Pokémon",
        "title": "Pokémon",
        "color": "#FFD60A",
        "row": "back"
    },
    "kick-buttowski": {
        "id": "kick-buttowski",
        "name": "Kick Buttowski",
        "franchise": "Kick Buttowski",
        "title": "Kick Buttowski",
        "color": "#FF9F0A",
        "row": "back"
    },
    "oswald": {
        "id": "oswald",
        "name": "Oswald",
        "franchise": "Oswald",
        "title": "Oswald",
        "color": "#0A84FF",
        "row": "back"
    },
    # Front row (7)
    "doremon": {
        "id": "doremon",
        "name": "Doraemon",
        "franchise": "Doraemon",
        "title": "Doraemon",
        "color": "#40C4FF",
        "row": "back"
    },
    "shin-chan": {
        "id": "shin-chan",
        "name": "Shin-chan",
        "franchise": "Shin-chan",
        "title": "Shin-chan",
        "color": "#FF375F",
        "row": "front"
    },
    "ninja-hattori": {
        "id": "ninja-hattori",
        "name": "Ninja Hattori",
        "franchise": "Ninja Hattori",
        "title": "Ninja Hattori",
        "color": "#5856D6",
        "row": "front"
    },
    "tom-and-jerry": {
        "id": "tom-and-jerry",
        "name": "Tom & Jerry",
        "franchise": "Tom and Jerry",
        "title": "Tom and Jerry",
        "color": "#FF9500",
        "row": "front"
    },
    "roll-no-21": {
        "id": "roll-no-21",
        "name": "Kris",
        "franchise": "Roll No 21",
        "title": "Roll No 21",
        "color": "#64D2FF",
        "row": "front"
    },
    "oggy-cockroaches": {
        "id": "oggy-cockroaches",
        "name": "Oggy",
        "franchise": "Oggy and the Cockroaches",
        "title": "Oggy and the Cockroaches",
        "color": "#30B0C7",
        "row": "front"
    },
    "courage": {
        "id": "courage",
        "name": "Courage",
        "franchise": "Courage the Cowardly Dog",
        "title": "Courage the Cowardly Dog",
        "color": "#FF2D55",
        "row": "front"
    }
}

print(f"Loading PSD from {PSD_PATH}...")
psd = PSDImage.open(PSD_PATH)
print(f"Canvas size: {psd.size}")

lineup_data = {
    "canvas_width": psd.size[0],
    "canvas_height": psd.size[1],
    "characters": []
}

for i, layer in enumerate(psd):
    print(f"Processing layer {i}: '{layer.name}'...")
    if layer.name not in MAPPING:
        print(f"Warning: layer '{layer.name}' not recognized in mapping, skipping.")
        continue

    meta = MAPPING[layer.name]
    char_id = meta["id"]
    out_png = os.path.join(EXPORT_DIR, f"{char_id}.png")

    img = layer.composite()
    if img is None:
        print(f"Error compositing {layer.name}")
        continue

    img.save(out_png, "PNG")
    print(f"Saved {out_png} ({img.size[0]}x{img.size[1]}) at offset {layer.offset}")

    # Estimate head center: top 30% of character, weighted by alpha
    img_np = np.array(img)
    alpha = img_np[:, :, 3] if img_np.shape[2] == 4 else np.ones((img.size[1], img.size[0]), dtype=np.uint8) * 255
    h_top = max(1, int(img.size[1] * 0.35))
    alpha_top = alpha[:h_top, :]
    total_a = np.sum(alpha_top)
    if total_a > 0:
        y_indices, x_indices = np.indices(alpha_top.shape)
        head_rel_x = float(np.sum(x_indices * alpha_top) / total_a)
        head_rel_y = float(np.sum(y_indices * alpha_top) / total_a)
    else:
        head_rel_x = img.size[0] / 2.0
        head_rel_y = img.size[1] * 0.20

    head_x = layer.offset[0] + head_rel_x
    head_y = layer.offset[1] + head_rel_y

    char_entry = {
        "id": char_id,
        "name": meta["name"],
        "franchise": meta["franchise"],
        "title": meta["title"],
        "color": meta["color"],
        "row": meta["row"],
        "file": f"{char_id}.png",
        "x": layer.offset[0],
        "y": layer.offset[1],
        "width": img.size[0],
        "height": img.size[1],
        "center_x": layer.offset[0] + img.size[0] / 2.0,
        "head_x": round(head_x, 1),
        "head_y": round(head_y, 1)
    }
    lineup_data["characters"].append(char_entry)

# Write lineup_cartoon.json
json_path = os.path.join(EXPORT_DIR, "lineup_cartoon.json")
with open(json_path, "w", encoding="utf-8") as f:
    json.dump(lineup_data, f, indent=2)
print(f"Saved {json_path}")
