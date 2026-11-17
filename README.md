# 班伴

本项目是面向老师的 PC Web 智能排座管理系统。当前 MVP 覆盖班级、学生、成绩、教室布局、排座规则、座位方案生成、人工微调和导出流程。

## 目录结构

```text
.
├── ClassmateHub-springboot   后端，Spring Boot 3 多模块工程
├── ClassmateHub-Vu3       前端，Vue3 + Vite + Element Plus 工程
├── LICENSE                 本项目 MIT 许可证
└── THIRD-PARTY-NOTICES.md  上游项目与第三方依赖声明
```

## 本地运行

### 环境准备

- JDK 17 或更高版本，项目编译目标为 Java 17；本地已使用 JDK 21 打包。
- Maven，本地已使用 Maven 3.9.11 打包。
- Node.js 和 npm，本地已使用 Node.js 22 构建。
- MySQL 和 Redis；本地初始化脚本已在 MySQL 8.4 验证。

### 初始化数据库

创建字符集为 `utf8mb4` 的 MySQL 数据库 `classmate_hub`，然后导入 `ClassmateHub-springboot/sql/classmate_hub.sql`。脚本包含表结构、必要基础配置和上游初始化管理员，不包含班级、学生、座位方案、既有用户及运行日志。不要将本地数据库的实际数据提交到公开仓库。

初始化管理员账号为 `admin`，初始密码为 `admin123`。仅供本地首次登录，登录后立即修改密码，禁止在对外部署时保留默认密码。初始化 SQL 中新建用户的初始密码参数为 `123456`，可在「系统管理 → 参数设置」中修改 `sys.user.initPassword`；请在使用前设置安全的初始密码。

### 配置并启动后端

先启动 MySQL 和 Redis。后端默认连接本机 MySQL 的 `classmate_hub` 数据库，Redis 默认使用 `localhost:6380` 和数据库索引 `5`；与本机环境不同时，请调整连接配置。

启动前，请按自己的本地环境修改 `ClassmateHub-springboot/ruoyi-admin/src/main/resources` 下的配置：

- `application-druid.yml`：将主数据源的数据库地址、账号和密码改为本地 MySQL 的配置。Druid 监控账号和密码使用环境变量占位符，可设置对应环境变量，或改为本地配置。
- `application.yml`：将 Redis 地址、端口、数据库索引和密码改为本地配置；无密码时密码留空。同时将 `token.secret` 改为自己生成的随机密钥，上传目录可按需调整。

配置完成后，可以在 IDEA 中运行 `RuoYiApplication`，也可以在终端中打包并启动：

```bash
cd ClassmateHub-springboot
mvn -DskipTests package
java -jar ruoyi-admin/target/ruoyi-admin.jar
```

不要将实际凭据提交到仓库。

### 安装并启动前端

安装依赖，并在启动 Vite 前将环境变量 `VITE_APP_BASE_API` 设置为 `/dev-api`。例如在 Windows PowerShell 中执行：

```powershell
cd ClassmateHub-Vu3
npm install
$env:VITE_APP_BASE_API = '/dev-api'
npm run dev
```

访问 `http://localhost:8222`。前端开发服务器会将 `/dev-api` 请求代理到 `http://localhost:8310`；非 PowerShell 环境请使用相应方式设置该变量。生产部署时需按实际反向代理路径配置 `VITE_APP_BASE_API`。

### 常见启动问题

如果后端无法连接数据库或 Redis，请检查对应配置及服务状态。如果前端接口请求没有走 `/dev-api`，请检查启动前设置的 `VITE_APP_BASE_API`，并确认后端已经启动。

## 老师使用流程

1. 班级管理
   - 新增班级，选择学段、年级、学期和班级科目。
   - 班级科目会用于后续考试和成绩导入模板。

2. 学生管理
   - 维护学生基础信息，包括学号、姓名、性别、身高、视力等级、成绩等级、纪律等级和特殊需求。
   - 点击「导入」后，在同一个弹窗内选择班级并上传学生 Excel。

3. 成绩管理
   - 先新增考试批次。
   - 点击「查看成绩」进入考试成绩详情页。
   - 下载成绩模板，填写各科成绩、总分或排名后导入。
   - 执行「同步等级」后，学生管理中的成绩等级会更新，并参与后续排座。

4. 教室布局
   - 维护教室座位、过道和不可用座位。
   - 带过道布局中，同桌按每行可用座位从左到右两两识别。

5. 排座规则
   - 配置班级规则，例如男女搭配均衡、成绩强弱均衡、纪律关注学生分散、近视靠前、高个靠后、指定学生坐前排。
   - 规则权重会参与方案生成、优化和总分计算。

6. 座位方案
   - 选择班级和教室布局生成方案。
   - 在详情页查看评分明细、未安排学生和座位表。
   - 可手动拖拽微调、锁定座位、保存调整、确认方案。
   - 支持导出 Excel、图片和 PDF。
   - 支持新学期复制班级，并按需复制学生、关系、规则和默认教室布局。

## 导入说明

- 学生导入只允许 `xls`、`xlsx` 文件。
- 学生导入时必须选择班级，可选择按学号更新该班级已有学生。
- 成绩导入模板按考试所属班级的科目生成，避免小学、初中、高中科目混用。
- 成绩导入后建议执行「同步等级」，再重新生成座位方案验证成绩均衡效果。

## 常见问题

### 为什么男女搭配会扣分？

男女搭配按「同桌」计算。每行可用座位从左到右两两配对，过道和不可用座位不参与配对。同桌两人同性时会计入同性同桌数并扣分。

### 规则权重怎么影响总分？

启用的规则才会生成评分明细。总分按启用规则的 `rule_weight` 加权平均，未启用规则不会稀释总分。

### 修改规则后旧方案会自动重算吗？

不会。旧方案保存的评分明细不会自动变化。需要重新生成方案，或在方案详情页保存一次调整触发重算。

### 成绩等级如何参与排座？

成绩导入后执行「同步等级」，系统会把考试成绩等级同步到学生表的成绩等级字段。之后启用「成绩强弱均衡」规则重新生成座位方案即可参与排座。

## 验证命令

后端编译：

```bash
cd ClassmateHub-springboot
mvn -DskipTests compile
```

前端生产构建：

先按部署环境设置 `VITE_APP_BASE_API`，再执行：

```bash
cd ClassmateHub-Vu3
npm run build:prod
```

## 开源许可与致谢

本项目代码采用 MIT 许可证，详见 `LICENSE`。随附的 Noto Serif SC 字体子集采用 SIL Open Font License 1.1，许可和版权声明见 `ClassmateHub-Vu3/src/assets/fonts/OFL.txt`。

本项目基于若依（RuoYi）二次开发：后端为 RuoYi-Vue 的 Spring Boot 3 分支，前端为 RuoYi-Vue3，上游均遵循 MIT 许可证（`Copyright (c) 2018 RuoYi`）。上游 LICENSE 全文、源码版权头和相关声明均按 MIT 要求保留；第三方依赖清单与署名边界见 `THIRD-PARTY-NOTICES.md`。

本项目不是若依官方产品，与若依官方无隶属或背书关系。
