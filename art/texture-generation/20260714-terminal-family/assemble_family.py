from pathlib import Path
import hashlib
import json

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parent
PROJECT = ROOT.parents[2]
PALETTE_HEX = [
    "#0E1016",
    "#161922",
    "#20242F",
    "#2A303D",
    "#353D4C",
    "#465164",
    "#5B687D",
    "#AAB6CC",
    "#3FDCE5",
    "#2EA8FF",
    "#9A5CE8",
    "#C083FF",
    "#FF8A24",
    "#FFC060",
    "#B46A3C",
    "#E4E8F2",
    "#4BC6A8",
    "#5B5264",
]
PALETTE = [tuple(bytes.fromhex(color[1:])) for color in PALETTE_HEX]
VOID, SHADOW, DARK, MID, CASING, EDGE, HIGHLIGHT, LIGHT = PALETTE[:8]
CYAN, BLUE, PURPLE, LIGHT_PURPLE, ORANGE, LIGHT_ORANGE = PALETTE[8:14]
COPPER, QUARTZ, PRISMARINE, NETHERITE = PALETTE[14:]
CHASSIS_POINTS = sorted({
    (x, y)
    for y in range(16)
    for x in range(16)
    if x in (0, 1, 14, 15) or y in (0, 1, 14, 15)
} | {(2, 2), (13, 2), (2, 13), (13, 13)})
TIER_ORNAMENTS = [
    {
        "role": "copper_bound_cell",
        "accents": ["#B46A3C"],
        "points": [(x, y, COPPER) for x, y in (
            (6, 4), (9, 4), (6, 5), (9, 5),
            (6, 10), (9, 10), (6, 11), (9, 11),
        )],
    },
    {
        "role": "iron_braced_double_node",
        "accents": ["#AAB6CC"],
        "points": [(x, y, LIGHT) for x, y in (
            (4, 4), (11, 4), (4, 6), (11, 6),
            (4, 9), (11, 9), (4, 11), (11, 11),
            (6, 7), (9, 7), (6, 8), (9, 8),
        )],
    },
    {
        "role": "gold_lapis_lattice",
        "accents": ["#FFC060", "#2EA8FF"],
        "points": [
            *[(x, y, LIGHT_ORANGE) for x, y in (
                (4, 4), (11, 4), (5, 5), (10, 5),
                (5, 10), (10, 10), (4, 11), (11, 11),
            )],
            *[(x, y, BLUE) for x, y in (
                (6, 4), (9, 4), (4, 7), (11, 7),
                (4, 8), (11, 8), (6, 11), (9, 11),
            )],
        ],
    },
    {
        "role": "diamond_quartz_cross_frame",
        "accents": ["#E4E8F2", "#3FDCE5"],
        "points": [
            *[(x, y, QUARTZ) for x, y in (
                (5, 4), (6, 4), (9, 4), (10, 4),
                (4, 5), (11, 5), (4, 6), (11, 6),
                (4, 9), (11, 9), (4, 10), (11, 10),
                (5, 11), (6, 11), (9, 11), (10, 11),
            )],
            *[(x, y, CYAN) for x, y in (
                (7, 6), (8, 6), (7, 9), (8, 9),
            )],
        ],
    },
    {
        "role": "prismarine_ender_halo",
        "accents": ["#4BC6A8", "#9A5CE8"],
        "points": [
            *[(x, y, PRISMARINE) for x, y in (
                (6, 4), (7, 4), (8, 4), (9, 4),
                (4, 6), (11, 6), (4, 7), (11, 7),
                (4, 8), (11, 8), (4, 9), (11, 9),
                (6, 11), (7, 11), (8, 11), (9, 11),
            )],
            *[(x, y, PURPLE) for x, y in (
                (5, 5), (10, 5), (6, 6), (9, 6),
                (6, 9), (9, 9), (5, 10), (10, 10),
            )],
        ],
    },
    {
        "role": "netherite_amethyst_crown_circuit",
        "accents": ["#5B5264", "#C083FF"],
        "points": [
            *[(x, y, NETHERITE) for x, y in (
                (6, 3), (7, 3), (8, 3), (9, 3),
                (5, 4), (10, 4), (4, 5), (11, 5),
                (3, 7), (12, 7), (3, 8), (12, 8),
                (5, 11), (10, 11),
            )],
            *[(x, y, LIGHT_PURPLE) for x, y in (
                (6, 4), (9, 4), (6, 5), (9, 5),
                (5, 6), (10, 6), (6, 7), (9, 7),
                (6, 8), (9, 8), (5, 9), (10, 9),
                (7, 10), (8, 10),
            )],
        ],
    },
]
CREATIVE_ORNAMENT = {
    "role": "creative_infinity_cell",
    "accents": ["#3FDCE5", "#C083FF"],
    "points": [
        *[(x, y, CYAN) for x, y in (
            (4, 5), (5, 5), (10, 5), (11, 5),
            (3, 6), (6, 6), (9, 6), (12, 6),
            (3, 9), (6, 9), (9, 9), (12, 9),
            (4, 10), (5, 10), (10, 10), (11, 10),
        )],
        *[(x, y, LIGHT_PURPLE) for x, y in (
            (4, 7), (7, 7), (8, 7), (11, 7),
            (4, 8), (7, 8), (8, 8), (11, 8),
        )],
    ],
}
SELECTED = ROOT / "selected"
METADATA = ROOT / "metadata"


