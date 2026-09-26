# 简约台球 (PoolWatch) – Oppo Watch 2 / Wear OS 轻量台球游戏

专为 **Oppo Watch 2**（Snapdragon Wear 4100 / 1GB RAM）及同类 Wear OS 手表设计的超轻量二维台球小游戏。

## 特点

- **极低内存占用**：纯 Canvas 绘制，无位图、无第三方物理引擎、无 Compose
- **省电流畅**：静止时自动降频到 ~25 FPS，运动时 60 帧目标；所有球停止后几乎不耗 CPU
- **简约黑白风格**：只有黑球 + 白球（母球），平面背景，无复杂纹理
- **完整操作**：手指拖动白球 → 实时显示击球角度 + 力度 → 松手击球
- **可直接 GitHub 编译**：标准 Android Gradle 项目，Android Studio 打开即可构建

## 适配设备

- Oppo Watch 2 (42mm / 46mm) – ColorOS Watch / Wear OS
- 其他 Wear OS 2.x ~ 4.x 手表（minSdk 28）
- 也可在普通手机上运行（调试用）

## 如何编译

### 方法一：Android Studio（推荐）

1. 安装 [Android Studio](https://developer.android.com/studio)（建议 Hedgehog 或更新）
2. `File → Open` 选择本项目根目录
3. 等待 Gradle 同步完成
4. 连接手表（开启开发者选项 + ADB 调试）或使用 Wear OS 模拟器
5. 点击 Run 或 `./gradlew :app:assembleDebug`

### 方法二：GitHub Actions（本项目已配置）

把整个项目上传到 GitHub 后，进入 **Actions → Build Android APK**，点击 **Run workflow**（或者直接 push 一次代码）。

GitHub Actions 会自动：

1. 使用 JDK 17
2. 使用 Gradle 8.2.2
3. 编译 `:app:assembleDebug`
4. 将 `app-debug.apk` 上传为名为 `poolwatch-debug-apk` 的 Artifact

下载 Artifact 后即可得到 APK。

### 方法三：命令行

本项目为了让 GitHub Actions 不依赖本地 Gradle，Workflow 直接由 `gradle/actions/setup-gradle` 安装 Gradle 8.2.2。因此压缩包没有强制加入 Gradle Wrapper。

如果你本机已经安装 Gradle 8.2.2 和 Android SDK，可运行：

```bash
gradle :app:assembleDebug
# APK 输出在 app/build/outputs/apk/debug/app-debug.apk
```

生成的 APK 通常 **< 1.5 MB**（开启 minify 后更小）。

## 操作说明

1. 游戏启动后白球在左侧，黑球在右侧
2. **所有球静止时**，用手指按住白球并拖动
3. 拖动方向 = 击球方向的反方向（类似真实球杆后拉）
4. 拖动距离决定力度，屏幕会显示虚线瞄准线和力度圈
5. 松手即可击球
6. 黑球进袋得分，白球进袋会自动复位
7. 全部黑球进袋即通关（可重启应用再玩）

## 项目结构

```
OppoWatchPoolGame/
├── app/
│   ├── build.gradle          # 模块构建（minSdk 28, 开启 minify）
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/example/poolwatch/
│   │   │   ├── MainActivity.kt
│   │   │   ├── GameView.kt     # 核心游戏循环 + 物理 + 绘制
│   │   │   └── Ball.kt         # 轻量球对象
│   │   └── res/values/strings.xml
├── .github/
│   └── workflows/
│       └── build.yml       # GitHub Actions 自动编译 APK
├── build.gradle
├── settings.gradle
└── README.md
```

## 技术要点（为何省内存/省电）

| 技术 | 说明 |
|------|------|
| SurfaceView + 自建线程 | 避免 View 系统开销 |
| 纯数学碰撞 | 无 Box2D / Dyn4j 等库 |
| 无 Bitmap | 全部 `drawCircle` / `drawRect` |
| 动态帧率 | 静止时 sleep 40ms，运动时 16ms |
| 速度阈值 | 极小速度直接置 0，减少无效计算 |
| ProGuard + shrinkResources | Release 包进一步瘦身 |

## 已知限制（有意为之）

- 球数量少（1 白 + 6 黑），适配小屏幕
- 无旋转摩擦力、无复杂旋转（保持计算量极低）
- 无音效（手表喇叭弱且费电）
- 袋口检测简单圆形

## License

MIT – 可自由修改、发布。欢迎在 GitHub 上 Fork 后针对自己的手表调参（球半径、摩擦力、力度系数等）。

---

**Enjoy the minimalist pool on your wrist!**
