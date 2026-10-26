import os
import sys
import json
import math
from psd_tools import PSDImage
from PIL import Image, ImageOps, ImageEnhance, ImageDraw

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PSD_PATH = os.path.join(REPO_ROOT, "tv-assets", "my-favourites.psd")
EXPORT_DIR = os.path.join(REPO_ROOT, "tv-assets", "export")
SCENES_IN_DIR = os.path.join(REPO_ROOT, "tv-assets", "scenes")
SCENES_OUT_DIR = os.path.join(EXPORT_DIR, "scenes")

os.makedirs(EXPORT_DIR, exist_ok=True)
os.makedirs(SCENES_OUT_DIR, exist_ok=True)

MAPPING = {
    "breaking-bad": {
        "id": "walter-white",
        "name": "Walter White",
        "franchise": "Breaking Bad",
        "color": "#3E8E41",
        "row": "back"
    },
    "Pirates-of-the-caribbean": {
        "id": "jack-sparrow",
        "name": "Jack Sparrow",
        "franchise": "Pirates of the Caribbean",
        "color": "#C9A227",
        "row": "back"
    },
    "better-call-saul": {
        "id": "saul-goodman",
        "name": "Saul Goodman",
        "franchise": "Better Call Saul",
        "color": "#1FB5C9",
        "row": "front"
    },
    "spiderman": {
        "id": "spider-man",
        "name": "Spider-Man",
        "franchise": "Spider-Man",
        "color": "#E5484D",
        "row": "back"
    },
    "john-wick": {
        "id": "john-wick",
        "name": "John Wick",
        "franchise": "John Wick",
        "color": "#D62839",
        "row": "front"
    },
    "Batman": {
        "id": "batman",
        "name": "Batman",
        "franchise": "Batman",
        "color": "#8A8F98",
        "row": "front"
    },
    "iron-man": {
        "id": "tony-stark",
        "name": "Tony Stark",
        "franchise": "Iron Man",
        "color": "#E5A100",
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

    # Composite layer with transparency
    img = layer.composite()
    if img is None:
        print(f"Error compositing {layer.name}")
        continue

    img.save(out_png, "PNG")
    print(f"Saved {out_png} ({img.size[0]}x{img.size[1]}) at offset {layer.offset}")

    char_entry = {
        "id": char_id,
        "name": meta["name"],
        "franchise": meta["franchise"],
        "color": meta["color"],
        "row": meta["row"],
        "file": f"{char_id}.png",
        "scene_file": f"{char_id}.jpg",
        "x": layer.offset[0],
        "y": layer.offset[1],
        "width": img.size[0],
        "height": img.size[1],
        "center_x": layer.offset[0] + img.size[0] / 2.0
    }
    lineup_data["characters"].append(char_entry)

# Write lineup.json
json_path = os.path.join(EXPORT_DIR, "lineup.json")
with open(json_path, "w", encoding="utf-8") as f:
    json.dump(lineup_data, f, indent=2)
print(f"Saved {json_path}")

# Process scenes
print("\nProcessing scene images (greyscale, darkened, vignetted)...")
scene_map = {
    "batman": "batman.jpg",
    "jack-sparrow": "jack-sparrow.jpg",
    "john-wick": "john-wick.jpg",
    "saul-goodman": "saul-goodman.png",
    "spider-man": "spider-man.jpg",
    "tony-stark": "tony-stark.jpg",
    "walter-white": "walter-white.jpg"
}

def create_vignette(width, height):
    # Radial vignette mask
    mask = Image.new("L", (width, height), 0)
    center_x, center_y = width / 2.0, height / 2.0
    max_radius = math.hypot(center_x, center_y)
    
    # Fast vignette drawing
    draw = ImageDraw.Draw(mask)
    steps = 40
    for s in range(steps):
        r = max_radius * (1.0 - (s / float(steps)))
        # Intensity: center stays bright (255), edges darken towards 40
        intensity = int(40 + (255 - 40) * (1.0 - (r / max_radius) ** 1.8))
        draw.ellipse([center_x - r, center_y - r, center_x + r, center_y + r], fill=intensity)
    return mask

vignette_mask = create_vignette(1920, 1080)

for char_id, scene_filename in scene_map.items():
    src_path = os.path.join(SCENES_IN_DIR, scene_filename)
    dst_path = os.path.join(SCENES_OUT_DIR, f"{char_id}.jpg")
    if not os.path.exists(src_path):
        print(f"Warning: Scene file {src_path} not found!")
        continue

    with Image.open(src_path) as im:
        # Convert to RGB, crop/scale to exactly 1920x1080
        im = ImageOps.fit(im.convert("RGB"), (1920, 1080), method=Image.Resampling.LANCZOS)
        
        # 1. Mute color saturation so the scene retains a subtle amount of its own color (~38% saturation)
        color_enhancer = ImageEnhance.Color(im)
        im_colored = color_enhancer.enhance(0.38)
        
        # 2. Darken (reduce brightness to ~0.40 so it stays ambient behind the characters)
        darkener = ImageEnhance.Brightness(im_colored)
        im_dark = darkener.enhance(0.40)
        
        # 3. Apply subtle contrast boost for crispness
        contrast = ImageEnhance.Contrast(im_dark)
        im_contrasted = contrast.enhance(1.15)
        
        # 4. Apply vignette
        black = Image.new("RGB", (1920, 1080), (0, 0, 0))
        final_scene = Image.composite(im_contrasted, black, vignette_mask)
        
        final_scene.save(dst_path, "JPEG", quality=85)
        print(f"Processed scene -> {dst_path}")

print("\nLayer export and scene processing complete!")