def nearest(rgb):
    return min(PALETTE, key=lambda color: sum((value - target) ** 2 for value, target in zip(rgb, color)))


def quantized(relative):
    source = Image.open(ROOT / relative).convert("RGBA")
    output = Image.new("RGBA", source.size)
    for y in range(source.height):
        for x in range(source.width):
            red, green, blue, alpha = source.getpixel((x, y))
            output.putpixel((x, y), (*nearest((red, green, blue)), alpha))
    return output


def apply_chassis(image, chassis):
    for point in CHASSIS_POINTS:
        image.putpixel(point, chassis.getpixel(point))


def mirror_x(image):
    for y in range(16):
        for x in range(8):
            image.putpixel((15 - x, y), image.getpixel((x, y)))


def panel(draw):
    draw.rectangle((3, 3, 12, 12), fill=EDGE)
    draw.rectangle((4, 4, 11, 11), fill=SHADOW)


def core(image):
    draw = ImageDraw.Draw(image)
    panel(draw)
    for y, left in ((4, 7), (5, 6), (6, 5), (7, 4), (8, 4), (9, 5), (10, 6), (11, 7)):
        draw.line((left, y, 15 - left, y), fill=PURPLE)
    draw.rectangle((6, 6, 9, 9), fill=CYAN)
    draw.rectangle((6, 7, 9, 8), fill=PURPLE)
    draw.rectangle((7, 7, 8, 8), fill=LIGHT_PURPLE)


def storage_terminal(image):
    draw = ImageDraw.Draw(image)
    panel(draw)
    draw.rectangle((4, 4, 11, 11), fill=DARK)
    for left, top, color in ((4, 4, CYAN), (9, 4, BLUE), (4, 9, BLUE), (9, 9, CYAN)):
        draw.rectangle((left, top, left + 2, top + 2), fill=color)
    draw.rectangle((7, 7, 8, 8), fill=PURPLE)


def crafting_terminal(image):
    draw = ImageDraw.Draw(image)
    panel(draw)
    draw.rectangle((4, 4, 11, 11), fill=DARK)
    for row, y in enumerate((4, 7, 10)):
        for column, x in enumerate((4, 7, 10)):
            color = PURPLE if (row, column) == (1, 1) else CYAN
            draw.rectangle((x, y, x + 1, y + 1), fill=color)
    draw.rectangle((6, 7, 9, 8), fill=PURPLE)
    draw.rectangle((7, 7, 8, 8), fill=LIGHT_PURPLE)


