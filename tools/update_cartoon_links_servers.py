import os
import re
import json
import shutil

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TXT_PATH = os.path.join(REPO_ROOT, "tv-assets", "links", "my-fav-cartoons-all-links-for-shows-and-movies.txt")
EXPORT_JSON_PATH = os.path.join(REPO_ROOT, "tv-assets", "export", "cartoon", "cartoon_links.json")
ASSETS_JSON_PATH = os.path.join(REPO_ROOT, "launcher", "app", "src", "main", "assets", "cartoon_links.json")

with open(TXT_PATH, "r", encoding="utf-8") as f:
    text = f.read()

parts = text.split("server 2 links below:")
s1_text = parts[0]
s2_text = parts[1] if len(parts) > 1 else ""

def parse_section(sec_text):
    data = {
        "ash-ketchem": {"title": "Pokémon", "tabs": {"Series": [], "Movies": []}},
        "ben-10-classic": {"title": "Ben 10", "tabs": {"Classic Series": [], "Movies": []}},
        "ben-10-alienforce": {"title": "Ben 10: Alien Force", "tabs": {"Alien Force": [], "Ultimate Alien": []}},
        "sho-yamato": {"title": "Idaten Jump", "single_url": None},
        "jinga-hagane": {"title": "Beyblade: Metal Fusion", "tabs": {"Metal Saga": []}},
        "courage": {"title": "Courage the Cowardly Dog", "tabs": {"Series": []}},
        "kick-buttowski": {"title": "Kick Buttowski", "tabs": {"Series": []}},
        "ninja-hattori": {"title": "Ninja Hattori", "tabs": {"Classic Series": [], "Returns": [], "Movies": []}},
        "tom-and-jerry": {"title": "Tom & Jerry", "tabs": {"The Tom & Jerry Show": [], "Tales": [], "Classic (1940-1980)": [], "Movies": []}},
        "doremon": {"title": "Doraemon", "tabs": {"Series": [], "Movies": []}},
        "shin-chan": {"title": "Shin-chan", "tabs": {"Series": [], "Uncensored": [], "Movies": []}},
        "oggy-cockroaches": {"title": "Oggy and the Cockroaches", "tabs": {"Series": [], "Movies": []}},
        "roll-no-21": {"title": "Roll No 21", "tabs": {"Series": [], "Movies": []}},
        "oswald": {"title": "Oswald", "single_url": None}
    }

    cur_char = None
    cur_tab = None

    for line in sec_text.splitlines():
        line = line.strip()
        if not line: continue

        # Header detection
        if re.search(r'1\.\s*pokemon-all-movies:', line, re.I):
            cur_char = 'ash-ketchem'; cur_tab = 'Movies'; continue
        elif re.search(r'1\.\s*pokemon:', line, re.I):
            cur_char = 'ash-ketchem'; cur_tab = 'Series'; continue
        elif re.search(r'2\.\s*ben-10-all-movies:', line, re.I):
            cur_char = 'ben-10-classic'; cur_tab = 'Movies'; continue
        elif re.search(r'2\.\s*ben-10-classic:', line, re.I):
            cur_char = 'ben-10-classic'; cur_tab = 'Classic Series'; continue
        elif re.search(r'3\.\s*ben-10-ultimate-alien:', line, re.I):
            cur_char = 'ben-10-alienforce'; cur_tab = 'Ultimate Alien'; continue
        elif re.search(r'3\.\s*ben-10-alien-force:', line, re.I):
            cur_char = 'ben-10-alienforce'; cur_tab = 'Alien Force'; continue
        elif re.search(r'4\.\s*idaten\s*jump:', line, re.I):
            cur_char = 'sho-yamato'; cur_tab = None
            m = re.search(r'https?://\S+', line)
            if m: data[cur_char]['single_url'] = m.group(0).strip()
            continue
        elif re.search(r'5\.\s*beyblade-metal', line, re.I):
            cur_char = 'jinga-hagane'; cur_tab = 'Metal Saga'; continue
        elif re.search(r'6\.\s*courage:', line, re.I):
            cur_char = 'courage'; cur_tab = 'Series'; continue
        elif re.search(r'7\.\s*kick-buttowski:', line, re.I):
            cur_char = 'kick-buttowski'; cur_tab = 'Series'; continue
        elif re.search(r'8\.\s*ninja-hattori-movie:', line, re.I):
            cur_char = 'ninja-hattori'; cur_tab = 'Movies'; continue
        elif re.search(r'8\.\s*ninja-hattori-returns:', line, re.I):
            cur_char = 'ninja-hattori'; cur_tab = 'Returns'; continue
        elif re.search(r'8\.\s*ninja-hattori:', line, re.I):
            cur_char = 'ninja-hattori'; cur_tab = 'Classic Series'; continue
        elif re.search(r'9\.\s*the-tom-and-jerry-show:', line, re.I):
            cur_char = 'tom-and-jerry'; cur_tab = 'The Tom & Jerry Show'; continue
        elif re.search(r'9\.\s*tom-and-jerry-tales:', line, re.I):
            cur_char = 'tom-and-jerry'; cur_tab = 'Tales'; continue
        elif re.search(r'9\.\s*tom-and-jerry-kids-show', line, re.I):
            cur_char = 'tom-and-jerry'; cur_tab = 'Classic (1940-1980)'; continue
        elif re.search(r'9\.\s*tom-and-jerry-all-movies:', line, re.I):
            cur_char = 'tom-and-jerry'; cur_tab = 'Movies'
            m = re.search(r'https?://\S+', line)
            if m: data[cur_char]['tabs']['Movies'].append(('All Movies', m.group(0).strip()))
            continue
        elif re.search(r'10\.\s*doraemon', line, re.I):
            cur_char = 'doremon'; cur_tab = 'Series'; continue
        elif re.search(r'11\.\s*shin-chan\(uncensored\):', line, re.I):
            cur_char = 'shin-chan'; cur_tab = 'Uncensored'; continue
        elif re.search(r'11\.\s*shin-chan-movies:', line, re.I):
            cur_char = 'shin-chan'; cur_tab = 'Movies'; continue
        elif re.search(r'11\.\s*shin-chan:', line, re.I):
            cur_char = 'shin-chan'; cur_tab = 'Series'; continue
        elif re.search(r'12\.\s*oogy-and-the-cockroaches.*movie', line, re.I):
            cur_char = 'oggy-cockroaches'; cur_tab = 'Movies'
            m = re.search(r'https?://\S+', line)
            if m: data[cur_char]['tabs']['Movies'].append(('The Movie', m.group(0).strip()))
            continue
        elif re.search(r'12\.\s*oggy-and-the-cockroaches:', line, re.I):
            cur_char = 'oggy-cockroaches'; cur_tab = 'Series'; continue
        elif re.search(r'13\.\s*roll-no-21:', line, re.I):
            cur_char = 'roll-no-21'; cur_tab = 'Series'; continue
        elif re.search(r'14\.\s*oswald:', line, re.I):
            cur_char = 'oswald'; cur_tab = None
            m = re.search(r'https?://\S+', line)
            if m: data[cur_char]['single_url'] = m.group(0).strip()
            continue

        # Item line
        m = re.search(r'^(.*?):\s*(https?://\S+)$', line)
        m_empty = re.match(r'^(.*?):\s*$', line) if not m else None
        if (m or m_empty) and cur_char:
            key_raw = m.group(1).strip() if m else m_empty.group(1).strip()
            url = m.group(2).strip() if m else None
            
            if cur_char == 'sho-yamato':
                if url: data[cur_char]['single_url'] = url
                continue

            target_tab = cur_tab
            if cur_char == 'roll-no-21' and ('movie' in key_raw.lower() or (url and 'search?q=' in url)):
                target_tab = 'Movies'
            elif cur_char == 'doremon' and ('movie' in key_raw.lower() or (url and 'rareanimes' in url)):
                target_tab = 'Movies'
            elif cur_char == 'shin-chan' and ('movie' in key_raw.lower() or (url and 'rareanimes' in url)):
                target_tab = 'Movies'

            s_match = re.search(r'season=season-(\d+)', url or '', re.I)
            if s_match:
                name = f'Season {s_match.group(1)}'
            elif re.match(r'^ss(\d+)$', key_raw, re.I):
                num = re.match(r'^ss(\d+)$', key_raw, re.I).group(1)
                name = f'Season {num}'
            elif 'movie' in key_raw.lower() or 'all' in key_raw.lower():
                name = 'All Movies' if 'all' in key_raw.lower() else key_raw
            else:
                name = key_raw

            if target_tab and target_tab in data[cur_char]['tabs']:
                if url:
                    data[cur_char]['tabs'][target_tab].append((name, url))

    return data

