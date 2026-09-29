"""Models Galaxy MC's ships in Blender and voxelises them into ship blueprints.

Run with Blender 4.2+ (or the `bpy` module from PyPI):

    blender -b -P tools/blender/build_ships.py        # or: python3 tools/blender/build_ships.py

For every ship this script
  1. builds the hull as real Blender meshes (cylinders, cones, bevelled boxes, swept wings, nacelles),
     tagging each object with the block it is made of and whether it is a hollow hull, a solid part or
     a paint region (windows, stripes, running lights) - the .blend is saved to tools/blender/ so the
     ships can be opened, looked at and reshaped by hand;
  2. voxelises the meshes at one block per unit by ray-cast parity against a BVH of each object;
  3. hollows the hull (one-block shell), keeps solid parts solid, applies paint regions to the shell;
  4. fits out the interior - decks, stairs and ladders, bulkheads, airlocks with boarding ramps,
     cockpit, bunks, cargo, engine room;
  5. writes src/main/resources/data/galaxy_mc/ships/<ship>.json in the format ShipBlueprint reads.

Coordinates: Blender X = Minecraft X, Blender Z = Minecraft Y, Blender -Y = Minecraft Z, so the ship's
nose (Blender +Y) points to Minecraft north (-Z), which is what the blueprint loader expects.
Shapes are centred on x = z = 0.5 so hulls are symmetric around voxel column 0.
"""
import json
import math
import os
import sys

import bpy
import bmesh
from mathutils import Vector
from mathutils.bvhtree import BVHTree

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "galaxy_mc", "ships")

C = 0.5  # voxel-centre offset for the ship's axis

# ---------------------------------------------------------------------------------------------- blocks

HULL = "galaxy_mc:hull_plating"
DARK = "galaxy_mc:hull_plating_dark"
STRIPE = "galaxy_mc:hull_stripe"
GLASS = "galaxy_mc:reinforced_glass"
FLOOR = "galaxy_mc:ship_floor"
LIGHT = "galaxy_mc:ship_light"
THRUSTER = "galaxy_mc:thruster"
LIFE = "galaxy_mc:life_support"
FORGE = "galaxy_mc:stellar_forge"
ARMOR_TABLE = "galaxy_mc:thermal_armor_table"
FUEL_BLOCK = "galaxy_mc:fuel_block"
AIR = "minecraft:air"


def console(facing):
    return f"galaxy_mc:navigation_console[facing={facing}]"


def door(facing, half, hinge="left"):
    return f"galaxy_mc:airlock_door[facing={facing},half={half},hinge={hinge},open=false,powered=false]"


def stairs(facing, half="bottom", material="polished_blackstone"):
    return f"minecraft:{material}_stairs[facing={facing},half={half},shape=straight,waterlogged=false]"


def ladder(facing):
    return f"minecraft:ladder[facing={facing},waterlogged=false]"


def chest(facing):
    return f"minecraft:chest[facing={facing},type=single,waterlogged=false]"


def furnace(facing):
    return f"minecraft:furnace[facing={facing},lit=false]"


def bed(facing, part):
    return f"minecraft:white_bed[facing={facing},part={part},occupied=false]"


# ---------------------------------------------------------------------------------------------- scene

def mc_to_blender(x, y, z):
    """Minecraft blueprint coordinates -> Blender coordinates."""
    return Vector((x, -z, y))


def reset_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def tag(obj, kind, block, priority=0):
    obj["kind"] = kind  # hollow | solid | paint
    obj["block"] = block
    obj["priority"] = priority
    return obj


def material(name, rgb):
    mat = bpy.data.materials.get(name)
    if mat is None:
        mat = bpy.data.materials.new(name)
        mat.diffuse_color = (rgb[0], rgb[1], rgb[2], 1.0)
    return mat


MATERIAL_COLOURS = {
    HULL: (0.85, 0.87, 0.9), DARK: (0.25, 0.27, 0.3), STRIPE: (0.95, 0.45, 0.1), GLASS: (0.3, 0.7, 0.95),
    THRUSTER: (0.35, 0.35, 0.38), LIGHT: (1.0, 0.95, 0.7),
}


def finish(obj, name):
    obj.name = name
    block = obj["block"]
    obj.data.materials.append(material(block.split(":")[1], MATERIAL_COLOURS.get(block, (0.6, 0.6, 0.6))))
    if obj["kind"] == "paint":
        obj.display_type = "WIRE"
    return obj