def storage_unit(image, tier):
    draw = ImageDraw.Draw(image)
    panel(draw)
    draw.rectangle((3, 3, 12, 12), fill=EDGE)
    draw.rectangle((4, 4, 11, 11), fill=DARK)
    draw.rectangle((6, 6, 9, 9), fill=SHADOW)
    draw.rectangle((7, 7, 8, 8), fill=CASING)
    for x, y, color in TIER_ORNAMENTS[tier - 1]["points"]:
        image.putpixel((x, y), color)


def creative_storage_unit(image):
    draw = ImageDraw.Draw(image)
    panel(draw)
    draw.rectangle((3, 3, 12, 12), fill=EDGE)
    draw.rectangle((4, 4, 11, 11), fill=DARK)
    for x, y, color in CREATIVE_ORNAMENT["points"]:
        image.putpixel((x, y), color)


def connected_sheet(source):
    states = [
        (False, False, False, False, False),
        (True, True, True, True, False),
        (True, False, True, False, False),
        (False, True, False, True, False),
        (True, True, True, True, True),
    ]
    sheet = Image.new("RGBA", (80, 16), (0, 0, 0, 0))
    for index, (top, right, bottom, left, preserve_corners) in enumerate(states):
        tile = source.copy()
        draw = ImageDraw.Draw(tile)
        edge_start = 2 if preserve_corners else 0
        edge_end = 13 if preserve_corners else 15
        if top:
            draw.rectangle((edge_start, 0, edge_end, 1), fill=SHADOW)
            draw.rectangle((7, 0, 8, 1), fill=CYAN)
        if right:
            draw.rectangle((14, edge_start, 15, edge_end), fill=SHADOW)
            draw.rectangle((14, 7, 15, 8), fill=CYAN)
        if bottom:
            draw.rectangle((edge_start, 14, edge_end, 15), fill=SHADOW)
            draw.rectangle((7, 14, 8, 15), fill=CYAN)
        if left:
            draw.rectangle((0, edge_start, 1, edge_end), fill=SHADOW)
            draw.rectangle((0, 7, 1, 8), fill=CYAN)
        sheet.alpha_composite(tile, (index * 16, 0))
    return sheet


def bus_top(image, inward):
    draw = ImageDraw.Draw(image)
    panel(draw)
    color = BLUE if inward else ORANGE
    bright = CYAN if inward else LIGHT_ORANGE
    draw.rectangle((7, 7, 8, 8), fill=PURPLE)
    draw.line((7, 3, 7, 6), fill=color)
    draw.line((8, 3, 8, 6), fill=color)
    draw.line((3, 7, 6, 7), fill=color)
    draw.line((3, 8, 6, 8), fill=color)
    if inward:
        for point in ((6, 6), (9, 6), (6, 9), (9, 9)):
            draw.point(point, fill=bright)
    else:
        for point in ((3, 6), (12, 6), (3, 9), (12, 9)):
            draw.point(point, fill=bright)
    draw.rectangle((7, 7, 8, 8), fill=PURPLE)
    draw.rectangle((7, 7, 8, 8), fill=LIGHT_PURPLE)


def bus_side(image, inward):
    draw = ImageDraw.Draw(image)
    panel(draw)
    color = BLUE if inward else ORANGE
    bright = CYAN if inward else LIGHT_ORANGE
    draw.rectangle((3, 6, 12, 9), fill=DARK)
    draw.line((3, 7, 12, 7), fill=color)
    draw.line((3, 8, 12, 8), fill=color)
    draw.rectangle((7, 6, 8, 8), fill=PURPLE)
    if inward:
        draw.line((4, 6, 6, 6), fill=bright)
        draw.line((9, 6, 11, 6), fill=bright)
    else:
        draw.line((3, 9, 5, 9), fill=bright)
        draw.line((10, 9, 12, 9), fill=bright)


