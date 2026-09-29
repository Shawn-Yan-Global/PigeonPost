#!/bin/bash

# ============================================================
# Version Bump Script for CI/CD
# ============================================================
# This script helps automate version bumping for the app.
# It can be used in CI/CD pipelines to automatically increment
# the version code and update the version name.
#
# Usage:
#   ./scripts/bump_version.sh patch    # 1.4.0 -> 1.4.1
#   ./scripts/bump_version.sh minor    # 1.4.0 -> 1.5.0
#   ./scripts/bump_version.sh major    # 1.4.0 -> 2.0.0
#   ./scripts/bump_version.sh <version>  # Set specific version like 1.5.0
# ============================================================

set -e

VERSION_FILE="version.properties"

if [ ! -f "$VERSION_FILE" ]; then
    echo "Error: $VERSION_FILE not found!"
    exit 1
fi

# Read current version
CURRENT_VERSION=$(grep "versionName=" "$VERSION_FILE" | cut -d'=' -f2)
CURRENT_CODE=$(grep "versionCode=" "$VERSION_FILE" | cut -d'=' -f2)

echo "Current version: $CURRENT_VERSION (code: $CURRENT_CODE)"

# Parse version numbers
IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VERSION"

# Determine new version based on argument
case "$1" in
    "major")
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        ;;
    "minor")
        MINOR=$((MINOR + 1))
        PATCH=0
        ;;
    "patch")
        PATCH=$((PATCH + 1))
        ;;
    "")
        echo "Error: Please specify version bump type (major/minor/patch) or a specific version"
        echo "Usage: $0 [major|minor|patch|<version>]"
        exit 1
        ;;
    *)
        # Assume it's a specific version number
        if [[ $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
            NEW_VERSION="$1"
        else
            echo "Error: Invalid version format. Use major/minor/patch or semantic version like 1.2.3"
            exit 1
        fi
        ;;
esac

# Construct new version if not already set
if [ -z "$NEW_VERSION" ]; then
    NEW_VERSION="$MAJOR.$MINOR.$PATCH"
fi

# Increment version code
NEW_CODE=$((CURRENT_CODE + 1))

echo "New version: $NEW_VERSION (code: $NEW_CODE)"

# Update version.properties file
cat > "$VERSION_FILE" << EOF
# Application Version Configuration
# This file is used for version management and can be modified by CI/CD scripts
# DO NOT manually edit this file unless you are releasing a new version

# Version code must be an integer that increases with each release
versionCode=$NEW_CODE

# Version name follows semantic versioning (MAJOR.MINOR.PATCH)
versionName=$NEW_VERSION
EOF

echo "✅ Version updated successfully!"
echo "   $CURRENT_VERSION (code: $CURRENT_CODE) -> $NEW_VERSION (code: $NEW_CODE)"




