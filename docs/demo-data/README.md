# 二手车系统模拟数据包

作者 pangzhihao。用于毕业设计本地演示和联调。车图由 AI 生成，车型、车况、价格、日期与交易记录均为演示样例。批量车源按车型复用基础包的 12 张图。

| 数据 | 基础样例 | 导入批量数据后合计 |
| --- | ---: | ---: |
| 账号 | 4 | 84 |
| 车源 | 12 | 2,012 |
| 公开在售 | 7 | 1,107 |
| 交易订单 | 4 | 704 |
| 车辆锁 | 2 | 402 |
| 看车预约 | 3 | 1,003 |
| 代销委托 | 4 | 654 |
| 车况报告 | 3 | 1,003 |
| 贷款登记 | 4 | 704 |
| 售前及售后工单 | 3 | 603 |
| 通知、审计 | 7、5 | 1,207、2,705 |
| 图片及 PDF 附件 | 14 | 2,014 |

两套数据共用 10 个品牌、5 个城市。基础车图与报告在 [assets](assets/) 中。

## 导入 MySQL

解压后，在数据包目录打开 PowerShell 7。`schema.sql` 是项目建表脚本，复制自 `backend/src/main/resources/schema.sql`。使用 Navicat 或 MySQL Workbench 时，选择新建的 `used_car_demo` 数据库，再按相同顺序执行三份 SQL。若已经导入上一版 `seed.sql`，连接原演示库，只执行第三份 `bulk-seed.sql`。

```powershell
mysql.exe -u root -p -e 'CREATE DATABASE used_car_demo CHARACTER SET utf8mb4;'
cmd.exe /d /c 'mysql.exe --default-character-set=utf8mb4 -u root -p used_car_demo < schema.sql'
cmd.exe /d /c 'mysql.exe --default-character-set=utf8mb4 -u root -p used_car_demo < seed.sql'
cmd.exe /d /c 'mysql.exe --default-character-set=utf8mb4 -u root -p used_car_demo < bulk-seed.sql'
```

`seed.sql` 和 `bulk-seed.sql` 为附件和业务记录保留固定编号，每个演示库各导入一次。预约和委托根据导入时的北京时间生成相对日期。

## 安装附件并启动

在数据包目录执行脚本。它会将原始 JPEG 与 PDF 复制为后端能识别的存储文件，安装全部附件约需要 226 MB。已有演示库应把 `APP_UPLOADS` 指向原演示附件目录，重复执行脚本时会复用内容一致的文件。只导入基础样例时，去掉 `-IncludeBulk`。

```powershell
$env:APP_UPLOADS = Join-Path (Get-Location).Path 'uploads'
.\install-uploads.ps1 -Destination $env:APP_UPLOADS -IncludeBulk
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3306/used_car_demo?connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true'
$env:SPRING_SQL_INIT_MODE = 'never'
$env:DB_USER = Read-Host 'MySQL 用户名'
$env:DB_PASSWORD = Read-Host 'MySQL 密码' -MaskInput
```

在同一个 PowerShell 窗口切换到项目的 `backend` 目录，执行 `mvn spring-boot:run` 或启动已打包的 JAR。前端按项目平时的方式启动。`APP_UPLOADS` 也可设为其他绝对路径，运行附件脚本和启动后端时须使用同一目录。

## 演示账号

本地演示账号的密码均为 `UsedCarDemo#2026`。批量数据还包括 `bulk_seller_01` 到 `bulk_seller_20` 和 `bulk_buyer_01` 到 `bulk_buyer_60`。原演示卖家和买家也关联部分批量记录。

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
- 批量测试时，访客可以同时按品牌、城市、价格和交易方式筛选，再查看后续页面。工作人员可以按状态查看各业务队列，普通用户可用对应买卖家账号查看个人数据范围。
