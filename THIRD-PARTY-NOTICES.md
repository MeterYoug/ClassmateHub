# 第三方声明

本项目「班伴」基于若依（RuoYi）开源项目二次开发。本文列明上游项目、第三方依赖的版权归属与许可信息，以及本项目代码的署名边界。完整许可文本见根目录 `LICENSE`。

## 上游项目

| 项目 | 用途 | 许可证 | 版权 |
| --- | --- | --- | --- |
| RuoYi-Vue（springboot3 分支，v3.9.2） | 后端基础框架 | MIT | Copyright (c) 2018 RuoYi |
| RuoYi-Vue3 | 前端基础框架 | MIT | Copyright (c) 2018 RuoYi |

- 官方仓库：https://gitee.com/y_project/RuoYi-Vue 、https://gitcode.com/yangzongzhuan/RuoYi-Vue3
- 官方著作权说明：https://ruoyi.vip/copyright.html （全系列免费全开源且可商用；官方教学视频另受著作权保护，不得随意传播）
- 两个子目录 `ClassmateHub-springboot/LICENSE` 与 `ClassmateHub-Vu3/LICENSE` 保留上游 MIT 许可全文。
- 源码中的 `Copyright (c) ... ruoyi` 头部注释按 MIT 要求保留，不得删除；`@author ruoyi` 标记属于代码生成器模板遗留，同样保留，说明见「本项目代码」。
- 官方 README 与官网著作权页未见禁止二次开源或禁止商用的附加条款。网上流传的「禁止擅自开源、禁止公开分享源码」条款来自第三方 RuoYi Office 商业版，不适用于本项目。

## 依赖组件

后端通过 Maven 引入，前端通过 npm 引入。本仓库不分发 `node_modules/`、构建产物 `dist/` 与 jar 包，依赖源码未随本仓库附带。

| 侧 | 主要组件 |
| --- | --- |
| 后端 | Spring Boot、Spring Security、MyBatis、Druid、PageHelper、fastjson2、Apache POI、Apache Velocity、commons-io、OSHI、yauaa、springdoc-openapi、JWT |
| 前端 | Vue 3、Vite、Element Plus、Pinia、Vue Router、Axios、ECharts、vuedraggable、Sass |

各依赖遵循其自身许可证，完整名称与版本见各 `pom.xml` 和 `ClassmateHub-Vu3/package.json`。已检查本地安装的 25 个前端直接依赖的包元数据：声明的许可证为 MIT、Apache-2.0 或 ISC；这不涵盖传递依赖或后端依赖，包元数据也不能代替许可证全文核验。对外分发构建产物或依赖源码前，应检查对应的完整依赖树，并按各许可证要求附带 LICENSE 与 NOTICE。

## 字体

- `ClassmateHub-Vu3/src/assets/fonts/NotoSerifSC-SemiBold-subset.ttf` 是 Noto Serif SC 的子集化、静态化版本，字体元数据记载版权为 © 2017–2023 Adobe，原字体版本为 2.02。字体及其修改版本按 SIL Open Font License 1.1 分发，不适用本项目的 MIT 许可；版权声明与许可全文见同目录 `OFL.txt`，构建产物中也包含 `licenses/NotoSerifSC-OFL.txt`。
- 上游项目：https://github.com/notofonts/noto-cjk 。原始字体来自本地安装的 `NotoSerifSC-VF.ttf`；与上游发布包的精确版本对应关系尚未确认。

## 图片与其他素材

仓库包含来自若依项目及本项目添加的图片和图标。素材未逐项完成来源和许可核对，重新分发前应确认各素材的权利与适用条款；MIT 软件许可本身不能证明所有独立素材均可按 MIT 许可分发。

## 本项目代码

`ruoyi-seating` 模块、`com.ruoyi.web.controller.seating` 与 `src/views/seating` 等排座业务代码由本项目编写。部分文件头因使用若依代码生成器模板而保留 `@author ruoyi` 标记，该标记属于模板遗留，不改变上游若依代码的版权归属。

## 商标

本项目不是若依官方产品，未获授权使用「若依 / RuoYi」作为产品名称或进行官方背书宣传。界面品牌为「班伴」。
