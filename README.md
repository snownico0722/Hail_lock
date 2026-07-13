# 雹 Hail (防沉迷版)

雹是一款以**防沉迷与数字戒断**为核心目标的 Android 应用控制工具。本软件是基于原开源项目 [Hail (aistra0528/Hail)](https://github.com/aistra0528/Hail) 进行二次开发的精简版本。它通过 Android 系统的“设备所有者 (Device Owner)”权限，将成瘾性应用进行隐藏或暂停，帮助用户对抗电子沉迷，重建健康的数字生活习惯。

[GitHub Releases](https://github.com/snownico0722/Hail_lock/releases)

---

## 核心设计：数字戒断

雹通过将成瘾性应用“冻结”来实现防沉迷。冻结是指使**应用在日常状态下完全不可运行且不可见**。用户只能在确实需要时，通过输入防沉迷密码（PIN码）解冻应用，从而极大提高了使用娱乐、社交、短视频应用的物理门槛。

在“设备所有者”模式下，雹提供以下两种维度的戒断（冻结）手段：

### 1. 软件隐藏 (Hide) —— 视线阻断
被隐藏`hide`的应用在系统的桌面启动器与已安装应用列表中均被完全抹除。
* **防沉迷作用**：将容易让人沉迷的软件彻底“隐形”。“看不见便不会想去打开”，有助于用户脱离对特定图标的无意识点击依赖。
* **说明**：此状态下，软件包几乎处于卸载状态，无法使用，但并没有删除数据或实际的软件包文件。

### 2. 应用暂停 (Suspend) —— 警示与冷却
被暂停`suspend`的应用在桌面呈现为灰度置灰图标。
* **防沉迷作用**：通知会被全部隐藏，试图打开时系统将弹出警示对话框告知“当前应用处于暂停状态”。这能够在使用时起到主动的物理和心理警示，促使用户停下来思考，减少多巴胺成瘾。
* **注意**：暂停仅限制前台与用户的交互，不会限制其在后台运行。

### 3. 屏蔽系统分身 (防绕过)
在激活“设备所有者 (Device Owner)”后，雹会自动强制开启“单用户模式”限制：
* **防沉迷作用**：系统会自动禁止创建新的多用户空间，并禁止切换至其他多用户（即屏蔽“手机分身/第二空间”）。这能强力防止用户通过在系统分身中安装并打开成瘾应用来绕过防沉迷限制。

---

## 激活指南：设备所有者 (Device Owner)

要使“隐藏”和“暂停”功能生效，您必须通过 ADB 将“雹”设置为您设备的设备所有者。

### 1. 准备工作
* 确保您的电脑上已配置好 ADB（Android 调试桥）环境。
* 在手机的“开发者选项”中启用“USB 调试”。

### 2. 设置设备所有者
1. 在手机的“系统设置 > 账户”中，**移除所有已登录的账户**（如 Google 账号、小米账号、华为账号等，设置成功后可重新登录）。
2. 将手机连接到电脑，在终端或命令行中执行以下命令：
   ```shell
   adb shell dpm set-device-owner com.aistra.hail/.receiver.DeviceAdminReceiver
   ```
3. 设置成功后，终端会输出以下信息：
   ```text
   Success: Device owner set to package com.aistra.hail
   Active admin set to component {com.aistra.hail/com.aistra.hail.receiver.DeviceAdminReceiver}
   ```

### 3. 高级防护：禁止卸载目标冻结应用 (防绕过)
为了防止在数字戒断期间，成瘾性应用被直接卸载并重新安装以绕过“雹”的锁定，您可以通过 ADB 开启系统级的“禁止卸载”限制。

根据手机的 Android 系统版本，在终端中执行对应的命令（请将命令中的 `替换为包名` 替换为您想锁定的应用包名，例如 `com.tencent.tmgp.sgame`）：

* **安卓 7.1.2**：
  ```shell
  adb shell service call package 145 s16 替换为包名 i32 1 i32 0
  ```
* **安卓 8.0**：
  ```shell
  adb shell service call package 151 s16 替换为包名 i32 1 i32 0
  ```
* **安卓 8.1**：
  ```shell
  adb shell service call package 152 s16 替换为包名 i32 1 i32 0
  ```
* **安卓 9.0**：
  ```shell
  adb shell service call package 151 s16 替换为包名 i32 1 i32 0
  ```
* **安卓 10**：
  ```shell
  adb shell service call package 156 s16 替换为包名 i32 1 i32 0
  ```
* **安卓 11**：
  ```shell
  adb shell service call package 136 s16 替换为包名 i32 1 i32 0
  ```
* **安卓 12**：
  ```shell
  adb shell service call package 136 s16 替换为包名 i32 1 i32 0
  ```
* **安卓 13**：
  ```shell
  adb shell service call package 133 s16 替换为包名 i32 1 i32 0
  ```
* **安卓 14**：
  ```shell
  adb shell service call package 134 s16 替换为包名 i32 1 i32 0
  ```
* **安卓 15**：
  ```shell
  adb shell service call package 138 s16 替换为包名 i32 1 i32 0
  ```
* **安卓 16**：
  ```shell
  adb shell service call package 139 s16 替换为包名 i32 1 i32 0
  ```

> [!NOTE]
> 开启此限制后，即使用户处于未冻结状态，也无法直接在系统桌面或设置中卸载该软件。若需解除禁止卸载限制，只需将命令末尾的最后一个 `i32 0` 更改为 `i32 1` 并重新执行即可（例如在 Android 14 上解除限制：`adb shell service call package 134 s16 包名 i32 1 i32 1`）。

### 4. 卸载与移除设备所有者
设置为设备所有者的应用（如“雹”本身）无法直接卸载。如需卸载，请进入“雹”的设置中，点击“移除设备所有者”即可。

---

## 许可证

    Hail - Freeze Android apps
    Copyright (C) 2021-2026 Aistra
    Copyright (C) 2022-2026 Hail contributors

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
