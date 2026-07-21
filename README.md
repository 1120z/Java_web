# 航空电子元件库存管理系统

> 面向航空工业场景的航空电子元件库存管理系统，基于 Servlet + JDBC + MySQL 原生技术栈实现。
> 东软教育 × 郑州航空工业管理学院 2026 暑期实训项目。

## 技术栈

| 层次 | 技术 | 版本 |
|------|------|------|
| 后端 | Servlet + JDBC | Servlet 4.0 |
| JSON | Jackson | 2.13.2 |
| 实体 | Lombok | 1.18.30 |
| 数据库 | MySQL | 8.0 |
| 前端 | HTML5 + CSS3 + jQuery | jQuery 3.7.0 |
| 图表 | ECharts | 5.5.0 |
| 构建 | Maven | 输出 WAR |
| 运行 | Tomcat | 8.5 |
| JDK | Java | 17 |

## 功能模块

| 模块 | 路径 | 核心功能 |
|------|------|----------|
| 用户登录注册 | /login、/register、/profile | 注册、登录、改密、个人中心 |
| 仪表盘 | /dashboard | 统计卡片、分类环形图、7 天出入库趋势折线图 |
| 元件管理 | /components | CRUD、多条件筛选（分类/供应商/关键词）、低库存预警 |
| 出入库管理 | /inventory | 入库/出库记录、库存联动、多条件筛选 |
| 分类管理 | /categories | 元件分类 CRUD、名称搜索 |
| 供应商管理 | /suppliers | 供应商 CRUD、等级筛选、查看关联元件 |

## 快速开始

### 1. 准备环境

- JDK 17
- Maven 3.8+
- MySQL 8.0
- Tomcat 8.5

### 2. 初始化数据库

```sql
-- 用 MySQL Workbench / Navicat 执行
source sql/init.sql;
```

脚本会创建 `aviation_inventory` 数据库及 6 张表。

### 3. 修改数据库连接

编辑 `src/main/java/com/aviation/utils/JDBCUtils.java`，填入本机 MySQL 账号密码。

### 4. 构建并部署

```bash
mvn clean package
# 把 target/aviation.war 复制到 Tomcat 的 webapps/
# 双击 bin/startup.bat 启动 Tomcat
```

### 5. 访问

浏览器打开 `http://localhost:8080/aviation/`

### 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |
| zhangsan | 123456 | 操作员 |
| lisi | 123456 | 操作员 |

> 详细部署流程和常见报错请见 [部署说明.md](部署说明.md)

## 项目结构

```
project/
├── pom.xml                       # Maven 配置
├── 部署说明.md                   # 面向新手的完整部署文档
├── sql/
│   ├── init.sql                  # 数据库完整初始化脚本
│   ├── suppliers.sql             # 供应商模块增量迁移
│   └── trend_data.sql            # 折线图数据修复
└── src/main/
    ├── java/com/aviation/
    │   ├── controller/           # 8 个 Servlet
    │   ├── dao/                  # 5 个 DAO
    │   ├── entity/               # 5 个实体类
    │   └── utils/                # JDBC + Auth 工具
    └── webapp/
        ├── css/style.css         # 航空 HUD + Typography 两套风格
        ├── js/                   # jQuery + ECharts 本地依赖
        ├── index.html            # 登录页
        ├── register.html
        ├── dashboard.html        # 仪表盘
        ├── profile.html          # 个人中心
        ├── components.html       # 元件管理
        ├── inventory.html        # 出入库
        ├── categories.html       # 分类管理
        └── suppliers.html        # 供应商管理
```

## 数据库设计

`aviation_inventory` 共 6 张表：

| 表名 | 说明 |
|------|------|
| users | 用户表（含管理员/操作员两种角色） |
| categories | 元件分类表 |
| suppliers | 供应商表 |
| components | 航空电子元件表（核心表，关联分类和供应商） |
| inventory_records | 出入库记录表 |

## 小组分工

| 角色 | 负责模块 |
|------|----------|
| A | 项目经理、架构、用户登录注册 |
| B | 元件管理 |
| C | 出入库管理 |
| D | 前端、仪表盘可视化 |
| E | 供应商管理、分类管理、测试部署 |

## 技术特色

- **航空 HUD 风格 UI**：暗夜黑 + 钴蓝 + 琥珀配色，蓝图网格背景，L 型角标
- **Typography 实验风仪表盘**：超大标题、打字机动效、大数字滚动入场
- **前后端分离**：原生 Servlet + JSON + Ajax，无 JSP 渲染
- **库存联动**：出入库操作自动更新 components 表 stock_quantity
