# 班伴前端

班伴是面向老师的 PC Web 智能排座管理系统，前端提供班级、学生、成绩、教室布局、排座规则和座位方案管理能力。

## 技术栈

- Vue 3
- Vite
- Element Plus
- Pinia
- Vue Router

## 本地运行

先按仓库根目录的 `README.md` 配置并启动后端。以下示例使用 Windows PowerShell：

```powershell
npm install
$env:VITE_APP_BASE_API = '/dev-api'
npm run dev
```

开发服务默认运行在 `http://localhost:8222`，接口通过 `/dev-api` 代理到后端服务。

## 构建生产版本

先将 `VITE_APP_BASE_API` 设置为部署环境的接口前缀，再执行：

```bash
npm run build:prod
```

## 主要功能

- 班级、学生和成绩管理
- 学生与成绩 Excel 导入
- 教室座位布局配置
- 排座规则配置、规则引擎生成与 AI 大模型智能排座
- AI 模型接口配置（含多供应商预设与思考程度选择）及老师权限管控
- 座位方案拖拽微调、锁定、确认和历史管理
- Excel、图片与 PDF 导出

## 相关文档

- 项目说明：仓库根目录 `README.md`
