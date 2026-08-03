#!/usr/bin/env python3
"""Convert the Android client's flag vector drawables into SVG files.

The site is supposed to look like the Android app, and the app already ships a
complete, visually consistent flag set. Re-drawing or sourcing them elsewhere
would drift from the app, so they are converted instead of replaced.

The drawables use a deliberately small subset of the vector format: a <vector>
root with a viewport, <path> elements carrying pathData and fillColor, and a
handful of <group>/<clip-path> pairs. Anything outside that subset is reported
instead of being silently dropped, because a partially converted flag looks
plausible while being wrong.

Usage:
    python scripts/convert_flags.py <android-res-drawable-dir> <output-dir>
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID_NS = "http://schemas.android.com/apk/res/android"
A = f"{{{ANDROID_NS}}}"

# Only two-letter country codes are useful to the site: the server list keys off
# ISO country codes. "flag_large_*" are higher-detail duplicates and "flag_fastest"
# is handled separately because it is not a country.
COUNTRY_PATTERN = re.compile(r"^flag_([a-z]{2})\.xml$")
SPECIAL = {"flag_fastest.xml": "fastest"}


class UnsupportedDrawable(Exception):
    pass


def dimension(value: str | None, fallback: float) -> float:
    """Reads a vector dimension, which may be a bare number or a dp value."""
    if not value:
        return fallback
    return float(value.removesuffix("dp").removesuffix("px"))


def convert_path(node: ET.Element) -> str:
    data = node.get(f"{A}pathData")
    if not data:
        raise UnsupportedDrawable("a <path> without pathData")

    # Vector pathData and SVG path data are the same grammar, so the geometry is
    # copied verbatim; only the presentation attributes differ.
    attributes = [f'd="{escape(collapse(data))}"']

    fill = node.get(f"{A}fillColor")
    # A vector path with no fill renders as nothing, and SVG would default it to
    # black instead, so the difference has to be spelled out.
    attributes.append(f'fill="{fill}"' if fill else 'fill="none"')

    alpha = node.get(f"{A}fillAlpha")
    if alpha:
        attributes.append(f'fill-opacity="{alpha}"')

    stroke = node.get(f"{A}strokeColor")
    if stroke:
        attributes.append(f'stroke="{stroke}"')
        width = node.get(f"{A}strokeWidth")
        if width:
            attributes.append(f'stroke-width="{width}"')

    rule = node.get(f"{A}fillType")
    if rule:
        attributes.append(f'fill-rule="{rule.lower()}"')

    return "<path " + " ".join(attributes) + "/>"


def collapse(path_data: str) -> str:
    return " ".join(path_data.split())


def escape(value: str) -> str:
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")


def convert_group(node: ET.Element, clip_id: str) -> tuple[list[str], list[str]]:
    """Returns (defs, body) for a <group>, resolving its <clip-path> if present."""
    defs: list[str] = []
    body: list[str] = []

    for attribute in node.attrib:
        # Transforms would silently misplace the artwork if ignored. None of the
        # bundled flags use them, so this is a guard rather than a limitation.
        if attribute.startswith(f"{A}") and attribute != f"{A}name":
            raise UnsupportedDrawable(f"a <group> attribute {attribute}")

    clip = node.find("clip-path")
    attributes = ""
    if clip is not None:
        data = clip.get(f"{A}pathData")
        if not data:
            raise UnsupportedDrawable("a <clip-path> without pathData")
        defs.append(f'<clipPath id="{clip_id}"><path d="{escape(collapse(data))}"/></clipPath>')
        attributes = f' clip-path="url(#{clip_id})"'

    body.append(f"<g{attributes}>")
    for child in node:
        tag = child.tag
        if tag == "path":
            body.append(convert_path(child))
        elif tag == "clip-path":
            continue
        else:
            raise UnsupportedDrawable(f"a <{tag}> inside a <group>")
    body.append("</g>")

    return defs, body


def convert(source: Path) -> str:
    root = ET.parse(source).getroot()
    if root.tag != "vector":
        raise UnsupportedDrawable(f"a <{root.tag}> root")

    view_width = dimension(root.get(f"{A}viewportWidth"), dimension(root.get(f"{A}width"), 32))
    view_height = dimension(root.get(f"{A}viewportHeight"), dimension(root.get(f"{A}height"), 32))

    defs: list[str] = []
    body: list[str] = []
    clip_index = 0

    for child in root:
        if child.tag == "path":
            body.append(convert_path(child))
        elif child.tag == "group":
            clip_index += 1
            group_defs, group_body = convert_group(child, f"clip{clip_index}")
            defs.extend(group_defs)
            body.extend(group_body)
        else:
            raise UnsupportedDrawable(f"a <{child.tag}> element")

    lines = [
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {trim(view_width)} {trim(view_height)}">',
    ]
    if defs:
        lines.append("<defs>" + "".join(defs) + "</defs>")
    lines.extend(body)
    lines.append("</svg>")
    return "".join(lines) + "\n"


def trim(value: float) -> str:
    return str(int(value)) if value == int(value) else str(value)


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 2

    source_dir = Path(sys.argv[1])
    output_dir = Path(sys.argv[2])
    output_dir.mkdir(parents=True, exist_ok=True)

    written = 0
    skipped: list[str] = []

    for source in sorted(source_dir.glob("flag_*.xml")):
        match = COUNTRY_PATTERN.match(source.name)
        if match:
            name = match.group(1)
        elif source.name in SPECIAL:
            name = SPECIAL[source.name]
        else:
            continue

        try:
            svg = convert(source)
        except (UnsupportedDrawable, ET.ParseError) as error:
            skipped.append(f"{source.name}: {error}")
            continue

        (output_dir / f"{name}.svg").write_text(svg, encoding="utf-8")
        written += 1

    print(f"converted {written} flags into {output_dir}")
    for entry in skipped:
        print(f"  skipped {entry}")
    return 1 if skipped else 0


if __name__ == "__main__":
    raise SystemExit(main())
