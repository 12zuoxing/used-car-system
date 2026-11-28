# 二手车系统模拟数据包

作者 pangzhihao。用于毕业设计本地演示和联调。车图由 AI 生成，车型、车况、价格、日期与交易记录均为演示样例。

| 数据 | 内容 |
| --- | --- |
| 账号 | 卖家、买家、工作人员和管理员各 1 个 |
| 车源 | 12 辆，其中 7 辆公开在售、2 辆交易锁定、1 辆待审核、1 辆已售、1 辆已下架 |
| 字典 | 10 个品牌、5 个城市 |
| 业务记录 | 4 个委托、3 份车况报告、3 个预约、4 笔订单、4 笔贷款登记、3 个工单、7 条通知、5 条审计和对应车辆锁 |
| 文件 | [12 张 JPEG 车图与 2 份 PDF 报告](assets/) |

## 导入 MySQL

解压后，在包含 `schema.sql`、`seed.sql` 的目录打开 PowerShell。`schema.sql` 是项目建表脚本，复制自 `backend/src/main/resources/schema.sql`。使用 Navicat 或 MySQL Workbench 时，选择新建的 `used_car_demo` 数据库，再按相同顺序执行这两份 SQL。

```powershell
mysql.exe -u root -p -e 'CREATE DATABASE used_car_demo CHARACTER SET utf8mb4;'
cmd.exe /d /c 'mysql.exe --default-character-set=utf8mb4 -u root -p used_car_demo < schema.sql'
cmd.exe /d /c 'mysql.exe --default-character-set=utf8mb4 -u root -p used_car_demo < seed.sql'
```

`seed.sql` 为附件和业务记录保留固定编号，每个新库导入一次。预约和委托根据导入时的北京时间生成相对日期。

## 安装附件并启动

在数据包目录执行脚本。脚本会创建 `uploads`，将原始 JPEG 与 PDF 复制为后端能识别的存储文件。重复执行可复用内容一致的文件。

```powershell
$env:APP_UPLOADS = Join-Path (Get-Location).Path 'uploads'
.\install-uploads.ps1 -Destination $env:APP_UPLOADS
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3306/used_car_demo?connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true'
$env:SPRING_SQL_INIT_MODE = 'never'
$env:DB_USER = Read-Host 'MySQL 用户名'
$env:DB_PASSWORD = Read-Host 'MySQL 密码' -MaskInput
```

在同一个 PowerShell 窗口切换到项目的 `backend` 目录，执行 `mvn spring-boot:run` 或启动已打包的 JAR。前端按项目平时的方式启动。`APP_UPLOADS` 也可设为其他绝对路径，运行附件脚本和启动后端时须使用同一目录。

## 演示账号

四个本地演示账号的密码均为 `UsedCarDemo#2026`。

| 账号 | 页面入口 |
| --- | --- |
| `sample_seller` | 我的车源、代销委托、卖出订单、售前整备 |
| `sample_buyer` | 看车预约、买入订单、贷款登记、售后送修、通知 |
| `sample_staff` | 车源及报告审核、委托审核、过户、贷款办理与工单 |
| `sample_admin` | 账号角色、基础字典与审计日志 |

## 答辩演示样例

- 访客从市场打开丰田卡罗拉或比亚迪秦 PLUS。两台车均可展示封面、车况摘要及 PDF 附件。另有两台已经登记过预约的车源，便于展示预约列表。
- 工作人员在后台打开订单 `950005`，演示对已模拟支付的特斯拉 Model 3 确认过户，或填写原因后取消。买家可在订单 `950002` 查看已经取消的思域交易及取消原因。
- 买家打开订单 `950007`，查看已成交的 RAV4 荣放及关联售后工单。工作人员可处理待审的 CX-5 车源及委托。
