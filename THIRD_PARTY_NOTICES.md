# 第三方组件说明

## BiliSponsorSkip

- 项目：https://github.com/makabaka11/BiliSponsorSkip
- 来源版本：`e1f0a1973c5760b6c7fb7eccb0281e0d1f933ce0`
- 作者：makabaka11 及上游贡献者；上游亦注明部分实现源自 BiliRoaming、BilibiliSponsorBlock。
- 许可证：[GNU GPL v3.0](licenses/BiliSponsorSkip-GPL-3.0.txt)。此部分继续适用 GPL v3.0；BiliFix 的其余部分适用根目录 AGPL v3.0。
- 移植范围：只读 skipSegments API 协议、AV/BV 转换、分类标识及配色、单次区间跳过保护思路。对应 `feature/sponsor/` 中的来源注释。
- 本项目修改：Java 实现、modern Xposed 接入、国际白版 3.20.4 播放器适配、按播放器绑定 aid/cid、有限缓存与异步查询、原生设置页和 ViewOverlay 进度标记；不包含上游提交、投票、用户身份或统计功能。

GPL v3 第 13 条与 AGPL v3 第 13 条描述两类代码组合时的许可要求；原许可和来源说明随源码保留。