def directional_bus(image, inward):
    draw = ImageDraw.Draw(image)
    panel(draw)
    draw.rectangle((7, 6, 8, 9), fill=PURPLE)
    draw.rectangle((7, 7, 8, 8), fill=LIGHT_PURPLE)
    color = BLUE if inward else ORANGE
    bright = CYAN if inward else LIGHT_ORANGE
    if inward:
        points = ((3, 7), (4, 7), (5, 7), (6, 7), (5, 6), (5, 8),
                  (9, 7), (10, 7), (11, 7), (12, 7), (10, 6), (10, 8))
        tips = ((6, 7), (9, 7))
    else:
        points = ((3, 7), (4, 7), (5, 7), (6, 7), (4, 6), (4, 8),
                  (9, 7), (10, 7), (11, 7), (12, 7), (11, 6), (11, 8))
        tips = ((3, 7), (12, 7))
    for point in points:
        draw.point(point, fill=color)
    for point in tips:
        draw.point(point, fill=bright)


def remote(image):
    draw = ImageDraw.Draw(image)
    for point in ((0, 0), (15, 0), (0, 15), (15, 15)):
        image.putpixel(point, (0, 0, 0, 0))
    draw.rectangle((3, 2, 12, 10), fill=EDGE)
    draw.rectangle((4, 3, 11, 9), fill=SHADOW)
    draw.rectangle((5, 4, 10, 7), fill=DARK)
    draw.rectangle((5, 4, 7, 5), fill=CYAN)
    draw.rectangle((8, 4, 10, 5), fill=BLUE)
    draw.rectangle((5, 6, 7, 7), fill=BLUE)
    draw.rectangle((8, 6, 10, 7), fill=CYAN)
    draw.rectangle((4, 11, 6, 13), fill=PURPLE)
    draw.point((5, 11), fill=LIGHT_PURPLE)
    draw.line((8, 12, 11, 12), fill=CYAN)


def normalize_metadata(group, candidate, metadata_name=None):
    source = ROOT / f"candidates/{group}/{candidate}.json"
    original = json.loads(source.read_text())
    output = {
        "created_at_utc": original["created_at_utc"],
        "type": original["type"],
        "subject": original["subject"],
        "model": original["model"],
        "style": original["style"],
        "tileable": original["tileable"],
        "prompt": original["prompt"],
        "size": original["size"],
        "seed": original["seed"],
        "reference_image": "selected/chassis.png",
        "img2img": original["img2img"],
        "input": original["input"],
        "prediction": f"candidates/{group}/{candidate}.raw.png",
        "selected_candidate": f"candidates/{group}/{candidate}.png",
        "postprocess": "nearest shared palette, shared chassis mask, semantic pixel cleanup",
    }
    path = METADATA / f"{metadata_name or group}.json"
    path.write_text(json.dumps(output, indent=2) + "\n")
    return path.relative_to(ROOT).as_posix()


def normalize_candidate_metadata():
    def normalized(value):
        if isinstance(value, dict):
            return {key: normalized(nested) for key, nested in value.items()}
        if isinstance(value, list):
            return [normalized(nested) for nested in value]
        if isinstance(value, str) and value.startswith("/"):
            path = Path(value).resolve()
            try:
                return path.relative_to(ROOT).as_posix()
            except ValueError as error:
                raise ValueError(f"candidate metadata path escapes family root: {path}") from error
        return value

    for path in sorted((ROOT / "candidates").rglob("*.json")):
        path.write_text(json.dumps(normalized(json.loads(path.read_text())), indent=2) + "\n")


