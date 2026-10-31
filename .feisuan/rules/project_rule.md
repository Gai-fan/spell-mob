
# Minecraft Forge Mod 开发规范指南

## 一、项目基本信息

- **操作系统版本**：Windows 11
- **工作区路径**：`D:\mc.mod.test\thirdmod\spell_mob`
- **使用框架**：JDK 17.0.10 + Gradle
- **当前时间**：2026-02-20 12:34:03
- **代码作者**：86152
- **项目名称**：spell_mob
- **项目版本**：1.0.0

## 二、技术栈要求

- **主框架**：Minecraft Forge 1.20.1-47.2.20
- **语言版本**：Java 17
- **构建工具**：Gradle
- **核心依赖**：
  - `net.minecraftforge:forge:1.20.1-47.2.20`
  - `io.redspace.ironsspellbooks:irons_spellbooks:1.20.1-3.4.0.9` (API依赖)
  - `io.redspace.ironsspellbooks:irons_spellbooks:1.20.1-3.4.0.9` (运行时依赖)

## 三、项目目录结构

```
spell_mob/
├── gradle/                    # Gradle配置文件
├── libs/                      # 本地依赖库
├── run-data/                 # 运行时数据
├── src/
│   ├── generated/            # 自动生成的资源文件
│   │   └── resources/
│   ├── main/                # 主要源代码
│   │   ├── java/           # Java源代码
│   │   │   └── spellmob/  # 主包
│   │   │       ├── capability/  # 能力系统
│   │   │       ├── data/       # 数据管理
│   │   │       ├── events/     # 事件处理
│   │   │       ├── goal/       # 目标系统
│   │   │       └── util/      # 工具类
│   │   └── resources/      # 资源文件
│   │       ├── data/       # 数据包
│   │       │   └── minecraft/
│   │       │       └── spell_casters/
│   │       └── META-INF/   # 元信息
│   └── test/               # 测试代码
│       ├── java/
│       └── resources/
```

## 四、开发规范

### 1. 包结构规范

| 包名 | 用途说明 |
|------|----------|
| `spellmob.capability` | 能力系统实现 |
| `spellmob.data` | 数据管理相关 |
| `spellmob.events` | 事件处理系统 |
| `spellmob.goal` | AI目标系统 |
| `spellmob.util` | 工具类和辅助方法 |

### 2. 依赖管理规范

- **Iron's Spells 'n Spellbooks 依赖**：
  - API依赖使用 `compileOnly`，仅在编译时需要
  - 运行时依赖使用 `runtimeOnly`
  - 版本统一定义为 `irons_spells_version = "1.20.1-3.4.0.9"`

### 3. 构建配置规范

- **Gradle插件**：
  - `java-library`
  - `eclipse`
  - `idea`
  - `net.minecraftforge.gradle` (版本范围 [6.0,6.2))

- **Minecraft配置**：
  - 映射版本：`official` 1.20.1
  - 运行时工作目录：`run`

### 4. 仓库配置规范

| 仓库名称 | URL | 用途 |
|----------|-----|------|
| Iron's Maven - Release | `https://code.redspace.io/releases` | 发布版本 |
| Iron's Maven - Snapshots | `https://code.redspace.io/snapshots` | 快照版本 |
| Maven Central | `https://mavenCentral.org/` | 通用依赖 |
| Forge Maven | `https://maven.minecraftforge.net/` | Forge相关依赖 |

## 五、编码规范

### 1. 命名规范

| 类型 | 命名方式 | 示例 |
|------|----------|------|
| 类名 | UpperCamelCase | `SpellCapability` |
| 方法/变量 | lowerCamelCase | `getSpellPower()` |
| 常量 | UPPER_SNAKE_CASE | `MAX_SPELL_LEVEL` |
| 包名 | lowercase | `spellmob.util` |

### 2. 注释规范

- 所有公共类、方法和字段需添加 **Javadoc** 注释
- 使用中文注释，提高可读性
- 复杂逻辑需添加行内注释说明

### 3. 代码组织原则

| 原则 | 说明 |
|------|------|
| **单一职责** | 每个类只负责一个功能领域 |
| **开闭原则** | 对扩展开放，对修改关闭 |
| **依赖倒置** | 依赖抽象而非具体实现 |
| **接口隔离** | 使用小而专的接口 |

## 六、Forge Mod 开发特定规范

### 1. 能力系统 (Capability)

- 能力实现类必须注册到 Minecraft 的能力系统中
- 使用 `@CapabilityInject` 注解注入能力实例
- 能力提供者必须正确实现 `ICapabilityProvider` 接口

### 2. 事件处理 (Events)

- 事件处理类应使用 `@Mod.EventBusSubscriber` 注册
- 事件方法必须为 `static` 并带有 `@SubscribeEvent` 注解
- 优先级应合理设置，避免与其他模组冲突

### 3. AI 目标系统 (Goals)

- 自定义 AI 目标应继承 `Goal` 类
- 在合适的实体注册时添加 AI 目标
- 考虑使用 `GoalSelector` 管理多个目标

### 4. 数据管理 (Data)

- 数据结构应实现 `Serializable` 接口
- 使用 Minecraft 的数据包系统进行同步
- 考虑使用 `Capability` 持久化数据

## 七、调试与测试规范

### 1. 运行配置

- 客户端运行工作目录：`run`
- 服务器运行工作目录：`run`
- 游戏测试服务器工作目录：`run`

### 2. 测试规范

- 单元测试放在 `src/test/java` 目录
- 资源测试文件放在 `src/test/resources` 目录
- 使用 JUnit 5 进行单元测试

## 八、版本控制规范

- 项目版本定义在 `build.gradle` 中的 `version` 变量
- 使用语义化版本号 (MAJOR.MINOR.PATCH)
- 依赖版本应使用变量统一管理 (如 `irons_spells_version`)

## 九、发布规范

- 构建产物为 `jar` 文件，基础名称为 `spell_mob`
- 不推荐将依赖打包进 Mod Jar，应由用户在 `mods` 文件夹中提供
- 发布前需在多环境下测试兼容性
