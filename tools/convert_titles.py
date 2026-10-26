import csv
import json
import os

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CSV_PATH = os.path.join(REPO_ROOT, "tv-assets", "titles.csv")
OUT_JSON = os.path.join(REPO_ROOT, "tv-assets", "export", "titles.json")

def convert():
    if not os.path.exists(CSV_PATH):
        print(f"Error: {CSV_PATH} not found!")
        return

    by_character = {}
    with open(CSV_PATH, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            char_id = row.get("character", "").strip()
            if not char_id:
                continue
            entry = {
                "character": char_id,
                "group": row.get("group", "").strip(),
                "title": row.get("title", "").strip(),
                "year_or_season": row.get("year_or_season", "").strip(),
                "app_package": row.get("app_package", "").strip(),
                "link": row.get("link", "").strip(),
                "poster_file": row.get("poster_file", "").strip()
            }
            by_character.setdefault(char_id, []).append(entry)

    os.makedirs(os.path.dirname(OUT_JSON), exist_ok=True)
    with open(OUT_JSON, "w", encoding="utf-8") as f:
        json.dump(by_character, f, indent=2)

    print(f"Successfully converted {CSV_PATH} -> {OUT_JSON} ({len(by_character)} characters)")

if __name__ == "__main__":
    convert()
