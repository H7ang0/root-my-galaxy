# Nothing Galaxy Root

<img width="108" height="108" alt="sprout_icon_108" src="https://github.com/user-attachments/assets/2ba0e360-0876-489c-b256-f75df7589785" />

作者：H7ang0，基于 Root-My-Galaxy 开发。
交流群请加 H7ang0 微信，备注来意。

Nothing Galaxy Root 是 Galaxy S24 Ultra（国行）的一键 root 安装器，
仅支持以下设备与固件：

```text
model:   SM-S9280
firmware: S9280ZCS6DZF2 (CHC / 国行)
kernel:  6.1.145-android14-11-3254743-abS9280ZCS6DZF2
```

应用与设备偏移、native exploit payload、KernelSU 构建产物分离；
本版本不再从网络拉取支持清单，payload 直接内置在 APK 的 assets 中。

## 界面

安装界面为 iOS 越狱风格的终端控制台（黑色背景、彩色日志前缀、
ASCII 启动画面、闪烁光标）。

## 构建

Requirements:

- Android Studio JBR 21
- Android SDK 37
- Android NDK 28 or newer
- CMake 3.22.1

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug
```

输出:

```text
app/build/outputs/apk/debug/app-debug.apk
```

仅在您拥有或明确获准测试的设备上使用。