def cylinder(name, kind, block, x, y0, y1, z, r, axis="y", priority=0, verts=64):
    """Cylinder between y0 and y1 (axis y) or z0..z1 along z (axis 'z', y0/y1 are then z range)."""
    depth = y1 - y0
    mid = (y0 + y1) / 2
    if axis == "y":
        bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=depth, location=mc_to_blender(x, mid, z))
    else:
        bpy.ops.mesh.primitive_cylinder_add(vertices=verts, radius=r, depth=depth, location=mc_to_blender(x, z, mid),
                                            rotation=(math.pi / 2, 0, 0))
    return finish(tag(bpy.context.active_object, kind, block, priority), name)


def cone(name, kind, block, x, y0, y1, z, r0, r1, axis="y", priority=0, verts=64):
    depth = y1 - y0
    mid = (y0 + y1) / 2
    if axis == "y":
        bpy.ops.mesh.primitive_cone_add(vertices=verts, radius1=r0, radius2=r1, depth=depth, location=mc_to_blender(x, mid, z))
    else:
        # Along Minecraft -z (the nose direction): radius r0 at z = y1 (aft end), r1 at z = y0 (fore end).
        bpy.ops.mesh.primitive_cone_add(vertices=verts, radius1=r0, radius2=r1, depth=depth, location=mc_to_blender(x, z, mid),
                                        rotation=(-math.pi / 2, 0, 0))
    return finish(tag(bpy.context.active_object, kind, block, priority), name)


def box(name, kind, block, x0, x1, y0, y1, z0, z1, bevel=0.0, priority=0):
    cx, cy, cz = (x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=mc_to_blender(cx, cy, cz))
    obj = bpy.context.active_object
    obj.scale = (x1 - x0, z1 - z0, y1 - y0)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    if bevel > 0:
        mod = obj.modifiers.new("bevel", "BEVEL")
        mod.width = bevel
        mod.segments = 6
        mod.limit_method = "NONE"
        bpy.ops.object.modifier_apply(modifier=mod.name)
    return finish(tag(obj, kind, block, priority), name)


def ellipsoid(name, kind, block, x, y, z, rx, ry, rz, priority=0):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=64, ring_count=32, radius=1.0, location=mc_to_blender(x, y, z))
    obj = bpy.context.active_object
    obj.scale = (rx, rz, ry)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    return finish(tag(obj, kind, block, priority), name)


