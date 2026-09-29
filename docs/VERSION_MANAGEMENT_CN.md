# 版本管理快速指南

## 🎯 设计思路

将应用版本信息（versionCode 和 versionName）从 `libs.versions.toml` 独立出来，放到专门的 `version.properties` 文件中。

### 为什么这样设计？

1. **职责分离**: `libs.versions.toml` 专注于依赖管理，`version.properties` 专注于版本管理
2. **CI/CD 友好**: 独立文件更容易被自动化脚本修改
3. **避免冲突**: 减少合并冲突的可能性
4. **简单明了**: 配置更清晰，易于理解和维护

---

## 📁 配置文件

### version.properties
```properties
# 版本代码（必须递增的整数）
versionCode=4

# 版本名称（语义化版本）
versionName=1.4.0
```

**位置**: 项目根目录 `/version.properties`

---

## 🚀 快速使用

### 方式 1: 手动修改
直接编辑 `version.properties` 文件

### 方式 2: 使用 Shell 脚本
```bash
# 修复版本（1.4.0 → 1.4.1）
./scripts/bump_version.sh patch

# 次要版本（1.4.0 → 1.5.0）
./scripts/bump_version.sh minor

# 主要版本（1.4.0 → 2.0.0）
./scripts/bump_version.sh major

# 指定版本
./scripts/bump_version.sh 2.0.0
```

### 方式 3: 使用 Python 脚本
```bash
python3 scripts/bump_version.py patch
python3 scripts/bump_version.py minor
python3 scripts/bump_version.py major
python3 scripts/bump_version.py 2.0.0
```

---

## 🤖 CI/CD 集成示例

### GitHub Actions
```yaml
- name: Bump version
  run: ./scripts/bump_version.sh patch

- name: Commit version
  run: |
    git add version.properties
    git commit -m "chore: bump version"
    git push
```

### GitLab CI
```yaml
bump_version:
  script:
    - ./scripts/bump_version.sh $VERSION_TYPE
    - git add version.properties
    - git commit -m "chore: bump version"
```

### Jenkins
```groovy
sh './scripts/bump_version.sh patch'
sh 'git add version.properties'
sh 'git commit -m "chore: bump version"'
```

---

## 📦 如何读取版本？

版本信息在构建时自动从 `version.properties` 读取到 `app/build.gradle.kts` 中：

```kotlin
// 自动加载配置
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

## ✅ 版本号规则

遵循 [语义化版本规范](https://semver.org/lang/zh-CN/)：

- **MAJOR (主版本号)**: 不兼容的 API 修改
- **MINOR (次版本号)**: 向下兼容的功能性新增
- **PATCH (修订号)**: 向下兼容的问题修正

**示例**:
- `1.4.0` → `1.4.1` (bug 修复)
- `1.4.0` → `1.5.0` (新功能)
- `1.4.0` → `2.0.0` (重大更新)

---

## 🔍 查看当前版本

```bash
# 方式 1: 直接查看文件
cat version.properties

# 方式 2: 提取版本号
grep versionName version.properties | cut -d'=' -f2

# 方式 3: 提取版本代码
grep versionCode version.properties | cut -d'=' -f2
```

---

## 📚 完整文档

详细使用指南请参考: [VERSION_MANAGEMENT.md](./VERSION_MANAGEMENT.md)

---

## 💡 最佳实践

1. **每次发布前更新版本**: 确保 versionCode 递增
2. **使用脚本自动化**: 避免手动修改带来的错误
3. **创建 Git 标签**: 每次发版后打标签便于追踪
4. **规范提交信息**: 使用 `chore: bump version to x.x.x`
5. **CI/CD 集成**: 在发布流程中自动更新版本

---

## 🆘 常见问题

**Q: 为什么要独立出来？**  
A: 便于 CI/CD 脚本修改，避免影响依赖管理，职责更清晰。

**Q: versionCode 一定要递增吗？**  
A: 是的，Google Play 要求新版本的 versionCode 必须大于旧版本。

**Q: 脚本支持哪些操作系统？**  
A: Shell 脚本支持 Linux/macOS，Python 脚本跨平台支持。

**Q: 如何在代码中获取版本号？**  
A: 使用 `PackageManager.getPackageInfo()` API。

**Q: 版本文件丢失怎么办？**  
A: 构建会失败并提示错误，从 Git 历史恢复或手动创建。




