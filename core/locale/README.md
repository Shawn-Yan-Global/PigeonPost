# Locale Module - 国际化模块（委托模式）

## 📦 模块概述

这个模块提供了完整的国际化支持，**无需继承任何基类**，使用**委托模式**和**扩展函数**实现松耦合设计。

## 🎯 设计理念

### ❌ 旧方案的问题
```kotlin
// 强制继承，耦合度高
class MyApplication : LocaleAwareApplication()
class MyActivity : LocaleAwareComponentActivity()
```

**问题：**
- ✗ 强制继承，破坏类层次结构
- ✗ 难以升级和维护
- ✗ 无法与其他基类配合使用

### ✅ 新方案：委托模式
```kotlin
// 灵活组合，低耦合
class MyApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.withLocale())  // 简单！
    }
}
```

**优势：**
- ✓ **零继承**：不破坏类层次结构
- ✓ **松耦合**：随时可以移除或替换
- ✓ **易升级**：独立升级不影响业务代码
- ✓ **灵活性**：可以与任何基类配合使用

## 🏗️ 架构设计

```
com.octopus.locale/
├── LocaleHelper.kt           # 工具类：语言保存/读取/应用
├── LocaleDelegate.kt         # 委托类：处理 attachBaseContext 逻辑
├── LocaleExtensions.kt       # 扩展函数：提供便捷 API
├── AppLocaleManager.kt       # 接口：语言管理规范
└── AppLocaleManagerImpl.kt   # 实现：语言管理具体实现
```

## 📖 使用方法

### 1. Application 国际化

```kotlin
class PigeonPostApplication : Application() {
    
    override fun attachBaseContext(base: Context) {
        // 方式1：使用扩展函数（推荐，最简洁）
        super.attachBaseContext(base.withLocale())
    }
    
    override fun onCreate() {
        super.onCreate()
        // 语言已自动应用
    }
}
```

### 2. Activity 国际化

#### 方式 1：使用扩展函数（推荐）

```kotlin
class MainActivity : ComponentActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        // 从 SharedPreferences 读取（同步）
        super.attachBaseContext(newBase.withLocale())
    }
}
```

#### 方式 2：使用扩展函数 + 自定义 Provider

```kotlin
class BaseActivity : ComponentActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        // 从 DataStore 读取（需要 runBlocking）
        val context = newBase.withLocale { ctx ->
            val repository = PreferencesRepository(ctx)
            runBlocking { repository.appLanguage.first() }
        }
        super.attachBaseContext(context)
    }
}
```

#### 方式 3：使用 LocaleDelegate（完全控制）

```kotlin
class MyActivity : ComponentActivity() {
    private val localeDelegate = LocaleDelegate()
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localeDelegate.attachBaseContext(newBase))
    }
}
```

### 3. 更改语言

```kotlin
// 在 ViewModel 或 Activity 中
suspend fun changeLanguage(languageCode: String) {
    // 方式1：使用扩展函数（推荐）
    context.setLanguageAndRestart(activity, languageCode)
}

// 或者手动控制
suspend fun changeLanguage(languageCode: String) {
    // 1. 保存到 DataStore
    preferencesRepository.setAppLanguage(languageCode)
    
    // 2. 重启应用
    AppLocaleManagerImpl.restartApp(activity)
}
```

### 4. 获取当前语言

```kotlin
// 使用扩展函数
val currentLanguage = context.getSavedLanguage()

// 或使用 LocaleHelper
val currentLanguage = LocaleHelper.getSavedLanguageCode(context)
```

## 🎨 API 概览

### 扩展函数 (LocaleExtensions.kt)

```kotlin
// 应用语言（从 SharedPreferences 读取）
Context.withLocale(): Context

// 应用语言（自定义 provider）
Context.withLocale(languageProvider: (Context) -> String): Context

// 保存语言并重启
Context.setLanguageAndRestart(activity: Activity, languageCode: String)

// 获取保存的语言
Context.getSavedLanguage(): String
```

### LocaleDelegate

```kotlin
// 处理 attachBaseContext
fun attachBaseContext(base: Context): Context

// 处理 attachBaseContext（自定义 provider）
fun attachBaseContext(base: Context, languageProvider: (Context) -> String): Context
```

### LocaleHelper

```kotlin
// 同步读取语言代码
fun getSavedLanguageCode(context: Context): String

// 同步保存语言代码
fun saveLanguageCode(context: Context, languageCode: String)

// 应用语言到 Context
fun applyLocale(context: Context, languageCode: String, countryCode: String? = null): Context

// 创建 Locale
fun createLocale(languageCode: String, countryCode: String? = null): Locale
```

