# 信鸽 (PigeonPost) 📮

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-13%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-purple.svg)](https://kotlinlang.org)
[![Version](https://img.shields.io/badge/Version-1.0.0-orange.svg)](https://github.com/Shawn-Yan-Global/PigeonPost)

**中文文档** | [English](README.md)

一个用于监听短信并根据自定义模板自动转发到邮箱的 Android 应用程序。

## ✨ 功能特性

### 🎯 核心功能
- **短信监听**：持续监听接收到的短信
- **邮件转发**：自动将匹配的短信转发到配置的邮箱
- **模板配置**：支持关键词匹配、区分大小写、AND/OR 逻辑
- **邮箱配置**：SMTP 服务器配置，提供快捷预设（Gmail、QQ邮箱、163邮箱）
- **转发记录**：查看短信记录，支持手动重试，下拉刷新

### 🚀 高级功能
- **活跃时段**：配置高优先级监听时段（如每日 8:00-9:00）
- **权限监控**：实时权限状态监控与告警
- **自动启动**：设备重启或应用更新后自动启动
- **日志管理**：查看、导出和自动清理日志，并明确提示清理实际删除了多少个文件
- **记录管理**：自动清理旧记录，可配置保留时长
- **国际化**：完整支持中英文
- **无过滤模式**：可选择转发所有短信而不过滤

### 🎨 用户体验
- **Material Design 3**：现代化精美 UI
- **深色模式**：自动适配系统主题
- **语言设置**：应用内语言切换
- **通知控制**：细粒度通知类型控制
- **快捷操作**：从通知栏停止监听
- **保存确认**：会覆盖已保存配置的设置项，提交前先询问

## 🏗️ 架构设计

项目为多模块 Gradle 构建。所有模块的 Kotlin 源码统一放在 `src/main/kotlin` 下，并使用共享的
`ktlint` 约定插件保持格式一致。

```
PigeonPost/
├── app/                                  # 应用模块
│   └── src/main/kotlin/com/octopus/pigeon/post/
│       ├── PigeonPostApplication.kt      # 启动、模块装配、设置桥接
│       ├── service/
│       │   ├── SmsMonitorService.kt      # 短信监听前台服务
│       │   ├── PermissionMonitorService.kt
│       │   ├── SmsMatchingService.kt     # 模板匹配
│       │   ├── TemplateService.kt
│       │   ├── EmailService.kt           # SMTP 发送
│       │   ├── CleanupService.kt         # 一次性清理请求
│       │   ├── CleanupWorker.kt          # 定时清理 (WorkManager)
│       │   ├── SmsKeepAliveWorker.kt     # 保活兜底 (WorkManager)
│       │   └── CleanupActions.kt         # 共享清理逻辑
│       ├── receiver/
│       │   ├── SmsReceiver.kt            # 短信接收
│       │   └── BootReceiver.kt           # 开机启动
│       ├── data/
│       │   ├── database/                 # Room 数据库、DAO、转换器
│       │   ├── repository/               # 数据仓库
│       │   └── model/                    # 数据模型
│       ├── ui/
│       │   ├── activity/                 # Activity
│       │   ├── screen/                   # Compose 屏幕
│       │   ├── viewmodel/                # ViewModel
│       │   ├── component/                # 公共 Compose 组件
│       │   └── theme/                    # 主题配置
│       ├── permission/                   # 权限管理
│       └── util/                         # 工具类
├── core/
│   ├── logging/                          # 日志 + 日志管理 UI
│   ├── locale/                           # 运行时语言切换
│   └── ui/                               # 共享设计规范 (AppSpacing)
├── buildSrc/                             # ktlint 约定插件
└── gradle/                               # 版本目录、Wrapper
```

### 📦 模块说明

#### 主应用模块 (`:app`)
包含以下核心组件：
- 使用 Jetpack Compose 构建的 UI 组件
- 短信监听和邮件转发的后台服务
- 使用 Room 和 DataStore 的数据持久化
- 权限管理和监控

#### 核心模块 (Core)

**日志模块** (`:core:logging`)
- 基于 Log4j 的结构化日志系统
- 日志文件管理、查看与导出
- 手动清理与定时清理
- 通过 `LoggingConfig.initialize()` 注入到应用

**国际化模块** (`:core:locale`)
- 集中式语言管理
- 运行时语言切换，配置持久化在 DataStore
- `LocaleAwareComponentActivity` 基类，负责应用已保存的语言

**UI 模块** (`:core:ui`)
- 共享设计规范，目前为 `AppSpacing`
- 避免在业务模块中硬编码间距

## 🔧 技术栈

### 核心技术
| | |
|---|---|
| **语言** | Kotlin 2.4.20 |
| **构建系统** | Gradle 9.7.1 + Kotlin DSL，AGP 9.4.1 |
| **JDK** | 21 |
| **编译 SDK** | 37 |
| **最低 SDK** | 33 (Android 13) |
| **目标 SDK** | 37 |

### UI 框架
- **Jetpack Compose**（BOM 2026.09.00）与 Material Design 3
- **Compose Navigation** 2.10.2

### 架构组件
- **ViewModel**：MVVM 模式
- **StateFlow**：响应式状态管理
- **Room** 2.8.5：本地数据库
- **DataStore**：配置存储
- **KSP** 2.3.6：注解处理

### 后台处理
- **Coroutines**：进程内异步任务
- **WorkManager**：负责所有需要跨进程死亡与重启存活的任务：
  - `SmsKeepAliveWorker` 在系统杀掉监听服务后将其恢复
  - `CleanupWorker` 每 24 小时执行一次日志与记录清理
- **前台服务**（`dataSync` + `specialUse`）：保证短信监听可靠运行

项目不再使用 `AlarmManager` 或 `JobScheduler` 定时唤醒：原有的保活链路已被移除，因为 WorkManager
已经能更可靠地覆盖这两种场景。

### 邮件
- **JavaMail**（Android 兼容版本）通过 SMTP 发送

### 其他库
- **Log4j**：日志
- **Process Phoenix**：应用重启

## 🏗️ 构建

```bash
# 全量：编译 + lint + 单元测试（CI 应执行此命令）
./gradlew build

# Debug 构建
./gradlew assembleDebug

# Release 构建（R8 混淆压缩 + 资源压缩）
./gradlew :app:assembleRelease

# 单元测试
./gradlew :app:testDebugUnitTest

# 仅 lint
./gradlew :app:lintDebug
```

`ktlint` 会在每个 `assemble` 和 `test` 任务前自动执行，无需手动触发。如需显式运行：

```bash
./gradlew ktlintCheck    # 检查规范问题
./gradlew ktlintFormat   # 自动修复
```

## 📱 快速开始

### 环境要求
- 支持 AGP 9.4.1 的 Android Studio
- JDK 21
- Android 13 或更高版本的设备或模拟器

### 安装步骤

1. 克隆仓库
```bash
git clone https://github.com/Shawn-Yan-Global/PigeonPost.git
cd PigeonPost
```

2. 用 Android Studio 打开项目

3. 同步 Gradle 并构建项目

4. 在设备或模拟器上运行

### 配置说明

1. **授予权限**：允许短信读取和通知权限
2. **配置模板**：设置关键词和匹配逻辑
3. **配置邮箱**：设置 SMTP 服务器和凭据
4. **可选 - 活跃时段**：配置高优先级监听时段
5. **启动服务**：开启短信监听

### 数据存放位置

| 内容 | 位置 | 保护方式 |
| --- | --- | --- |
| 邮箱密码、收发件地址、关键词、HTML 模板 | `DataStore` | AES/GCM 加密，密钥保存在 Android Keystore |
| SMTP 主机、端口、各开关、活跃时段、debug 密码校验值 | `DataStore` | 明文：这些都不是敏感信息 |
| 短信记录：发件人、正文、发送失败原因 | Room 数据库（`pigeon_post_database`） | AES/GCM 加密，密钥保存在 Android Keystore |
| 发件人与正文的盲索引 | Room 数据库 | 带密钥的 HMAC，用于维持唯一索引和去重；只能看出两条记录是否同源或同文 |
| 日志文件 | `files/logs/` | debug 构建明文；release 构建 AES/GCM 加密。详见[日志治理](#日志治理) |
| 诊断包 | `cache/`，分享后删除 | ZipCrypto，密码为应用 ID（即包名） |
| 日志导出包 | `cache/`，分享后删除 | ZipCrypto，密码为应用 ID（即包名） |

加密后的字段只有应用本身能读取：密钥始终留在 Keystore 中，从不落盘，所以即使把 DataStore 文件从设备上拷走，也无法还原邮箱密码。端口、开关和活跃时段刻意不加密，因为加密它们只会增加开销和复杂度，并不能保护到任何东西。

锁屏时转发依然正常工作。Keystore 密钥刻意没有设置用户认证要求：接收短信时需要在没有界面的后台线程里读取凭据并完成转发。若这里要求认证，验证码到了也不会有任何反应，直到用户解锁手机。

盲索引是唯一需要如实说明的取舍。唯一索引匹配和去重需要"能与自身相等"的值，而随机化加密做不到这点，因此发件人和正文旁边各存了一份带密钥的 HMAC。持有数据库的人可以看出两条记录是否来自同一发件人、是否为同一正文，但无法得知具体内容。去掉盲索引可以连这一点也消除，代价是重复短信会无上限地堆积。

设置项会在首次启动时自动迁移：字段加密出现之前写入的值会照常读取，并在后台改写为加密格式，用户无需重新配置。**短信历史不会保留**——这些行原本是明文，若要转换就必须把所有已存短信读进内存。旧数据库会被丢弃重建，效果等同于重装应用。

两种导出包都使用 ZipCrypto 而非 AES，这样系统文件管理器和常见的桌面解压工具无需额外软件即可打开。它能防止导出包被误转发后被随手查看，但并不适合用来抵挡刻意想要破解内容的人。密码是应用 ID，分享时会在分享面板中显示。

### 日志治理

这是整个应用里最容易被"不知道背景的新贡献者"改坏的部分，所以规则写在这里，而不是散落在某个恰好写了第一行日志的文件里。

**两种构建。** 日志行为由一个开关 `BuildConfig.ENCRYPT_LOGS` 控制，定义在 `app/build.gradle.kts`：

| 构建 | 磁盘上的日志文件 | 日志中的短信正文 |
| --- | --- | --- |
| `debug` | 明文，可直接 `cat` 或看 Logcat | 原样写入 |
| `release` | AES/GCM 加密，密钥在 Keystore | 绝不写入，只记录特征摘要 |

可以在 `local.properties` 里覆盖：

```properties
# 默认：debug 明文，release 加密
pigeonpost.logs.encrypt=true
```

在 debug 构建上设 `true` 是复现客户环境的正规做法：文件被加密了，但应用内所有读取入口依然能解密。CI 两种设置都不需要；release 默认加密，除非构建显式要求不加密。

**为什么 release 的日志要加密。** 日志文件在别人手机上停留于应用私有目录，直到这个人主动分享。私有目录其他应用读不到，但 root 设备读得到、备份读得到、拿到数据目录副本的人也读得到。而日志恰恰是最可能被转手交给别人的那一个文件，所以正式版本在磁盘上必须是不透明的。

加密是**逐行**而不是逐文件。Log4j 的 file appender 只接受文件名并直接往里写字节，想挂钩子就得先写明文；而整文件加密意味着每次追加都要重写整个文件。逐行 AES/GCM 后以 base64 追加，追加和轮转都很便宜，而且一行读不出来只损失一行，不会毁掉整个文件。

密钥保存在平台 Keystore 中，别名与保护用户凭据的那把**不同**，所以日志文件不能被用来反推邮箱密码。密钥刻意不要求用户认证：接收短信时必须在锁屏状态下完成读取和转发，这里加认证会导致验证码到了却毫无反应，直到用户解锁手机。

**为什么 release 日志永远不含短信正文。** 它记录的是**特征**：

```
📱 SMS received from id:9f3a2b1c — len=18,digits=6,cjk=8,digit_run=6
```

长度、字符类别计数、最长连续数字段。这能把验证码和普通文本区分开——而这是摘要加长度做不到的——并且足以回答"关键词为什么没匹配上"或"短信是不是被截断了"，同时正文本身不可还原。相同内容仍会产生相同摘要，因此同一会话内的事件依然可以互相关联。

**为什么导出是在设备上解密的。** 加密密钥绑定在写入它的那台设备上。一个被复制到 issue 里、或作为附件发出去的加密文件，任何人都解不开。所以 `Export logs` 会在客户手机上逐行解密，再把明文写进导出包。磁盘上的文件全程保持加密，而你收到的导出包用应用 ID 作为密码即可打开。

**如果你要新增一行日志。** 请使用 `PigeonLogRedactor`，不要直接拼接值。标识类用 `tag()`（稳定且不可逆），短信内容用 `body()` 或 `describe()`。通过字符串插值进入日志的值，是唯一能推翻上述所有机制的写法，而且它既不会导致构建失败，也不会在 code review 中被看出来。

### Debug 菜单密码

Debug 菜单可以从侧边栏的 Advanced options 进入，或者长按版本号三次，两种方式都会要求密码。

密码只需要输入一次。输入之后菜单会保持解锁，重启应用后依然可以直接进入，不会每次都再问一遍。
修改密码会重新上锁，下次进入需要使用新密码。清除应用数据或卸载应用同样会恢复为上锁状态。

密码写在 `local.properties` 里，该文件已被 git 忽略：

```properties
pigeonpost.debug.password=在这里填你的密码
```

构建时会计算哈希并只把哈希打进安装包，所以 APK 里不含明文，无法用 `strings` 读出密码。
如果缺少这个配置项，构建仍然成功，会为本次构建随机生成一个密码并打印在输出开头。
因此 CI 不需要这个配置项，也不会打包一个所有人都知道的密码。

盐值放在 `gradle.properties` 的 `pigeonpost.debug.password.salt`。它不是机密，
但发布后不要更改：应用在校验时会用盐重新计算，盐一旦变化，已经设置过自己密码的用户就无法再进入。

## 📋 权限说明

应用需要以下权限：

| 权限 | 用途 |
|-----|------|
| `READ_SMS` | 读取短信内容用于转发 |
| `RECEIVE_SMS` | 接收短信广播 |
| `INTERNET` | 通过 SMTP 发送邮件 |
| `FOREGROUND_SERVICE` | 保持监听服务运行 |
| `FOREGROUND_SERVICE_DATA_SYNC` | 前台服务类型声明 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | 短信/权限监听的前台服务类型 |
| `RECEIVE_BOOT_COMPLETED` | 开机后自动启动 |
| `VIBRATE` | 检测到权限问题时振动提示 |
| `POST_NOTIFICATIONS` | 显示通知（Android 13+） |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 可靠的后台运行 |

`androidx.work` 会为自身的内部调度额外引入 `WAKE_LOCK` 与 `ACCESS_NETWORK_STATE`，应用本身并未直接使用。

## 🔐 隐私与安全

- **不收集数据**：所有数据仅保存在本机
- **本地存储**：短信记录和日志均存放在本地
- **Keystore 加密凭据**：邮箱密码、收发件地址、关键词和 HTML 模板均使用保存在 Android Keystore 中的密钥加密，仅凭 DataStore 文件无法还原
- **导出包加密**：诊断包和日志导出包都带密码保护，密码均为应用 ID（即包名）
- **诊断信息脱敏**：诊断包只报告配置的"形状"（计数与布尔值），不含地址、密码、关键词，也不含你的活跃时段
- **混淆加固**：Release 构建启用 R8，包含激进的代码压缩、控制流混淆和资源名混淆
- **开源透明**：代码完全公开，便于安全审计
- **无统计分析**：不含任何第三方追踪或统计

## 🤝 贡献指南

欢迎贡献代码！请提交 Pull Request。

1. Fork 本项目
2. 创建功能分支（`git checkout -b feature/AmazingFeature`）
3. 提交修改（`git commit -m 'Add some AmazingFeature'`）
4. 推送到分支（`git push origin feature/AmazingFeature`）
5. 创建 Pull Request

提交前请确认 `./gradlew build` 通过。

## 📄 开源许可

本项目基于 Apache License 2.0 开源，详见 [LICENSE](LICENSE) 文件。

```
Copyright 2025-2026 PigeonPost Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## 👥 作者

- **PigeonPost 团队** - *初始版本*

## 🙏 致谢

- 感谢所有为改进本项目做出贡献的开发者
- 基于 [Jetpack Compose](https://developer.android.com/jetpack/compose) 构建
- 邮件功能由 [JavaMail API](https://javaee.github.io/javamail/) 提供支持

## 📞 支持

如有疑问或问题，欢迎：
1. 查阅 [Issues](https://github.com/Shawn-Yan-Global/PigeonPost/issues) 页面
2. 如果没有相同问题，请创建新 Issue
3. 详细描述你的问题

---

**用 ❤️ 制作，给需要可靠短信转发的 Android 开发者**
