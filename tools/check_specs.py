#!/usr/bin/env python3
"""Consistency check: every phase folder has the three files; a phase marked done has every box ticked."""
import pathlib, re, sys

root = pathlib.Path(__file__).resolve().parent.parent
specs = root / "specs"
errors = []
roadmap = (specs / "roadmap.md").read_text(encoding="utf-8")
sections = re.split(r"^## ", roadmap, flags=re.M)[1:]
done = [s.splitlines()[0] for s in sections if re.search(r"^Status: done \d{4}-\d{2}-\d{2}$", s, flags=re.M)]
phases = sorted(p for p in specs.iterdir() if p.is_dir())
for p in phases:
    for f in ("requirements.md", "plan.md", "validation.md"):
        if not (p / f).is_file():
            errors.append(f"{p.name}: missing {f}")
for i, title in enumerate(done):
    m = re.match(r"Phase (\d+)", title)
    if not m:
        continue
    n = m.group(1)
    folder = [p for p in phases if re.match(rf"\d{{4}}-\d{{2}}-\d{{2}}-p{n}-", p.name)]
    if not folder:
        errors.append(f"{title}: marked done but no folder p{n}-*")
        continue
    val = (folder[0] / "validation.md").read_text(encoding="utf-8")
    open_boxes = re.findall(r"^- \[ \] .*", val, flags=re.M)
    manual = [b for b in open_boxes if "(manual)" in b]
    if len(open_boxes) != len(manual):
        errors.append(f"{title}: {len(open_boxes) - len(manual)} unticked box(es) in {folder[0].name}/validation.md")
for e in errors:
    print("ERROR", e)
print(f"{len(phases)} phase folder(s), {len(done)} done, {len(errors)} error(s)")
sys.exit(1 if errors else 0)
