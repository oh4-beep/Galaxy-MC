"""Renders a line-up of Galaxy MC creatures from their generated model JSON and textures.

Replicates Minecraft's ModelPart maths (pivot translation, ZYX rotation, box-UV face mapping, the
renderer's -1/-1/1 flip) so what you see here is what the game draws.

    python3 tools/mobs/render_mobs.py out.png [species ...]
"""
import json
import math
import os
import sys

import bpy
from mathutils import Matrix, Vector

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "galaxy_mc")


def part_matrices(parts):
    by = {p["name"]: p for p in parts}
    cache = {}

    def m(name):
        if name in cache:
            return cache[name]
        p = by[name]
        x, y, z = p["pivot"]
        rx, ry, rz = p["rot"]
        local = Matrix.Translation(Vector((x, y, z))) @ Matrix.Rotation(rz, 4, "Z") @ Matrix.Rotation(ry, 4, "Y") @ Matrix.Rotation(rx, 4, "X")
        parent = p["parent"]
        cache[name] = (m(parent) @ local) if parent in by else local
        return cache[name]

    return {p["name"]: m(p["name"]) for p in parts}


def build(model, texture_path, name, offset, scale=1.0):
    tw, th = model["texture"]
    mats = part_matrices(model["parts"])
    verts, faces, uvs = [], [], []
    for p in model["parts"]:
        M = mats[p["name"]]
        for c in p["cubes"]:
            ox, oy, oz = c["origin"]
            w, h, d = c["size"]
            g = c.get("inflate", 0.0)
            x0, y0, z0 = ox - g, oy - g, oz - g
            x1, y1, z1 = ox + w + g, oy + h + g, oz + d + g
            V = [Vector((x0, y0, z0)), Vector((x1, y0, z0)), Vector((x1, y1, z0)), Vector((x0, y1, z0)),
                 Vector((x0, y0, z1)), Vector((x1, y0, z1)), Vector((x1, y1, z1)), Vector((x0, y1, z1))]
            u, v = c["uv"]
            u0, u1, u2, u3, u4, u5 = u, u + d, u + d + w, u + d + 2 * w, u + 2 * d + w, u + 2 * d + 2 * w
            v0, v1, v2 = v, v + d, v + d + h
            polys = [
                ((5, 4, 0, 1), (u1, v0, u2, v1)), ((2, 3, 7, 6), (u2, v1, u3, v0)), ((0, 4, 7, 3), (u0, v1, u1, v2)),
                ((1, 0, 3, 2), (u1, v1, u2, v2)), ((5, 1, 2, 6), (u2, v1, u4, v2)), ((4, 5, 6, 7), (u4, v1, u5, v2)),
            ]
            for idx, (pu1, pv1, pu2, pv2) in polys:
                base = len(verts)
                for k in idx:
                    q = M @ V[k]
                    # Renderer flip (-1, -1, 1) and lift so y = 24 lands on the ground; to blocks; to Blender axes.
                    wx, wy, wz = -q.x / 16.0, (24.0 - q.y) / 16.0, q.z / 16.0
                    verts.append((offset[0] + wx * scale, offset[1] - wz * scale, wy * scale))
                corners = [(pu2, pv1), (pu1, pv1), (pu1, pv2), (pu2, pv2)]
                uvs.extend((cu / tw, 1.0 - cv / th) for cu, cv in corners)
                faces.append((base, base + 1, base + 2, base + 3))
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    uv = mesh.uv_layers.new()
    for i, loop in enumerate(mesh.loops):
        uv.data[i].uv = uvs[loop.vertex_index]
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.scene.collection.objects.link(obj)
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    nodes = mat.node_tree.nodes
    tex = nodes.new("ShaderNodeTexImage")
    tex.image = bpy.data.images.load(texture_path)
    tex.interpolation = "Closest"
    bsdf = nodes["Principled BSDF"]
    mat.node_tree.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    mat.node_tree.links.new(tex.outputs["Alpha"], bsdf.inputs["Alpha"])
    bsdf.inputs["Roughness"].default_value = 0.8
    obj.data.materials.append(mat)
    return obj


def main():
    args = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else sys.argv[1:]
    out = args[0]
    ids = args[1:] or ["frost_stalker", "plains_grazer", "rock_hound", "stone_behemoth", "scarab", "blade_mantis", "cave_weaver",
                       "dune_scorpion", "grey_watcher", "rock_golem", "sand_raptor", "stilt_strider", "snow_brute", "shore_crab",
                       "toxic_ooze", "cave_glider", "sky_ray", "frost_wyvern", "glow_jelly", "watcher_eye", "spitter_plant",
                       "shroom_walker", "shardling", "boulder_mimic", "rock_tortoise", "lumen_stag", "dune_runner", "swamp_hydra",
                       "tentacle_horror", "dune_worm"]
    bpy.ops.wm.read_factory_settings(use_empty=True)
    cols = 6
    for i, sid in enumerate(ids):
        with open(os.path.join(ASSETS, "creature_models", f"{sid}.json")) as f:
            model = json.load(f)
        tex = os.path.join(ASSETS, "textures", "entity", "creature", f"{sid}.png")
        build(model, tex, sid, ((i % cols) * 3.2, -(i // cols) * 3.2))
    scene = bpy.context.scene
    world = bpy.data.worlds.new("w")
    scene.world = world
    world.use_nodes = True
    world.node_tree.nodes["Background"].inputs["Color"].default_value = (0.5, 0.55, 0.65, 1)
    bpy.ops.object.light_add(type="SUN", rotation=(math.radians(40), 0, math.radians(-30)))
    bpy.context.active_object.data.energy = 3
    rows = (len(ids) + cols - 1) // cols
    cx = (cols - 1) * 3.2 / 2
    cy = -(rows - 1) * 3.2 / 2
    bpy.ops.object.camera_add(location=(cx + 9, cy + 24, 11))
    cam = bpy.context.active_object
    cam.rotation_euler = (Vector((cx, cy, 0.8)) - cam.location).to_track_quat("-Z", "Y").to_euler()
    cam.data.type = "ORTHO"
    cam.data.ortho_scale = max(cols, rows) * 3.4
    scene.camera = cam
    scene.render.engine = "CYCLES"
    scene.cycles.samples = 24
    scene.render.resolution_x = 1400
    scene.render.resolution_y = 1000
    scene.render.filepath = out
    bpy.ops.render.render(write_still=True)


if __name__ == "__main__":
    main()
