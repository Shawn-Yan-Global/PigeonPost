#!/usr/bin/env python3
"""
Version Bump Script for CI/CD (Python version)

This script helps automate version bumping for the app.
It can be used in CI/CD pipelines to automatically increment
the version code and update the version name.

Usage:
    python scripts/bump_version.py patch    # 1.4.0 -> 1.4.1
    python scripts/bump_version.py minor    # 1.4.0 -> 1.5.0
    python scripts/bump_version.py major    # 1.4.0 -> 2.0.0
    python scripts/bump_version.py 1.5.0    # Set specific version
"""

import sys
import re
from pathlib import Path


def read_version_file(file_path):
    """Read version.properties file and return version info."""
    if not file_path.exists():
        raise FileNotFoundError(f"Error: {file_path} not found!")
    
    version_code = None
    version_name = None
    
    with open(file_path, 'r') as f:
        for line in f:
            line = line.strip()
            if line.startswith('versionCode='):
                version_code = int(line.split('=')[1])
            elif line.startswith('versionName='):
                version_name = line.split('=')[1]
    
    if version_code is None or version_name is None:
        raise ValueError("Invalid version.properties file format!")
    
    return version_code, version_name


def parse_version(version_string):
    """Parse semantic version string into major, minor, patch."""
    match = re.match(r'^(\d+)\.(\d+)\.(\d+)$', version_string)
    if not match:
        raise ValueError(f"Invalid version format: {version_string}")
    
    return tuple(map(int, match.groups()))


def bump_version(current_version, bump_type):
    """Bump version based on type (major/minor/patch)."""
    major, minor, patch = parse_version(current_version)
    
    if bump_type == 'major':
        major += 1
        minor = 0
        patch = 0
    elif bump_type == 'minor':
        minor += 1
        patch = 0
    elif bump_type == 'patch':
        patch += 1
    else:
        # Assume it's a specific version
        if re.match(r'^\d+\.\d+\.\d+$', bump_type):
            return bump_type
        else:
            raise ValueError(
                f"Invalid bump type: {bump_type}. "
                "Use 'major', 'minor', 'patch', or a specific version like '1.2.3'"
            )
    
    return f"{major}.{minor}.{patch}"


def write_version_file(file_path, version_code, version_name):
    """Write updated version.properties file."""
    content = f"""# Application Version Configuration
# This file is used for version management and can be modified by CI/CD scripts
# DO NOT manually edit this file unless you are releasing a new version

# Version code must be an integer that increases with each release
versionCode={version_code}

# Version name follows semantic versioning (MAJOR.MINOR.PATCH)
versionName={version_name}
"""
    
    with open(file_path, 'w') as f:
        f.write(content)


def main():
    if len(sys.argv) < 2:
        print("Error: Please specify version bump type or version number")
        print("Usage: python bump_version.py [major|minor|patch|<version>]")
        sys.exit(1)
    
    bump_type = sys.argv[1]
    version_file = Path(__file__).parent.parent / 'version.properties'
    
    try:
        # Read current version
        current_code, current_version = read_version_file(version_file)
        print(f"Current version: {current_version} (code: {current_code})")
        
        # Calculate new version
        new_version = bump_version(current_version, bump_type)
        new_code = current_code + 1
        
        print(f"New version: {new_version} (code: {new_code})")
        
        # Write updated version
        write_version_file(version_file, new_code, new_version)
        
        print("✅ Version updated successfully!")
        print(f"   {current_version} (code: {current_code}) -> {new_version} (code: {new_code})")
        
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == '__main__':
    main()




