# BiliFix White

[![Download](https://img.shields.io/github/downloads/Xposed-Modules-Repo/com.xjw.bilifix.in/total)](https://github.com/xiaojiuwo233/BiliFix/releases/latest)
[![stars](https://img.shields.io/github/stars/xiaojiuwo233/BiliFix?label=Stars)](https://github.com/xiaojiuwo233/BiliFix)

哔哩哔哩国际版（白版）已经停更并下架一段时间，app内部分功能已失效。本模块目的是修复一些当前无法正常的功能，同时增加一些本地化功能，满足日常使用。

> 自用模块，写得比较烂，不保证其稳定性，欢迎各位大佬贡献！

### 如果本模块对你有所帮助，欢迎点个[Star](https://github.com/xiaojiuwo233/BiliFix)，十分感谢！
### [关于新版bilibili国际版的说明](https://github.com/xiaojiuwo233/BiliFix/issues/6)

## 兼容性

- 仅支持哔哩哔哩国际白版 `(com.bilibili.app.in)` 3.20.4
- Xposed API 101 或更高版本

## 功能

功能设置入口已集成进应用内设置

### 修复

- 修复新版专栏无法在国际版正常查看的问题
- 修复动态无法显示专栏投稿相关内容
- 修复分区页加载失败
- 修复粉丝与关注列表显示未登录
- 修复钱包页加载失败
- 修复完整用户主页
- 修复无法发送付费表情包
- 修复视频音画不同步

### 增强

- 为部分图片分享菜单 添加系统分享按钮
- 评论区和用户主页显示用户 IP 属地
- 获取由b站自动生成的视频字幕资源（自动生成与自动翻译）
- 评论AI翻译（实验性功能）
- 视频小窗贴边收起：双击只在最小与最大两档切换，普通横向视频最大接近屏幕宽度，左右保留约 8 dp 窄边，保持视频比例；拖向屏幕左右边缘收起，点击短圆角把手或向内滑动恢复；在增强设置中开启，关闭立即恢复。收起后显示 22 × 88 dp 半透明灰色把手和细白箭头，不拦截把手上下的区域；沿用原播放器，不暂停视频；当前适配国际白版 3.20.4 普通视频小窗。

## 已知问题

- 新版专栏下 评论区图片无法正常显示（可进入评论详情查看）
- 部分用户首次进入专栏可能会提示错误，重进即可恢复
- 部分账户无法显示评论中的IP属地 [#16](https://github.com/xiaojiuwo233/BiliFix/issues/16)

## 反馈

如果你在使用过程中遇到问题，欢迎在[issue](https://github.com/xiaojiuwo233/BiliFix/issues/new)中提出。

注意，反馈前务必包含必要的复现步骤和**打开详细日志选项**后的日志，否则issue将会被关闭。

## 许可证

本项目以 [GNU Affero General Public License v3.0](LICENSE) 开源。第三方组件仍遵循各自许可证。

## 小窗功能测试构建

```sh
./gradlew -PtestVersionName=0.12.1-test.6 -PtestVersionCode=44 \
  :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

测试版本参数只覆盖本次构建，默认正式版本保持不变。使用支持 modern Xposed API 101 的 LSPatch，将本模块内嵌到原始国际白版 3.20.4；不要同时加载旧的独立小窗模块。签名与已安装应用一致才可保留数据覆盖安装。

真机验证（2026-10-30）：NX888J / Android 16 / b6282，JingMatrix/LSPatch 1.2。已验证左右收起、点击和向内滑动恢复、连续拖动、关闭开关立即还原、重新开启及冷启动保留开关，视频画面持续推进。

当前手机竖屏下，双击在 594 / 1206 px 两档间切换，最大档左右各留 27 px（约 8 dp）；从两侧拉回后保持相同边距。关闭功能会恢复 B 站原始大小偏好，运行期间的两档切换不改写该偏好。把手可见尺寸为 22 × 88 dp，触摸范围为 28 × 88 dp，把手上下不会拦截触摸；灰底不透明度约为 78%，按下时约为 88%。

直播、横竖屏切换和其他 B 站版本尚未实测；横屏与竖向视频的尺寸约束仅通过几何单元测试。最后一次透明度调整已完成构建及安装包校验。
