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

### 3. 卸载与移除设备所有者
设置为设备所有者的应用无法直接卸载，如需卸载，请进入“雹”的设置中，点击“移除设备所有者”即可。

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
