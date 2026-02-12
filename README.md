# EPUB Reading

一个基于 **Android + Jetpack Compose** 的 EPUB 阅读器项目，目标是做出接近 iPhone 图书体验的中文小说阅读应用。

## 功能特性

- 支持从本地文件导入 `.epub`
- 书架展示与管理（浏览、删除）
- 自动解析书籍元数据与目录
- 阅读页支持章节内上下滚动阅读
- 支持章节切换、目录跳转
- 支持字体大小、主题、夜间模式
- 自动保存阅读进度（章节 + 滚动位置）

## 技术栈

- **Kotlin**
- **Jetpack Compose**（Material 3）
- **MVVM**（ViewModel + StateFlow）
- **Kotlin Coroutines / Flow**
- **kotlinx.serialization**
- **Gradle (Android)**

## 项目结构

```text
app/src/main/java/com/example/epubreader/
├─ core/                 # EPUB 解析、工具类
├─ data/                 # 数据模型与仓库实现
├─ domain/               # 业务模型与 UseCase
└─ presentation/         # UI、导航、ViewModel
```

## 本地运行

### 环境要求

- Android Studio（建议 Iguana 或更高）
- JDK 17
- Android SDK 34

### 构建与安装

```bash
# Windows PowerShell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

或直接在 Android Studio 点击 `Run 'app'`。

## 使用说明

1. 打开 App，点击右下角导入按钮选择 EPUB 文件  
2. 在书架点击书籍进入阅读  
3. 阅读页可上下滚动；底部按钮用于上一章/下一章  
4. 点击目录图标可跳转章节  

## 开发说明

- 项目仍在持续迭代中，后续会继续优化：
  - 目录匹配准确度
  - 阅读排版与分页体验
  - 更平滑的阅读动画与交互

## 免责声明

本项目仅用于学习与技术交流，请勿用于任何侵权用途。  

