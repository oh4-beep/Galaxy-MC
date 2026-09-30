#!/usr/bin/env bash
# Builds and runs the offline terrain previewer against the real Galaxy MC terrain code.
# Needs only a JDK (17+). Usage: tools/terrain-preview/run.sh sheet out.png 24
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
SRC="$ROOT/src/main/java/com/galaxymc"
python3 "$HERE/gen_stubs.py" >/dev/null
rm -rf "$HERE/build/classes"
mkdir -p "$HERE/build/classes"
javac -nowarn -encoding UTF-8 -d "$HERE/build/classes" \
  $(find "$HERE/build/stubs" -name '*.java') \
  "$SRC"/util/Hash.java "$SRC"/util/NameGenerator.java "$SRC"/util/Noise.java \
  "$SRC"/galaxy/FrontierMap.java "$SRC"/galaxy/Hazard.java "$SRC"/galaxy/FrontierPlanets.java "$SRC"/galaxy/Galaxy.java \
  "$SRC"/galaxy/PlanetProfile.java "$SRC"/galaxy/PlanetType.java "$SRC"/galaxy/SolarSystem.java \
  "$SRC"/galaxy/Star.java "$SRC"/galaxy/StarClass.java \
  "$SRC"/world/TerrainShaper.java "$SRC"/world/PlanetColumns.java "$SRC"/world/TerranBiome.java \
  "$HERE"/src/com/galaxymc/tools/TerrainPreviewMain.java
exec java -Djava.awt.headless=true -cp "$HERE/build/classes" com.galaxymc.tools.TerrainPreviewMain "$@"
