#!/bin/bash
# publish-updatesite.sh -- Build and publish the Eclipse update site
#
# Usage:
#   ./publish-updatesite.sh              # build + publish
#   ./publish-updatesite.sh --skip-build # publish existing build only
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SITE_REPO_URL="https://git.epod.dev/erhan/epod-adt-mcp-updatesite.git"
SITE_SOURCE="$SCRIPT_DIR/com.epod.adt.mcp.site/target/repository"
WORK_TMPDIR=""

cleanup() { [ -n "$WORK_TMPDIR" ] && rm -rf "$WORK_TMPDIR"; }
trap cleanup EXIT

# ── Build ────────────────────────────────────────────────────────────────
if [ "${1:-}" != "--skip-build" ]; then
    echo "==> Building project..."
    cd "$SCRIPT_DIR"
    mvn clean verify -q
    echo "==> Build complete."
else
    echo "==> Skipping build (--skip-build)."
fi

if [ ! -d "$SITE_SOURCE" ]; then
    echo "ERROR: Update site not found at $SITE_SOURCE"
    echo "       Run 'mvn clean verify' first."
    exit 1
fi

# ── Extract version ──────────────────────────────────────────────────────
PLUGIN_JAR=$(ls "$SITE_SOURCE"/plugins/com.epod.adt.mcp.plugin_*.jar 2>/dev/null | head -1)
if [ -n "$PLUGIN_JAR" ]; then
    VERSION=$(basename "$PLUGIN_JAR" | sed 's/com.epod.adt.mcp.plugin_//;s/\.jar//')
    echo "==> Plugin version: $VERSION"
else
    VERSION="0.0.0.$(date +%Y%m%d%H%M)"
    echo "==> WARNING: Could not determine version from plugin JAR. Using $VERSION"
fi

# ── Clone, update, push ─────────────────────────────────────────────────
WORK_TMPDIR=$(mktemp -d)
echo "==> Cloning update site repo..."
git clone --quiet "$SITE_REPO_URL" "$WORK_TMPDIR/site"

echo "==> Preparing composite p2 repository..."

# Remove old artifacts, keep README and .git
find "$WORK_TMPDIR/site" -maxdepth 1 -not -name '.git' -not -name 'README.md' -not -path "$WORK_TMPDIR/site" | xargs rm -rf

# Copy Tycho output into versioned child directory
mkdir -p "$WORK_TMPDIR/site/$VERSION"
cp -R "$SITE_SOURCE"/* "$WORK_TMPDIR/site/$VERSION/"

# Generate composite p2 metadata at root
TIMESTAMP=$(date +%s)

cat > "$WORK_TMPDIR/site/compositeContent.xml" << EOF
<?xml version='1.0' encoding='UTF-8'?>
<?compositeMetadataRepository version='1.0.0'?>
<repository name='EPOD ADT MCP Server Update Site'
    type='org.eclipse.equinox.internal.p2.metadata.repository.CompositeMetadataRepository'
    version='1.0.0'>
  <properties size='2'>
    <property name='p2.timestamp' value='${TIMESTAMP}'/>
    <property name='p2.compressed' value='false'/>
  </properties>
  <children size='1'>
    <child location='${VERSION}/'/>
  </children>
</repository>
EOF

cat > "$WORK_TMPDIR/site/compositeArtifacts.xml" << EOF
<?xml version='1.0' encoding='UTF-8'?>
<?compositeArtifactRepository version='1.0.0'?>
<repository name='EPOD ADT MCP Server Update Site'
    type='org.eclipse.equinox.internal.p2.artifact.repository.CompositeArtifactRepository'
    version='1.0.0'>
  <properties size='2'>
    <property name='p2.timestamp' value='${TIMESTAMP}'/>
    <property name='p2.compressed' value='false'/>
  </properties>
  <children size='1'>
    <child location='${VERSION}/'/>
  </children>
</repository>
EOF

cat > "$WORK_TMPDIR/site/p2.index" << 'EOF'
version=1
metadata.repository.factory.order=compositeContent.xml,\!
artifact.repository.factory.order=compositeArtifacts.xml,\!
EOF

# Validate expected files exist
for f in compositeContent.xml compositeArtifacts.xml p2.index "$VERSION/content.jar" "$VERSION/artifacts.jar" "$VERSION/p2.index"; do
    [ -f "$WORK_TMPDIR/site/$f" ] || { echo "ERROR: Missing $f"; exit 1; }
done

cd "$WORK_TMPDIR/site"
git add -A

if git diff --cached --quiet; then
    echo "==> No changes to publish. Update site is already up to date."
    exit 0
fi

git commit -m "Release $VERSION"
git push

echo ""
echo "==> Published update site v$VERSION"
echo "    URL: https://git.epod.dev/erhan/epod-adt-mcp-updatesite/raw/branch/main/"
