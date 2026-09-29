"""Renders the voxelised ship blueprints with Cycles: an exterior view and a cutaway of the decks.

    blender -b -P tools/blender/render_ships.py -- out_dir      # or: python3 tools/blender/render_ships.py out_dir
"""
import json
import math
import os
import sys

import bpy
from mathutils import Vector

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
SHIPS = os.path.join(ROOT, "src", "main", "resources", "data", "galaxy_mc", "ships")

COLOURS = {
    "hull_plating": (0.82, 0.84, 0.88, 0.35), "hull_plating_dark": (0.16, 0.17, 0.2, 0.4), "hull_stripe": (0.95, 0.42, 0.08, 0.4),
    "reinforced_glass": (0.35, 0.75, 1.0, 0.05), "ship_floor": (0.45, 0.47, 0.5, 0.5), "ship_light": (1.0, 0.95, 0.75, 0.2),
    "thruster": (0.3, 0.3, 0.33, 0.3), "life_support": (0.2, 0.85, 0.85, 0.3), "navigation_console": (0.2, 0.35, 0.95, 0.3),
    "stellar_forge": (0.55, 0.25, 0.8, 0.3), "thermal_armor_table": (0.6, 0.4, 0.2, 0.6), "fuel_block": (0.8, 0.15, 0.1, 0.4),
    "airlock_door": (0.7, 0.72, 0.75, 0.3), "chest": (0.6, 0.4, 0.15, 0.7), "white_bed": (0.9, 0.9, 0.9, 0.8),
    "ladder": (0.55, 0.4, 0.2, 0.8), "crafting_table": (0.55, 0.38, 0.2, 0.8), "furnace": (0.4, 0.4, 0.4, 0.8),
    "polished_blackstone_stairs": (0.18, 0.16, 0.2, 0.6), "smooth_quartz_stairs": (0.92, 0.9, 0.86, 0.4),
    "enchanting_table": (0.5, 0.1, 0.15, 0.4), "iron_bars": (0.6, 0.6, 0.62, 0.3), "smoker": (0.35, 0.33, 0.3, 0.8),
    "cartography_table": (0.5, 0.4, 0.3, 0.8),
}


def block_name(state):
    return state.split(":")[1].split("[")[0]


def build(data, cut_x=None):
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    materials = {}
    meshes = {}
    for x, y, z, pi in data["blocks"]:
        name = block_name(data["palette"][pi])
        ax, ay, az = data["anchor"]
        mx, my, mz = x - ax, y - ay, z - az
        if cut_x is not None and mx > cut_x:
            continue
        meshes.setdefault(name, []).append((mx, my, mz))
    for name, cells in meshes.items():
        verts, faces = [], []
        occupied = set(cells)
        for (x, y, z) in cells:
            # Blender coordinates: (x, -z, y); emit only faces not hidden by a neighbour of the same block.
            for (dx, dy, dz), quad in FACES:
                if (x + dx, y + dy, z + dz) in occupied:
                    continue
                base = len(verts)
                for qx, qy, qz in quad:
                    verts.append((x + qx, -(z + qz), y + qy))
                faces.append((base, base + 1, base + 2, base + 3))
        mesh = bpy.data.meshes.new(name)
        mesh.from_pydata(verts, [], faces)
        obj = bpy.data.objects.new(name, mesh)
        scene.collection.objects.link(obj)
        r, g, b, rough = COLOURS.get(name, (0.6, 0.6, 0.6, 0.5))
        mat = bpy.data.materials.new(name)
        mat.use_nodes = True
        bsdf = mat.node_tree.nodes["Principled BSDF"]
        bsdf.inputs["Base Color"].default_value = (r, g, b, 1)
        bsdf.inputs["Roughness"].default_value = rough
        if name in ("reinforced_glass",):
            bsdf.inputs["Transmission Weight"].default_value = 0.9
            bsdf.inputs["Alpha"].default_value = 0.35
        if name in ("ship_light", "life_support", "navigation_console"):
            bsdf.inputs["Emission Color"].default_value = (r, g, b, 1)
            bsdf.inputs["Emission Strength"].default_value = 3.0
        obj.data.materials.append(mat)
        materials[name] = mat
    # Ground plane
    bpy.ops.mesh.primitive_plane_add(size=400, location=(0, 0, 0))
    ground = bpy.context.active_object
    gm = bpy.data.materials.new("ground")
    gm.use_nodes = True
    gm.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.55, 0.38, 0.28, 1)
    ground.data.materials.append(gm)
    # Sky and sun
    world = bpy.data.worlds.new("sky")
    scene.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.35, 0.45, 0.65, 1)
    world.node_tree.nodes["Background"].inputs["Strength"].default_value = 0.8
    bpy.ops.object.light_add(type="SUN", rotation=(math.radians(50), math.radians(10), math.radians(35)))
    bpy.context.active_object.data.energy = 3.5


FACES = [
    ((1, 0, 0), [(1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)]),
    ((-1, 0, 0), [(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)]),
    ((0, 1, 0), [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)]),
    ((0, -1, 0), [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)]),
    ((0, 0, 1), [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)]),
    ((0, 0, -1), [(0, 0, 0), (0, 1, 0), (1, 1, 0), (1, 0, 0)]),
]


def camera(target, distance, yaw, pitch):
    t = Vector(target)
    d = Vector((math.cos(pitch) * math.sin(yaw), -math.cos(pitch) * math.cos(yaw), math.sin(pitch))) * distance
    bpy.ops.object.camera_add(location=t + d)
    cam = bpy.context.active_object
    direction = t - cam.location
    cam.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()
    cam.data.lens = 40
    bpy.context.scene.camera = cam


def render(path, w=960, h=640, samples=48):
    scene = bpy.context.scene
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = samples
    scene.cycles.use_denoising = True
    scene.render.resolution_x = w
    scene.render.resolution_y = h
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)


def main():
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    out = args[0] if args else HERE
    os.makedirs(out, exist_ok=True)
    only = args[1:]
    for name, dist in (("rocket", 60), ("starship", 95)):
        if only and name not in only:
            continue
        with open(os.path.join(SHIPS, f"{name}.json")) as f:
            data = json.load(f)
        ys = [b[1] - data["anchor"][1] for b in data["blocks"]]
        centre = (0.5, 0.5, max(ys) * 0.42)
        build(data)
        camera(centre, dist, math.radians(215), math.radians(22))
        render(os.path.join(out, f"{name}_exterior.png"))
        build(data, cut_x=0)
        camera(centre, dist * 0.8, math.radians(90), math.radians(12))
        render(os.path.join(out, f"{name}_cutaway.png"))


if __name__ == "__main__":
    main()
