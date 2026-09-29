# Version Management Guide

## Overview

This project uses a separate `version.properties` file to manage application versions, making it easier for CI/CD automation and version control.

---

## 📁 File Structure

```
PigeonPost/
├── version.properties           # Version configuration file (standalone)
├── scripts/
│   ├── bump_version.sh         # Version bump script
│   └── bump_version.py         # Same thing in Python
```

---

## 📝 version.properties Explanation

This file contains two key configurations:

- **versionCode**: Integer that must increment with each release (required by Google Play Store)
- **versionName**: Semantic version number (MAJOR.MINOR.PATCH)

Example:
```properties
versionCode=4
versionName=1.4.0
```

---

## 🔧 Manual Version Update

Directly edit the `version.properties` file:

```properties
versionCode=1
versionName=1.0.0
```

**Note**: 
- versionCode must increment
- versionName follows [Semantic Versioning](https://semver.org/)

---

## 🚀 Automated Version Update with Scripts

### Patch Version (Bug fixes)
```bash
./scripts/bump_version.sh patch
# 1.4.0 → 1.4.1
```

### Minor Version (New features)
```bash
./scripts/bump_version.sh minor
# 1.4.0 → 1.5.0
```

### Major Version (Breaking changes)
```bash
./scripts/bump_version.sh major
# 1.4.0 → 2.0.0
```

### Specific Version
```bash
./scripts/bump_version.sh 2.0.0
# Set directly to 2.0.0
```

### Python Script (Cross-platform)
```bash
python3 scripts/bump_version.py patch
python3 scripts/bump_version.py minor
python3 scripts/bump_version.py major
python3 scripts/bump_version.py 2.0.0
```

---

## 🤖 CI/CD Integration

### GitHub Actions Example

```yaml
# .github/workflows/bump-version.yml
name: Bump Version

on:
  workflow_dispatch:
    inputs:
      version_type:
        description: 'Version type or explicit version'
        required: true
        default: 'patch'

jobs:
  bump:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Bump version
        run: |
          chmod +x scripts/bump_version.sh
          ./scripts/bump_version.sh "${{ github.event.inputs.version_type }}"
      - name: Commit
        run: |
          git config user.name  "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git add version.properties
          git commit -m "chore: bump version"
          git push
```

Copy this into your own `.github/workflows/` directory, then:

**Manual Workflow Trigger**:
1. Go to GitHub Actions page
2. Select "Bump Version" workflow
3. Click "Run workflow"
4. Choose version type (patch/minor/major)
5. Run

### GitLab CI Example

```yaml
bump_version:
  stage: prepare
  script:
    - chmod +x scripts/bump_version.sh
    - ./scripts/bump_version.sh ${VERSION_TYPE}
    - git add version.properties
    - git commit -m "chore: bump version"
    - git push
  only:
    - main
```

### Jenkins Example

```groovy
stage('Bump Version') {
    steps {
        sh '''
            chmod +x scripts/bump_version.sh
            ./scripts/bump_version.sh patch
            git add version.properties
            git commit -m "chore: bump version"
            git push
        '''
    }
}
```

---

## 📦 Version Reading at Build Time

Version information is automatically loaded in `app/build.gradle.kts`:

```kotlin
// Load version configuration from version.properties
val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties()
versionProps.load(FileInputStream(versionPropsFile))

android {
    defaultConfig {
        versionCode = versionProps["versionCode"].toString().toInt()
        versionName = versionProps["versionName"].toString()
    }
}
```

---

## ✅ Best Practices

### 1. Version Number Rules
- **Major (X.0.0)**: Incompatible API changes
- **Minor (0.X.0)**: Backward-compatible new features
- **Patch (0.0.X)**: Backward-compatible bug fixes

### 2. Commit Message Convention
```bash
git commit -m "chore: bump version to 1.4.1"
```

### 3. Create Git Tags
```bash
git tag -a v1.4.1 -m "Release version 1.4.1"
git push origin v1.4.1
```

### 4. Release Workflow
1. Complete feature development
2. Update version: `./scripts/bump_version.sh patch`
3. Commit changes: `git commit -am "chore: bump version to 1.4.1"`
4. Create tag: `git tag v1.4.1`
5. Push code: `git push && git push --tags`
6. Build release: `./gradlew assembleRelease`

---

## 🔍 Check Current Version

### View Version File
```bash
cat version.properties
```

### Extract Version in Code
```kotlin
val packageInfo = packageManager.getPackageInfo(packageName, 0)
val versionName = packageInfo.versionName
val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    packageInfo.longVersionCode
} else {
    packageInfo.versionCode.toLong()
}
```

---

## 🛠️ Troubleshooting

### Script Permission Denied
```bash
chmod +x scripts/bump_version.sh
chmod +x scripts/bump_version.py
```

### Git Commit Failed
```bash
git config user.name "Your Name"
git config user.email "your.email@example.com"
```

### Version File Not Found
Ensure `version.properties` exists in project root:
```bash
ls -la version.properties
```

### Python Version Required
Python 3.6 or higher is required:
```bash
python3 --version
```

---

## 📚 Related Resources

- [Semantic Versioning](https://semver.org/)
- [Android Versioning](https://developer.android.com/studio/publish/versioning)
- [Gradle Best Practices](https://docs.gradle.org/current/userguide/publishing_gradle_module_metadata.html)

---

## 🌏 Language

- [English](./VERSION_MANAGEMENT.md) (Current)
- [中文文档](./VERSION_MANAGEMENT_CN.md)

---

## 💡 Design Philosophy

### Why Separate Version Configuration?

1. **Separation of Concerns**: Keep dependency management and version management separate
2. **CI/CD Friendly**: Simple property file format is easier to modify programmatically
3. **Reduced Conflicts**: Less merge conflicts in version control
4. **Clear Responsibility**: Each file has a single, well-defined purpose

### Advantages

| Feature | Before (toml) | After (properties) |
|---------|---------------|-------------------|
| Separation of Concerns | ❌ Mixed with dependencies | ✅ Standalone and clear |
| CI/CD Modification | ⚠️ Complex TOML parsing | ✅ Simple properties format |
| Merge Conflicts | ⚠️ Higher risk | ✅ Lower risk |
| Automation Scripts | ❌ None | ✅ Dual-script support |
| Documentation | ❌ None | ✅ Complete guides |

---

## 🎯 Quick Start

### First Time Setup

```bash
# 1. Grant execution permissions (one-time only)
chmod +x scripts/bump_version.sh
chmod +x scripts/bump_version.py

# 2. Test version bump
./scripts/bump_version.sh patch

# 3. View results
cat version.properties

# 4. Revert test (if needed)
git checkout version.properties
```

### Daily Usage

```bash
# Bug fix release
./scripts/bump_version.sh patch

# New feature release
./scripts/bump_version.sh minor

# Major release
./scripts/bump_version.sh major
```

---

## ❓ FAQ

**Q: Why separate from libs.versions.toml?**  
A: To make CI/CD modifications easier, avoid affecting dependency management, and provide clearer separation of concerns.

**Q: Must versionCode always increment?**  
A: Yes, Google Play requires new releases to have a higher versionCode than previous releases.

**Q: Which platforms do the scripts support?**  
A: Shell script works on Linux/macOS. Python script is cross-platform (Windows/Linux/macOS).

**Q: How to get version number in code?**  
A: Use `PackageManager.getPackageInfo()` API.

**Q: What if version.properties is lost?**  
A: Build will fail with an error. Restore from Git history or create manually.

**Q: Can I use both scripts?**  
A: Yes! Use whichever fits your environment better. They produce identical results.

---

## 📞 Support

For more information, see:
- Project README: [README.md](../README.md)
- Chinese Guide: [VERSION_MANAGEMENT_CN.md](./VERSION_MANAGEMENT_CN.md)
- Scripts README: [scripts/README.md](../scripts/README.md)
