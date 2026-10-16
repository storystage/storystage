#!/usr/bin/env bash
# Builds the portable StoryStage app for Windows from a completed Maven build.
#
# Usage: storystage/build/package-windows.sh <version> <output-dir>
#   Run from the repository root after `mvn install`. Needs JDK 21 (jpackage).
#
# Layout of the result (<output-dir>/StoryStage):
#   StoryStage.exe, StoryStage-console.exe   launchers
#   runtime/                                 Java 21 runtime made by jpackage
#   app/*.jar                                IDE jars (classpath)
#   app/javafx/*.jar                         JavaFX (module path; required because
#                                            EntryPoint extends javafx Application)
#   app/distribution/                        core/resources/target/distribution
#                                            (gallery, platform natives, EULAs)
set -euo pipefail

VERSION="${1:?version required, e.g. 0.1.0}"
OUT="${2:?output directory required}"
NAME="StoryStage"
ROOT="$(pwd)"
LIB="$ROOT/alice-ide/target/lib"
MAIN_JAR="$(ls "$ROOT"/alice-ide/target/alice-ide-*.jar | head -1)"
DIST="$ROOT/core/resources/target/distribution"
STAGE="$ROOT/target/storystage-package"

[ -f "$MAIN_JAR" ] || { echo "alice-ide jar not found; run mvn install first" >&2; exit 1; }
[ -d "$DIST" ] || { echo "$DIST not found; run mvn install first" >&2; exit 1; }

rm -rf "$STAGE" "$OUT/$NAME"
mkdir -p "$STAGE/input" "$STAGE/javafx" "$OUT"

# Classpath jars, with JavaFX kept apart for the module path.
cp "$MAIN_JAR" "$STAGE/input/"
for jar in "$LIB"/*.jar; do
  case "$(basename "$jar")" in
    javafx-*) cp "$jar" "$STAGE/javafx/" ;;
    *) cp "$jar" "$STAGE/input/" ;;
  esac
done

# JVM options, matching the Alice installer (installer/aliceinstallerproject.install4j).
JAVA_OPTS=(
  "-Xmx1024m"
  "-Dorg.alice.ide.rootDirectory=\$APPDIR/distribution"
  "-Dswing.aatext=true"
  "-Djogamp.gluegen.UseTempJarCache=false"
  "--module-path" "\$APPDIR/javafx"
  "--add-modules" "javafx.base,javafx.graphics,javafx.media"
  "--add-opens=java.base/java.io=ALL-UNNAMED"
  "--add-opens=java.desktop/sun.awt=ALL-UNNAMED"
  "--add-opens=java.base/java.time=ALL-UNNAMED"
)
JAVA_OPT_ARGS=()
for opt in "${JAVA_OPTS[@]}"; do JAVA_OPT_ARGS+=(--java-options "$opt"); done

# Second launcher with a console window, for diagnostics and the CI smoke test.
cat > "$STAGE/console.properties" <<EOF
win-console=true
EOF

jpackage \
  --type app-image \
  --name "$NAME" \
  --app-version "$VERSION" \
  --vendor "StoryStage contributors" \
  --description "StoryStage: direct your own 3D stories, then code them. Based on Alice 3." \
  --input "$STAGE/input" \
  --main-jar "$(basename "$MAIN_JAR")" \
  --main-class org.alice.stageide.EntryPoint \
  --add-launcher "$NAME-console=$STAGE/console.properties" \
  "${JAVA_OPT_ARGS[@]}" \
  --dest "$OUT"

# Files that must not be on the classpath go in after jpackage has run.
cp -r "$STAGE/javafx" "$OUT/$NAME/app/javafx"
cp -r "$DIST" "$OUT/$NAME/app/distribution"
cp "$ROOT/License.txt" "$ROOT/NOTICE" "$OUT/$NAME/"

echo "Packaged $OUT/$NAME"