### AppLocaleManager

```kotlin
interface AppLocaleManager {
    fun getSavedLanguageCode(context: Context): String
    fun saveLanguageCode(context: Context, languageCode: String)
    fun attachBaseContextWithLocale(context: Context, languageCode: String, countryCode: String? = null): Context
    fun restartApp(activity: Activity)
}
```

## 🔄 完整流程

### 用户更改语言：

1. **用户选择语言**
2. **保存并重启**
   ```kotlin
   context.setLanguageAndRestart(activity, "zh")
   ```

3. **应用重启后**
   - `Application.attachBaseContext()` → `base.withLocale()`
   - 从 SharedPreferences 读取语言
   - 应用到 Application Context
   - 所有组件自动使用新语言 ✅

## 🌟 使用示例

### 示例 1：简单的 Application

```kotlin
class MyApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.withLocale())
    }
}
```

### 示例 2：简单的 Activity

```kotlin
class MyActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withLocale())
    }
}
```

### 示例 3：带基类的 Activity

```kotlin
// 你的基类可以继承任何类
open class BaseActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withLocale { ctx ->
            runBlocking {
                PreferencesRepository(ctx).appLanguage.first()
            }
        })
    }
}

// 业务 Activity 继承你的基类
class MainActivity : BaseActivity() {
    // 语言已自动应用，无需额外代码
}
```

### 示例 4：语言设置界面

```kotlin
class LanguageSettingsActivity : ComponentActivity() {
    
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withLocale())
    }
    
    private fun changeLanguage(languageCode: String) {
        lifecycleScope.launch {
            // 方式1：使用扩展函数
            applicationContext.setLanguageAndRestart(this@LanguageSettingsActivity, languageCode)
            
            // 方式2：手动控制
            // repository.setAppLanguage(languageCode)
            // AppLocaleManagerImpl.restartApp(this@LanguageSettingsActivity)
        }
    }
}
```

## ✨ 优势对比

| 特性 | 旧方案（继承） | 新方案（委托） |
|------|------------|------------|
| **耦合度** | ❌ 高 - 强制继承 | ✅ 低 - 委托模式 |
| **灵活性** | ❌ 限制基类选择 | ✅ 可与任何基类配合 |
| **可维护性** | ❌ 难以升级 | ✅ 独立升级 |
| **代码侵入性** | ❌ 修改类层次 | ✅ 一行代码接入 |
| **学习成本** | ❌ 需理解基类 | ✅ 简单扩展函数 |
| **测试性** | ❌ 需要测试基类 | ✅ 易于单元测试 |

## 🔧 迁移指南

### 从旧的继承方案迁移

#### Before（旧方案）:
```kotlin
class MyApplication : LocaleAwareApplication() {
    override fun onCreate() {
        super.onCreate()
    }
}

class MyActivity : LocaleAwareComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
```

#### After（新方案）:
```kotlin
class MyApplication : Application() {  // 改回 Application
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base.withLocale())  // 添加一行
    }
    
    override fun onCreate() {
        super.onCreate()  // 保持不变
    }
}

class MyActivity : ComponentActivity() {  // 改回 ComponentActivity
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withLocale())  // 添加一行
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)  // 保持不变
    }
}
```

## 📝 注意事项

1. **SharedPreferences 键名**
   - 使用 `LocaleHelper.PREFS_NAME` = "locale_prefs"
   - 使用 `LocaleHelper.KEY_LANGUAGE` = "app_language"

2. **同步 vs 异步**
   - `attachBaseContext` 必须使用同步读取
   - 推荐使用 SharedPreferences（LocaleHelper 已封装）
   - 如需使用 DataStore，必须 `runBlocking`

3. **重启应用**
   - 语言更改后必须重启应用
   - 使用 `ProcessPhoenix.triggerRebirth()` 实现干净重启

## 🎯 最佳实践

1. **Application**: 直接使用 `base.withLocale()`
2. **Activity 基类**: 使用 `newBase.withLocale { ... }` 自定义 provider
3. **业务 Activity**: 继承你的基类，自动获得语言支持
4. **语言更改**: 使用 `context.setLanguageAndRestart()`

## 📚 相关文档

- [I18N Implementation](../../docs/I18N_IMPLEMENTATION.md)
- [I18N Best Practices](../../docs/I18N_BEST_PRACTICES.md)