def prism(name, kind, block, outline, y0, y1, priority=0):
    """Extrudes a Minecraft-space XZ polygon between heights y0 and y1 (fins, wings, pylons)."""
    mesh = bpy.data.meshes.new(name)
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    bm = bmesh.new()
    bottom = [bm.verts.new(mc_to_blender(px, y0, pz)) for px, pz in outline]
    top = [bm.verts.new(mc_to_blender(px, y1, pz)) for px, pz in outline]
    n = len(outline)
    bm.faces.new(bottom[::-1])
    bm.faces.new(top)
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new((bottom[i], bottom[j], top[j], top[i]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(mesh)
    bm.free()
    bpy.context.view_layer.objects.active = obj
    return finish(tag(obj, kind, block, priority), name)


def side_prism(name, kind, block, outline_zy, x0, x1, priority=0):
    """Extrudes a Minecraft-space (z, y) polygon along x between x0 and x1 (dorsal fins, keels)."""
    mesh = bpy.data.meshes.new(name)
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    bm = bmesh.new()
    left = [bm.verts.new(mc_to_blender(x0, py, pz)) for pz, py in outline_zy]
    right = [bm.verts.new(mc_to_blender(x1, py, pz)) for pz, py in outline_zy]
    n = len(outline_zy)
    bm.faces.new(left[::-1])
    bm.faces.new(right)
    for i in range(n):
        j = (i + 1) % n
        bm.faces.new((left[i], left[j], right[j], right[i]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(mesh)
    bm.free()
    return finish(tag(obj, kind, block, priority), name)


def fin(name, block, angle, root_r, tip_r, root_y0, root_y1, tip_y0, tip_y1, thickness):
    """A swept rocket fin as a thin prism in the plane of `angle` (radians around the y axis)."""
    mesh = bpy.data.meshes.new(name)
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    bm = bmesh.new()
    ca, sa = math.cos(angle), math.sin(angle)
    nx, nz = -sa, ca
    profile = [(root_r, root_y0), (tip_r, tip_y0), (tip_r, tip_y1), (root_r, root_y1)]
    verts = []
    for side in (-0.5, 0.5):
        ring = []
        for r, y in profile:
            px = C + ca * r + nx * thickness * side
            pz = C + sa * r + nz * thickness * side
            ring.append(bm.verts.new(mc_to_blender(px, y, pz)))
        verts.append(ring)
    a, b = verts
    bm.faces.new(a[::-1])
    bm.faces.new(b)
    for i in range(4):
        j = (i + 1) % 4
        bm.faces.new((a[i], a[j], b[j], b[i]))
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    bm.to_mesh(mesh)
    bm.free()
    return finish(tag(obj, "solid", block, 1), name)


# ---------------------------------------------------------------------------------------------- voxels

class Voxeliser:
    """Ray-cast parity voxelisation of every tagged object in the scene."""

    def __init__(self):
        self.objects = []
        depsgraph = bpy.context.evaluated_depsgraph_get()
        for obj in bpy.context.scene.objects:
            if obj.type != "MESH" or "kind" not in obj:
                continue
            eval_obj = obj.evaluated_get(depsgraph)
            mesh = eval_obj.to_mesh()
            verts = [obj.matrix_world @ v.co for v in mesh.vertices]
            polys = [tuple(p.vertices) for p in mesh.polygons]
            bvh = BVHTree.FromPolygons(verts, polys)
            lo = Vector((min(v.x for v in verts), min(v.y for v in verts), min(v.z for v in verts)))
            hi = Vector((max(v.x for v in verts), max(v.y for v in verts), max(v.z for v in verts)))
            self.objects.append((obj, bvh, lo, hi))
            eval_obj.to_mesh_clear()

    @staticmethod
    def inside(bvh, point):
        direction = Vector((1.0, 0.00137, 0.00091)).normalized()
        origin = point.copy()
        hits = 0
        for _ in range(64):
            loc, _, _, _ = bvh.ray_cast(origin, direction)
            if loc is None:
                break
            hits += 1
            origin = loc + direction * 1e-4
        return hits % 2 == 1

    def run(self):
        """Returns {(x, y, z): [objects containing that voxel centre]} in Minecraft coordinates."""
        cells = {}
        for obj, bvh, lo, hi in self.objects:
            # Blender bounds -> Minecraft integer voxel range.
            x0, x1 = math.floor(lo.x) - 1, math.ceil(hi.x) + 1
            y0, y1 = math.floor(lo.z) - 1, math.ceil(hi.z) + 1
            z0, z1 = math.floor(-hi.y) - 1, math.ceil(-lo.y) + 1
            for x in range(x0, x1 + 1):
                for y in range(y0, y1 + 1):
                    for z in range(z0, z1 + 1):
                        if self.inside(bvh, mc_to_blender(x + 0.5, y + 0.5, z + 0.5)):
                            cells.setdefault((x, y, z), []).append(obj)
        return cells


NEIGHBOURS = [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]


class Ship:
    """Voxelised hull plus the fitting-out helpers used by the per-ship interior code."""

    def __init__(self, name, cells):
        self.name = name
        self.blocks = {}
        self.interior = set()
        self.console = None
        hollow = {p for p, objs in cells.items() if any(o["kind"] == "hollow" for o in objs)}
        solid = {p for p, objs in cells.items() if any(o["kind"] == "solid" for o in objs)}

        def best(objs, kind):
            cands = [o for o in objs if o["kind"] == kind]
            return max(cands, key=lambda o: o["priority"])["block"] if cands else None

        for p, objs in cells.items():
            paint = best(objs, "paint")
            if p in hollow:
                shell = any((p[0] + dx, p[1] + dy, p[2] + dz) not in hollow for dx, dy, dz in NEIGHBOURS)
                if shell:
                    self.blocks[p] = paint or best(objs, "hollow")
                elif p not in solid:
                    self.interior.add(p)
                else:
                    self.blocks[p] = paint or best(objs, "solid")
            elif p in solid:
                self.blocks[p] = paint or best(objs, "solid")

    # -------- fitting-out primitives
    def put(self, x, y, z, block):
        p = (x, y, z)
        self.interior.discard(p)
        if block == AIR:
            self.blocks.pop(p, None)
            self.interior.add(p)
        else:
            self.blocks[p] = block

    def is_interior(self, x, y, z):
        return (x, y, z) in self.interior

    def fill_interior_layer(self, y, block, predicate=lambda x, z: True):
        for (x, py, z) in list(self.interior):
            if py == y and predicate(x, z):
                self.put(x, y, z, block)

    def wall_z(self, z, y0, y1, block, gap=None):
        """Bulkhead across the ship at z, with an optional doorway at x in gap (two blocks tall)."""
        for (x, y, pz) in list(self.interior):
            if pz == z and y0 <= y <= y1:
                if gap is not None and x in gap and y <= y0 + 1:
                    continue
                self.put(x, y, z, block)

    def interior_extent(self, y, z):
        xs = [x for (x, py, pz) in self.interior if py == y and pz == z]
        return (min(xs), max(xs)) if xs else (None, None)

    def to_json(self):
        pts = list(self.blocks.keys()) + list(self.interior)
        minx = min(p[0] for p in pts)
        miny = min(p[1] for p in pts)
        minz = min(p[2] for p in pts)
        palette = []
        index = {}
        blocks = []
        for p, b in sorted(self.blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            if b not in index:
                index[b] = len(palette)
                palette.append(b)
            blocks.append([p[0] - minx, p[1] - miny, p[2] - minz, index[b]])
        interior = [[p[0] - minx, p[1] - miny, p[2] - minz] for p in sorted(self.interior, key=lambda p: (p[1], p[2], p[0]))]
        cx, cy, cz = self.console
        return {
            "id": self.name,
            "generator": "tools/blender/build_ships.py",
            "anchor": [0 - minx, 0 - miny, 0 - minz],
            "console": [cx - minx, cy - miny, cz - minz],
            "palette": palette,
            "blocks": blocks,
            "interior": interior,
        }


# ---------------------------------------------------------------------------------------------- rocket

def model_rocket():
    reset_scene()
    R = 5.4
    cylinder("body", "hollow", HULL, C, 3, 22, C, R)
    cone("nose", "hollow", HULL, C, 22, 32, C, R, 0.8)
    cone("skirt", "solid", DARK, C, 1, 3, C, 3.4, R)
    cone("engine_bell", "solid", THRUSTER, C, 0, 1.2, C, 2.8, 2.2)
    for i in range(4):
        fin(f"fin_{i}", STRIPE, math.pi / 4 + i * math.pi / 2, R - 0.8, 9.2, 0, 11, 0, 3.5, 1.05)
    cylinder("stripe_band", "paint", STRIPE, C, 17, 18, C, R + 0.6, priority=2)
    cylinder("stripe_band_2", "paint", STRIPE, C, 5, 6, C, R + 0.6, priority=2)
    cylinder("cockpit_windows", "paint", GLASS, C, 19.2, 21.9, C, R + 0.6, priority=3)
    cone("nose_windows", "paint", GLASS, C, 24.2, 26.8, C, R - 0.2, R - 1.6, priority=3)
    for i, (dx, dz) in enumerate([(1, 0), (-1, 0), (0, -1)]):
        cylinder(f"porthole_{i}", "paint", GLASS, C + dx * R, 10, 12, C + dz * R, 1.2, priority=3, verts=24)
        cylinder(f"porthole_hi_{i}", "paint", GLASS, C + dx * R, 15, 17, C + dz * R, 1.2, priority=3, verts=24)
    cylinder("nose_light", "paint", LIGHT, C, 31, 32.5, C, 1.5, priority=4)
    return "rocket"


def fit_rocket(ship):
    decks = [3, 8, 13, 18]
    # Decks: solid floors across the interior, with a ladder well at (0, -4).
    for y in decks[1:]:
        ship.fill_interior_layer(y, FLOOR, lambda x, z: not (x == 0 and z == -4))
    for (x, y, z) in list(ship.blocks):
        if y == 3 and ship.blocks[(x, y, z)] == HULL:
            ship.blocks[(x, y, z)] = FLOOR
    # Ladder up the north wall from deck 1 to the cockpit.
    for y in range(4, 19):
        ship.put(0, y, -4, ladder("south"))
    # Ceiling lights set into each deck.
    for y in decks[1:]:
        for x, z in [(-2, 1), (2, 1), (0, 3)]:
            ship.put(x, y, z, LIGHT)
    # ---- deck 1: airlock and engine room
    ship.put(0, 4, 5, door("north", "lower"))
    ship.put(0, 5, 5, door("north", "upper"))
    ship.put(-4, 4, 0, LIFE)
    ship.put(4, 4, 0, FUEL_BLOCK)
    ship.put(3, 4, -2, chest("west"))
    ship.put(3, 4, 2, chest("west"))
    ship.put(-3, 4, -2, furnace("east"))
    # Boarding ramp outside the door, three wide.
    for x in (-1, 0, 1):
        ship.put(x, 3, 6, DARK)
        ship.put(x, 2, 7, stairs("north"))
        ship.put(x, 1, 8, stairs("north"))
        ship.put(x, 0, 9, stairs("north"))
        ship.put(x, 1, 7, DARK)
        ship.put(x, 0, 7, DARK)
        ship.put(x, 0, 8, DARK)
    # ---- deck 2: crew
    ship.put(-3, 9, 1, bed("north", "head"))
    ship.put(-3, 9, 2, bed("north", "foot"))
    ship.put(3, 9, 1, bed("north", "head"))
    ship.put(3, 9, 2, bed("north", "foot"))
    ship.put(-3, 9, -2, chest("east"))
    ship.put(3, 9, -2, chest("west"))
    ship.put(0, 9, 3, "minecraft:crafting_table")
    # ---- deck 3: workshop
    ship.put(-3, 14, 0, ARMOR_TABLE)
    ship.put(3, 14, 0, FORGE)
    ship.put(0, 14, 3, chest("north"))
    ship.put(-2, 14, 3, "minecraft:enchanting_table")
    # ---- deck 4: cockpit, console facing the forward windows
    ship.put(0, 19, -2, console("north"))
    ship.put(0, 19, 0, stairs("south", material="smooth_quartz"))
    ship.put(-2, 19, 1, LIFE)
    ship.console = (0, 19, -2)


# ---------------------------------------------------------------------------------------------- starship

def model_starship():
    reset_scene()
    # Fuselage: a bevelled box, 15 wide, 13 tall, 44 long, with a tapering nose.
    box("fuselage", "hollow", HULL, C - 7.4, C + 7.4, 2, 14.4, -22, 22, bevel=4.6)
    ellipsoid("nose", "hollow", HULL, C, 8.0, -21.0, 7.0, 6.0, 12.5)
    # Dorsal fin rising over the engines and a keel under the nose.
    side_prism("dorsal_fin", "solid", HULL, [(4, 13.5), (26, 13.5), (27.5, 21.5), (17, 21.5)], C - 0.55, C + 0.55)
    box("dorsal_fin_stripe", "paint", STRIPE, C - 1, C + 1, 18.1, 19.9, 0, 30, priority=2)
    # Chines: a dark strake along each flank.
    for side in (-1, 1):
        box(f"chine_{side}", "paint", DARK, C + side * 7.4 - 1.5, C + side * 7.4 + 1.5, 7.1, 7.9, -28, 22, priority=1)
    # High wings, swept back, clear of the side airlocks.
    for side in (-1, 1):
        root = C + side * 6.5
        tip = C + side * 18.5
        prism(f"wing_{side}", "solid", HULL,
              [(root, -6), (tip, 9), (tip, 17), (root, 18)] if side > 0 else [(root, 18), (tip, 17), (tip, 9), (root, -6)], 9, 10.6)
        prism(f"wingtip_{side}", "paint", STRIPE,
              [(tip - side * 2.2, 8), (tip + side * 0.5, 8), (tip + side * 0.5, 18), (tip - side * 2.2, 18)]
              if side > 0 else [(tip + side * 0.5, 8), (tip - side * 2.2, 8), (tip - side * 2.2, 18), (tip + side * 0.5, 18)],
              8, 12, priority=2)
        cylinder(f"running_light_{side}", "paint", LIGHT, tip, 9, 11, 16.5, 1.0, priority=4, verts=16)
    # Engine nacelles at the stern.
    cylinder("engine_main", "solid", DARK, C, 20, 28, 8.2, 3.3, axis="z")
    cylinder("engine_main_nozzle", "paint", THRUSTER, C, 27, 28.5, 8.2, 3.6, axis="z", priority=3)
    for side in (-1, 1):
        cylinder(f"engine_{side}", "solid", DARK, C + side * 7.0, 16, 26, 6.2, 2.5, axis="z")
        cylinder(f"engine_{side}_nozzle", "paint", THRUSTER, C + side * 7.0, 25, 26.5, 6.2, 2.8, axis="z", priority=3)
    # Belly lift thrusters and landing gear.
    for z in (-12, 2, 14):
        box(f"lift_{z}", "paint", THRUSTER, C - 2.4, C + 2.4, 1.7, 2.9, z - 1.4, z + 1.4, priority=3)
        for side in (-1, 1):
            box(f"gear_{z}_{side}", "solid", DARK, C + side * 5 - 1, C + side * 5 + 1, 0, 3, z - 1, z + 1)
    # Paint: canopy over the bridge, portholes along the sides, hazard stripes.
    ellipsoid("canopy", "paint", GLASS, C, 11.5, -22.5, 5.6, 3.6, 7.5, priority=3)
    for z in range(-15, 20, 4):
        for side in (-1, 1):
            box(f"window_{z}_{side}", "paint", GLASS, C + side * 7.4 - 1.2, C + side * 7.4 + 1.2, 10.1, 11.9, z + 0.1, z + 1.9, priority=3)
            if not 0 <= z <= 4:
                box(f"window_lo_{z}_{side}", "paint", GLASS, C + side * 7.4 - 1.2, C + side * 7.4 + 1.2, 5.1, 5.9, z + 0.1, z + 1.9,
                    priority=3)
    for z in (-9, 10):
        box(f"stripe_{z}", "paint", STRIPE, C - 9, C + 9, 1, 16, z, z + 1, priority=2)
    box("spine_light", "paint", LIGHT, C - 0.6, C + 0.6, 14, 16, -4, -3, priority=4)
    return "starship"


def fit_starship(ship):
    LOWER = 3
    UPPER = 8
    lower_y = range(LOWER + 1, UPPER)
    # Lower deck floor across the whole hull (the bottom shell layer is already there at y = 2).
    ship.fill_interior_layer(LOWER, FLOOR)
    # Upper deck: forward (bridge and mess) and aft (quarters); the cargo bay between is double-height.
    ship.fill_interior_layer(UPPER, FLOOR, lambda x, z: z <= -5 or z >= 9)
    # Bulkheads with doorways: forward storage | mess | cargo | engine room.
    ship.wall_z(-14, LOWER + 1, UPPER - 1, DARK, gap=range(-1, 2))
    ship.wall_z(11, LOWER + 1, UPPER - 1, DARK, gap=range(-1, 2))
    # Railings (dark hull) along the upper deck edges over the cargo bay.
    for x in range(-6, 7):
        if ship.is_interior(x, UPPER + 1, -5) and x not in (4, 5, 6):
            ship.put(x, UPPER + 1, -5, "minecraft:iron_bars[east=false,north=false,south=false,west=false,waterlogged=false]")
        if ship.is_interior(x, UPPER + 1, 9) and x not in (-4, -5, -6):
            ship.put(x, UPPER + 1, 9, "minecraft:iron_bars[east=false,north=false,south=false,west=false,waterlogged=false]")
    # Staircases up from the cargo bay: starboard one forward, port one aft.
    for i in range(5):
        for x in (5, 4):
            ship.put(x, LOWER + 1 + i, 0 - i, stairs("north"))
            for y in range(LOWER + 2 + i, LOWER + 5 + i):
                if (x, y, -i) in ship.blocks and ship.blocks[(x, y, -i)] == FLOOR:
                    ship.put(x, y, -i, AIR)
        for x in (-5, -4):
            ship.put(x, LOWER + 1 + i, 4 + i, stairs("south"))
    # Lights in the ceilings.
    for z in range(-18, 20, 5):
        for x in (-3, 3):
            top = max((y for (px, y, pz) in ship.interior if px == x and pz == z), default=None)
            if top is not None:
                ship.put(x, top, z, LIGHT)
    # ---- airlocks with ramps on both sides of the cargo bay
    for side, facing in ((1, "west"), (-1, "east")):
        hull_x = [x for (x, y, z) in ship.blocks if y == LOWER + 1 and z == 2 and x * side > 0]
        wall_x = max(hull_x) if side > 0 else min(hull_x)
        for dz in (2, 3):
            ship.put(wall_x, LOWER, dz, DARK)
            ship.put(wall_x, LOWER + 1, dz, door(facing, "lower", "left" if dz == 2 else "right"))
            ship.put(wall_x, LOWER + 2, dz, door(facing, "upper", "left" if dz == 2 else "right"))
        out = wall_x + side
        for dz in (1, 2, 3, 4):
            ship.put(out, LOWER, dz, DARK)
            ship.put(out + side, LOWER - 1, dz, stairs(facing))
            ship.put(out + 2 * side, LOWER - 2, dz, stairs(facing))
            ship.put(out + 3 * side, LOWER - 3, dz, stairs(facing))
            for k in range(1, 3):
                ship.put(out + k * side, LOWER - 1 - k, dz, DARK)
            ship.put(out, LOWER - 1, dz, DARK)
            ship.put(out, LOWER - 2, dz, DARK)
            ship.put(out, LOWER - 3, dz, DARK)
    # ---- cargo bay
    for z in range(-3, 9, 2):
        if ship.is_interior(-6, LOWER + 1, z) and not 0 <= z <= 5:
            ship.put(-6, LOWER + 1, z, chest("east"))
        if ship.is_interior(6, LOWER + 1, z) and z > 4:
            ship.put(6, LOWER + 1, z, chest("west"))
    ship.put(0, LOWER + 1, -2, FORGE)
    ship.put(-2, LOWER + 1, -2, ARMOR_TABLE)
    ship.put(2, LOWER + 1, -2, "minecraft:crafting_table")
    ship.put(0, LOWER + 1, 6, "minecraft:enchanting_table")
    # ---- mess (forward lower deck)
    ship.put(-4, LOWER + 1, -12, furnace("east"))
    ship.put(-4, LOWER + 1, -11, "minecraft:smoker[facing=east,lit=false]")
    ship.put(4, LOWER + 1, -12, chest("west"))
    ship.put(4, LOWER + 1, -11, chest("west"))
    ship.put(0, LOWER + 1, -10, "minecraft:crafting_table")
    # ---- forward storage (behind the nose)
    for x in (-2, 0, 2):
        if ship.is_interior(x, LOWER + 1, -18):
            ship.put(x, LOWER + 1, -18, chest("south"))
    # ---- engine room
    ship.put(-3, LOWER + 1, 16, LIFE)
    ship.put(3, LOWER + 1, 16, LIFE)
    for x in (-1, 0, 1):
        ship.put(x, LOWER + 1, 19, FUEL_BLOCK)
        ship.put(x, LOWER + 2, 19, THRUSTER)
    ship.put(5, LOWER + 1, 14, chest("west"))
    # ---- bridge (forward upper deck): console facing the canopy, seats behind it
    ship.put(0, UPPER + 1, -20, console("north"))
    ship.put(0, UPPER + 1, -18, stairs("south", material="smooth_quartz"))
    ship.put(-2, UPPER + 1, -17, stairs("south", material="smooth_quartz"))
    ship.put(2, UPPER + 1, -17, stairs("south", material="smooth_quartz"))
    ship.put(-3, UPPER + 1, -8, chest("east"))
    ship.put(3, UPPER + 1, -8, "minecraft:cartography_table")
    ship.console = (0, UPPER + 1, -20)
    # ---- crew quarters (aft upper deck): four bunks
    for x in (-4, -1, 2):
        if ship.is_interior(x, UPPER + 1, 14):
            ship.put(x, UPPER + 1, 13, bed("north", "head"))
            ship.put(x, UPPER + 1, 14, bed("north", "foot"))
    ship.put(4, UPPER + 1, 17, chest("west"))
    ship.put(-4, UPPER + 1, 17, chest("east"))
    ship.put(0, UPPER + 1, 18, LIFE)


# ---------------------------------------------------------------------------------------------- main

SHIPS = [(model_rocket, fit_rocket), (model_starship, fit_starship)]


def main():
    os.makedirs(OUT, exist_ok=True)
    only = [a for a in sys.argv[sys.argv.index("--") + 1:]] if "--" in sys.argv else []
    for model, fit in SHIPS:
        name = model()
        if only and name not in only:
            continue
        bpy.ops.wm.save_as_mainfile(filepath=os.path.join(HERE, f"{name}.blend"))
        cells = Voxeliser().run()
        ship = Ship(name, cells)
        fit(ship)
        data = ship.to_json()
        with open(os.path.join(OUT, f"{name}.json"), "w") as f:
            json.dump(data, f, separators=(",", ":"))
        print(f"{name}: {len(data['blocks'])} blocks, {len(data['interior'])} interior cells, palette {len(data['palette'])}")


if __name__ == "__main__":
    main()