def control_atlas():
    image = Image.new("RGBA", (256, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    white = (255, 255, 255, 255)

    def points(index, values):
        left = index * 16
        for x, y in values:
            draw.point((left + x, y), fill=white)

    ascending = {(3, y) for y in range(3, 13)} | {(2, 4), (4, 4)}
    ascending |= {(x, 4) for x in range(7, 13)} | {(x, 8) for x in range(7, 12)}
    ascending |= {(x, 12) for x in range(7, 10)}
    points(0, ascending)
    descending = {(3, y) for y in range(3, 13)} | {(2, 11), (4, 11)}
    descending |= {(x, 4) for x in range(7, 10)} | {(x, 8) for x in range(7, 12)}
    descending |= {(x, 12) for x in range(7, 13)}
    points(1, descending)
    name = {(4, y) for y in range(5, 13)} | {(11, y) for y in range(5, 13)}
    name |= {(x, 4) for x in range(5, 11)} | {(x, 8) for x in range(4, 12)}
    points(2, name)
    quantity = {(x, y) for x in (3, 4) for y in range(10, 13)}
    quantity |= {(x, y) for x in (7, 8) for y in range(7, 13)}
    quantity |= {(x, y) for x in (11, 12) for y in range(4, 13)}
    points(3, quantity)
    mod = {(x, y) for left, top in ((3, 3), (9, 3), (3, 9), (9, 9))
           for x in range(left, left + 3) for y in range(top, top + 3)}
    points(4, mod)
    tag = {(x, y) for x in (5, 10) for y in range(3, 13)}
    tag |= {(x, y) for y in (6, 10) for x in range(3, 13)}
    points(5, tag)
    search = {(4, 3), (5, 2), (6, 2), (7, 2), (8, 3), (3, 4), (3, 5),
              (3, 6), (4, 7), (5, 8), (6, 8), (7, 8), (8, 7), (9, 6),
              (9, 5), (9, 4), (9, 7), (10, 8), (11, 9), (12, 10), (13, 11)}
    points(6, search)
    one_way = {(2, y) for y in range(4, 12)} | {(13, y) for y in range(4, 12)}
    one_way |= {(x, 7) for x in range(4, 12)}
    one_way |= {(10, 5), (11, 6), (12, 7), (11, 8), (10, 9)}
    points(7, one_way)
    two_way = {(x, 5) for x in range(3, 12)} | {(x, 10) for x in range(4, 13)}
    two_way |= {(10, 3), (11, 4), (12, 5), (11, 6), (10, 7)}
    two_way |= {(5, 8), (4, 9), (3, 10), (4, 11), (5, 12)}
    points(8, two_way)
    previous = {(3, 7), (4, 6), (4, 8), (5, 5), (5, 9), (6, 4), (6, 10)}
    previous |= {(x, 7) for x in range(4, 13)}
    points(9, previous)
    points(10, {(15 - x, y) for x, y in previous})
    path = SELECTED / "terminal_controls.png"
    image.save(path)
    return path


def control_contact_sheet(path):
    scale = 12
    cell = 16 * scale
    label_height = 36
    source = Image.open(path).convert("RGBA")
    sheet = Image.new("RGBA", (16 * cell, cell + label_height), (53, 56, 65, 255))
    draw = ImageDraw.Draw(sheet)
    for index in range(16):
        icon = source.crop((index * 16, 0, index * 16 + 16, 16))
        icon = icon.resize((cell, cell), Image.Resampling.NEAREST)
        sheet.alpha_composite(icon, (index * cell, 0))
        draw.line((index * cell, 0, index * cell, sheet.height), fill=(70, 74, 84, 255))
        draw.text((index * cell + 2, cell + 4), str(index), fill=(155, 160, 172, 255))
    sheet.save(ROOT / "selected-terminal-controls.png")


def save_member(name, source, transform):
    image = quantized(source)
    chassis = Image.open(SELECTED / "chassis.png").convert("RGBA")
    apply_chassis(image, chassis)
    transform(image)
    mirror_x(image)
    path = SELECTED / f"{name}.png"
    image.save(path)
    return path


def contact_sheet(paths, output="selected-contact-sheet.png"):
    scale = 12
    cell = 16 * scale
    label_height = 28
    columns = 4
    rows = (len(paths) + columns - 1) // columns
    sheet = Image.new("RGBA", (columns * (cell + 18), rows * (cell + label_height)), (234, 234, 234, 255))
    draw = ImageDraw.Draw(sheet)
    for index, path in enumerate(paths):
        left = (index % columns) * (cell + 18)
        top = (index // columns) * (cell + label_height)
        image = Image.open(path).convert("RGBA").resize((cell, cell), Image.Resampling.NEAREST)
        sheet.alpha_composite(image, (left, top))
        draw.rectangle((left, top, left + cell - 1, top + cell - 1), outline=(55, 55, 55, 255))
        draw.text((left + 2, top + cell + 4), path.stem, fill=(25, 25, 25, 255))
    sheet.save(ROOT / output)


def connected_contact_sheet(paths):
    scale = 4
    cell_width = 80 * scale
    cell_height = 16 * scale
    label_height = 24
    columns = 2
    rows = (len(paths) + columns - 1) // columns
    sheet = Image.new(
        "RGBA",
        (columns * (cell_width + 18), rows * (cell_height + label_height)),
        (234, 234, 234, 255),
    )
    draw = ImageDraw.Draw(sheet)
    for index, path in enumerate(paths):
        left = (index % columns) * (cell_width + 18)
        top = (index // columns) * (cell_height + label_height)
        image = Image.open(path).convert("RGBA").resize(
            (cell_width, cell_height),
            Image.Resampling.NEAREST,
        )
        sheet.alpha_composite(image, (left, top))
        draw.rectangle((left, top, left + cell_width - 1, top + cell_height - 1), outline=(55, 55, 55, 255))
        draw.text((left + 2, top + cell_height + 4), path.stem, fill=(25, 25, 25, 255))
    sheet.save(ROOT / "selected-connected-contact-sheet.png")


def main():
    SELECTED.mkdir(parents=True, exist_ok=True)
    METADATA.mkdir(parents=True, exist_ok=True)
    normalize_candidate_metadata()
    chassis = quantized("candidates/chassis/shared_chassis_04.png")
    mirror_x(chassis)
    chassis.save(SELECTED / "chassis.png")
    member_paths = {}
    member_paths["storage_core"] = save_member(
        "storage_core", "candidates/storage_core/storage_core_02.png", core)
    member_paths["storage_terminal"] = save_member(
        "storage_terminal", "candidates/storage_terminal/storage_terminal_02.png", storage_terminal)
    member_paths["crafting_terminal"] = save_member(
        "crafting_terminal", "candidates/crafting_terminal/crafting_terminal_01.png", crafting_terminal)
    for tier in range(1, 7):
        member_paths[f"storage_unit_t{tier}"] = save_member(
            f"storage_unit_t{tier}",
            "candidates/storage_unit_base/storage_unit_base_03.png",
            lambda image, tier=tier: storage_unit(image, tier),
        )
    member_paths["creative_storage_unit"] = save_member(
        "creative_storage_unit",
        "candidates/creative_storage_unit/creative_storage_unit_01.png",
        creative_storage_unit,
    )
    member_paths["import_bus_top"] = save_member(
        "import_bus_top", "candidates/bus_top/import_bus_allside_01.png",
        lambda image: bus_top(image, True))
    member_paths["import_bus_side"] = save_member(
        "import_bus_side", "candidates/bus_top/import_bus_allside_01.png",
        lambda image: bus_side(image, True))
    member_paths["export_bus_top"] = save_member(
        "export_bus_top", "candidates/bus_top/export_bus_conduit_01.png",
        lambda image: bus_top(image, False))
    member_paths["export_bus_side"] = save_member(
        "export_bus_side", "candidates/bus_top/export_bus_conduit_01.png",
        lambda image: bus_side(image, False))
    member_paths["import_bus_front"] = save_member(
        "import_bus_front", "candidates/import_bus_front/import_bus_front_01.png",
        lambda image: directional_bus(image, True))
    member_paths["export_bus_front"] = save_member(
        "export_bus_front", "candidates/export_bus_front/export_bus_front_01.png",
        lambda image: directional_bus(image, False))
    remote_image = quantized("candidates/remote_terminal/remote_terminal_02.png")
    remote(remote_image)
    member_paths["remote_terminal"] = SELECTED / "remote_terminal.png"
    remote_image.save(member_paths["remote_terminal"])
    control_source = control_atlas()
    control_contact_sheet(control_source)

    metadata = {
        "storage_core": normalize_metadata("storage_core", "storage_core_02"),
        "storage_terminal": normalize_metadata("storage_terminal", "storage_terminal_02"),
        "crafting_terminal": normalize_metadata("crafting_terminal", "crafting_terminal_01"),
        "storage_unit": normalize_metadata("storage_unit_base", "storage_unit_base_03"),
        "creative_storage_unit": normalize_metadata(
            "creative_storage_unit", "creative_storage_unit_01"),
        "import_bus_allside": normalize_metadata(
            "bus_top", "import_bus_allside_01", "import_bus_allside"),
        "export_bus_conduit": normalize_metadata(
            "bus_top", "export_bus_conduit_01", "export_bus_conduit"),
        "import_bus_front": normalize_metadata("import_bus_front", "import_bus_front_01"),
        "export_bus_front": normalize_metadata("export_bus_front", "export_bus_front_01"),
        "remote_terminal": normalize_metadata("remote_terminal", "remote_terminal_02"),
    }
    normalize_metadata("bus_top", "bus_top_01")
    roles = {
        "storage_core": "core_rune_crystal",
        "storage_terminal": "storage_item_grid",
        "crafting_terminal": "crafting_grid_mark",
        **{f"storage_unit_t{tier}": f"storage_cell_tier_{tier}" for tier in range(1, 7)},
        "creative_storage_unit": CREATIVE_ORNAMENT["role"],
        "import_bus_top": "import_casing_top",
        "import_bus_side": "import_casing_side",
        "import_bus_front": "import_inward_arrow",
        "export_bus_top": "export_casing_top",
        "export_bus_side": "export_casing_side",
        "export_bus_front": "export_outward_arrow",
        "remote_terminal": "remote_display",
    }
    metadata_for = {
        "storage_core": metadata["storage_core"],
        "storage_terminal": metadata["storage_terminal"],
        "crafting_terminal": metadata["crafting_terminal"],
        **{f"storage_unit_t{tier}": metadata["storage_unit"] for tier in range(1, 7)},
        "creative_storage_unit": metadata["creative_storage_unit"],
        "import_bus_top": metadata["import_bus_allside"],
        "export_bus_top": metadata["export_bus_conduit"],
        "import_bus_side": metadata["import_bus_allside"],
        "export_bus_side": metadata["export_bus_conduit"],
        "import_bus_front": metadata["import_bus_front"],
        "export_bus_front": metadata["export_bus_front"],
        "remote_terminal": metadata["remote_terminal"],
    }
    members = {}
    for name, role in roles.items():
        category = "item" if name == "remote_terminal" else "block"
        texture_id = f"magic_storage:{category}/{name}"
        runtime = f"src/main/resources/assets/magic_storage/textures/{category}/{name}.png"
        source = member_paths[name]
        members[texture_id] = {
            "role": role,
            "runtime": runtime,
            "source": source.relative_to(ROOT).as_posix(),
            "metadata": metadata_for[name],
            "sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
        }
        if category == "block":
            members[texture_id]["symmetry_axes"] = ["x"]
        runtime_path = PROJECT / runtime
        runtime_path.parent.mkdir(parents=True, exist_ok=True)
        runtime_path.write_bytes(source.read_bytes())
    control_runtime = PROJECT / "src/main/resources/assets/magic_storage/textures/gui/terminal_controls.png"
    control_runtime.write_bytes(control_source.read_bytes())
    connected_names = [
        "storage_core",
        *[f"storage_unit_t{tier}" for tier in range(1, 7)],
        "creative_storage_unit",
        "storage_terminal",
        "crafting_terminal",
        "import_bus_top",
        "import_bus_side",
        "export_bus_top",
        "export_bus_side",
    ]
    connected_members = {}
    connected_paths = []
    for name in connected_names:
        source = member_paths[name]
        selected_connected = SELECTED / f"{name}_connected.png"
        connected_sheet(Image.open(source).convert("RGBA")).save(selected_connected)
        connected_paths.append(selected_connected)
        runtime = (
            "src/main/resources/resourcepacks/fusion_connected_casing/"
            f"assets/magic_storage/textures/block/{name}_connected.png"
        )
        runtime_path = PROJECT / runtime
        runtime_path.write_bytes(selected_connected.read_bytes())
        metadata_path = runtime_path.with_suffix(".png.mcmeta")
        metadata_path.write_text(json.dumps({
            "fusion": {
                "type": "connecting",
                "layout": "pieced",
            }
        }, indent=2) + "\n")
        texture_id = f"magic_storage:block/{name}_connected"
        connected_members[texture_id] = {
            "base": f"magic_storage:block/{name}",
            "runtime": runtime,
            "source": selected_connected.relative_to(ROOT).as_posix(),
            "metadata": metadata_path.relative_to(PROJECT).as_posix(),
            "layout": "pieced",
            "tiles": 5,
            "sha256": hashlib.sha256(selected_connected.read_bytes()).hexdigest(),
        }
    icons = [
        "SORT_ASCENDING", "SORT_DESCENDING", "SORT_NAME", "SORT_QUANTITY", "SORT_MOD",
        "SORT_ID", "SEARCH_OFF", "SEARCH_EMI", "SEARCH_EMI_TWO_WAY", "PREVIOUS", "NEXT",
    ]
    manifest = {
        "schema": 2,
        "model": "retro-diffusion/rd-fast",
        "runtime_size": [16, 16],
        "settings": {
            "seed": 71421,
            "revision_seeds": [71422, 71423, 71424, 71425],
            "block_img2img_strength": 0.38,
            "item_img2img_strength": 0.68,
            "style": "mc_texture/mc_item",
        },
        "palette": PALETTE_HEX,
        "chassis": {
            "source": "selected/chassis.png",
            "metadata": "metadata/chassis.json",
            "selected_candidate": "candidates/chassis/shared_chassis_04.png",
            "points": [list(point) for point in CHASSIS_POINTS],
        },
        "tier_ornaments": [
            {
                "tier": tier,
                "role": ornament["role"],
                "accents": ornament["accents"],
                "detail_points": [[x, y] for x, y, _ in ornament["points"]],
            }
            for tier, ornament in enumerate(TIER_ORNAMENTS, 1)
        ],
        "creative_ornament": {
            "role": CREATIVE_ORNAMENT["role"],
            "accents": CREATIVE_ORNAMENT["accents"],
            "detail_points": [[x, y] for x, y, _ in CREATIVE_ORNAMENT["points"]],
        },
        "members": members,
        "connected_textures": connected_members,
        "control_atlas": {
            "runtime": "src/main/resources/assets/magic_storage/textures/gui/terminal_controls.png",
            "source": "selected/terminal_controls.png",
            "cell_size": 16,
            "icons": [
                {"name": name, "atlas_index": index}
                for index, name in enumerate(icons)
            ],
            "sha256": hashlib.sha256((SELECTED / "terminal_controls.png").read_bytes()).hexdigest(),
        },
        "contact_sheets": [
            "chassis-contact-sheet.png",
            "candidate-contact-sheet.png",
            "selected-contact-sheet.png",
            "selected-connected-contact-sheet.png",
            "creative-storage-unit-candidates.png",
        ],
    }
    (ROOT / "selection.json").write_text(json.dumps(manifest, indent=2) + "\n")
    contact_sheet([member_paths[name] for name in roles])
    contact_sheet(
        [
            ROOT / f"candidates/creative_storage_unit/creative_storage_unit_{index:02d}.png"
            for index in range(1, 4)
        ],
        "creative-storage-unit-candidates.png",
    )
    connected_contact_sheet(connected_paths)


if __name__ == "__main__":
    main()
