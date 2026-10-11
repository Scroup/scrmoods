"""
Convert an exported scrmoods XML file into a CSV you can open in Excel / Google Sheets,
and print a short summary of every tracking point.

Usage:
    python scrmoods_to_csv.py scrmoods-2026-10-11.xml            -> writes scrmoods-2026-10-11.csv
    python scrmoods_to_csv.py scrmoods.xml --text                -> uses readable text ("Positive") instead of numbers

One row per day, one column per tracking point. Numbers: scale = option position (0 = first option),
Yes/No = 1/0, number points = the number itself.
"""
import csv
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    use_text = "--text" in sys.argv
    if not args:
        print(__doc__)
        sys.exit(1)
    src = Path(args[0])
    root = ET.parse(src).getroot()

    points = [p.attrib | {"options": [o.text or "" for o in p.findall("option")]}
              for p in root.find("points")]
    ids = [p["id"] for p in points]
    labels = {p["id"]: p["label"] for p in points}

    rows = []
    for e in root.find("entries"):
        row = {"date": e.get("date")}
        for v in e.findall("value"):
            row[v.get("point")] = (v.get("text") or v.text or "") if use_text else (v.text or "")
        row["note"] = (e.findtext("note") or "").replace("\n", " ")
        row["timed_notes"] = " | ".join(f'{t.get("time")}: {t.text or ""}' for t in e.findall("timednote"))
        rows.append(row)

    out = src.with_suffix(".csv")
    with open(out, "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(["date"] + [labels[i] for i in ids] + ["note", "timed notes"])
        for r in rows:
            w.writerow([r["date"]] + [r.get(i, "") for i in ids] + [r["note"], r["timed_notes"]])
    print(f"Wrote {out} ({len(rows)} days)\n")

    for p in points:
        if p["type"] == "text":
            continue
        vals = []
        for e in root.find("entries"):
            for v in e.findall("value"):
                if v.get("point") == p["id"]:
                    try:
                        vals.append(float(v.text))
                    except (TypeError, ValueError):
                        pass
        if not vals:
            continue
        avg = sum(vals) / len(vals)
        if p["type"] == "yesno":
            s = f"yes on {avg * 100:.0f}% of {len(vals)} days"
        elif p["type"] == "scale":
            opts = p["options"]
            near = opts[round(avg)] if opts and 0 <= round(avg) < len(opts) else ""
            s = f"average {avg:.2f} ({near}) over {len(vals)} days"
        else:
            s = f"average {avg:.1f} {p.get('unit', '')}, min {min(vals):g}, max {max(vals):g}, {len(vals)} days"
        removed = " [removed]" if p.get("archived") == "true" else ""
        print(f"- {p['label']}{removed}: {s}")


if __name__ == "__main__":
    main()