kartoons_data = parse_section(s1_text)
anidisk_data = parse_section(s2_text)

merged = {}

def sort_key(name):
    m = re.search(r'Season\s*(\d+)', name, re.I)
    if m:
        return (0, int(m.group(1)))
    return (1, name)

for cid, cinfo in kartoons_data.items():
    title = cinfo['title']
    if 'single_url' in cinfo:
        # SWAP: Server 1 = Anidisk, Server 2 = Kartoons
        s1_single = anidisk_data[cid].get('single_url')
        s2_single = cinfo['single_url']
        merged[cid] = {
            "id": cid,
            "title": title,
            "single_url": s1_single,
            "single_url2": s2_single
        }
    else:
        merged[cid] = {
            "id": cid,
            "title": title,
            "tabs": []
        }
        for tab_title, k_items in cinfo['tabs'].items():
            a_items = anidisk_data[cid]['tabs'].get(tab_title, [])
            k_dict = {name: url for name, url in k_items}
            a_dict = {name: url for name, url in a_items}

            # Ordered list of all item names sorted by season number
            ordered_names = list(dict.fromkeys([name for name, _ in a_items] + [name for name, _ in k_items]))
            ordered_names.sort(key=sort_key)

            items_list = []
            for name in ordered_names:
                # SWAP: Server 1 = Anidisk, Server 2 = Kartoons
                # STRICT: NO FALLBACK!
                u1 = a_dict.get(name)  # Server 1 link (Anidisk)
                u2 = k_dict.get(name)  # Server 2 link (Kartoons)
                if u1 or u2:
                    items_list.append({
                        "name": name,
                        "url": u1,
                        "url2": u2
                    })

            if items_list:
                merged[cid]["tabs"].append({
                    "title": tab_title,
                    "items": items_list
                })

# Write export JSON
os.makedirs(os.path.dirname(EXPORT_JSON_PATH), exist_ok=True)
with open(EXPORT_JSON_PATH, "w", encoding="utf-8") as f:
    json.dump(merged, f, indent=2)

# Copy to launcher assets
os.makedirs(os.path.dirname(ASSETS_JSON_PATH), exist_ok=True)
with open(ASSETS_JSON_PATH, "w", encoding="utf-8") as f:
    json.dump(merged, f, indent=2)

print("Generated dual-server cartoon_links.json successfully!")
for cid, info in merged.items():
    if "single_url" in info:
        print(f"  {cid:<20} -> Single URL: S1={'YES' if info['single_url'] else 'NO'} | S2={'YES' if info['single_url2'] else 'NO'}")
    else:
        tab_summaries = []
        for t in info.get("tabs", []):
            s1_cnt = sum(1 for it in t["items"] if it.get("url"))
            s2_cnt = sum(1 for it in t["items"] if it.get("url2"))
            tab_summaries.append(f"{t['title']} ({len(t['items'])} items; S1={s1_cnt}, S2={s2_cnt})")
        print(f"  {cid:<20} -> Tabs: {', '.join(tab_summaries)}")
