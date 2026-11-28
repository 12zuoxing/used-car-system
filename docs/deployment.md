# 部署与答辩演示

作者 pangzhihao

## 本地演示

使用 Java 17、Maven 3.9、Node.js 22 和 PowerShell 7。在项目根目录分别准备后端和前端。

```powershell
Push-Location backend
mvn package
Pop-Location

Push-Location frontend
npm ci
npm test
npm run build
Pop-Location
```

第一次启动时，在后端目录设置演示环境的管理员名和口令。演示使用独立 H2 文件及上传目录，演示数据会保留到下次启动。

```powershell
Set-Location backend
$env:APP_ADMIN_USERNAME = 'demo_admin'
$env:APP_ADMIN_PASSWORD = Read-Host '设置演示账号口令' -MaskInput
$env:DEMO_DB_URL = 'jdbc:h2:file:./data/defense;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH'
$env:APP_UPLOADS = './data/defense-uploads'
$env:SPRING_SQL_INIT_DATA_LOCATIONS = 'classpath:demo-data.sql'
java -jar target/used-car-system-1.0.0.jar --spring.profiles.active=demo
```

在另一个终端从项目根目录启动前端。开发调试使用 `npm run dev`，答辩展示构建结果可使用下列命令。

```powershell
Set-Location frontend
$env:VITE_BACKEND = 'http://127.0.0.1:8080'
npm run preview -- --port 5173 --strictPort
```

保持两个进程运行，从项目根目录准备演示车源。如果演示管理员和其他演示账号设置了不同口令，可另设 `DEMO_ADMIN_PASSWORD`。

```powershell
$env:ACCEPTANCE_BASE = 'http://127.0.0.1:5173'
$env:DEMO_PASSWORD = Read-Host '输入演示口令' -MaskInput
node tests/demo.mjs
```

打开 `http://127.0.0.1:5173`。本地演示接口文档地址为 `http://127.0.0.1:8080/swagger-ui/index.html`。业务账号通过实际注册、登录和角色接口准备，样本资料是构造数据，车图使用仓库样图。脚本可再次执行以读取并保留已创建的演示样本；演示过交易的车辆可从卖家账号发布同类型新车源。

| 账号 | 演示职责 |
| --- | --- |
| `demo_seller` | 发布车源、办理代销、提交售前整备 |
| `demo_buyer` | 看车预约、下单、贷款登记、模拟支付、售后送修及通知 |
| `demo_staff` | 车源和委托审核、预约确认、过户及已支付订单取消、贷款与工单办理 |
| `demo_admin` | 账号角色、基础字典、审计详情 |

建议用分开的 Chrome 账号窗口或不同浏览器保留买方、卖方和后台会话。完整演示可按下列顺序操作。

1. 用卖家账号发布车辆并附实拍样图和报告。工作人员从「车源审核」和「车况报告」审核。买家在市场筛选车源，查看车况并提交看车预约。
2. 买家在直销详情确认下单。随后在订单详情登记贷款，工作人员从「贷款办理」退回补充，买家重提后由工作人员办结。工作人员可先完成卖家提交的售前整备。
3. 买家模拟支付，工作人员确认过户。买家从已成交订单提交售后送修，工作人员受理和登记完工。买卖双方从顶部「通知」查看办理结果。
4. 在代销车源上演示「委托审核 → 车源上架 → 买家下单 → 成交」。验收脚本会用另一台代销车演示委托到期，以及取消旧订单后的下架结果。
5. 用另一笔已模拟支付的订单演示后台取消。进入「交易订单 → 办理过户」，点击「取消已支付订单」，填写原因并确认。买卖双方可从订单详情查看原因和模拟支付时间。仍符合上架条件的车辆恢复在售，已到期的代销车源转为下架。

## MySQL 部署

由数据库管理账号创建 `used_car`，字符集使用 `utf8mb4`。空库执行 [schema.sql](../backend/src/main/resources/schema.sql)，并为独立应用账号配置该库的数据读写权限。设置数据库连接和后端启动环境后运行 JAR。本服务的订单、预约和委托采用北京时间，示例 JDBC URL 同步设置数据库会话时区。

```powershell
Set-Location backend
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3306/used_car?connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true'
$env:SPRING_DATASOURCE_USERNAME = 'usedcar_app'
$env:SPRING_DATASOURCE_PASSWORD = Read-Host '输入应用数据库口令' -MaskInput
$env:SPRING_SQL_INIT_MODE = 'never'
$env:APP_BIND = '127.0.0.1'
$env:APP_PORT = '8080'
$env:APP_UPLOADS = 'D:/used-car-data/uploads'
$env:APP_ADMIN_USERNAME = 'car_admin'
$env:APP_ADMIN_PASSWORD = Read-Host '设置初始管理员口令' -MaskInput
$env:APP_SECURE_COOKIE = 'true'
$env:APP_API_DOCS_ENABLED = 'false'
$env:SERVER_FORWARD_HEADERS_STRATEGY = 'framework'
java -jar target/used-car-system-1.0.0.jar
```

上面的命令用于新终端，使用默认应用配置。初始管理员账号成功建立后，可从下一次启动环境移除 `APP_ADMIN_USERNAME` 和 `APP_ADMIN_PASSWORD`。上传目录应位于静态文件根目录之外，由运行后端的账号读写。部署时把 `frontend/dist` 交给静态站点，同一 HTTPS 主机转发 `/api/` 和 `/ws/`。

下面是 Nginx 的 HTTPS 站点示例。替换域名、证书和静态目录后验证配置。Spring 使用代理传递的来源信息检查 WebSocket 同源，Nginx 为升级请求传递 `Upgrade` 及 `Connection`。示例将连接读超时设为 10 分钟，浏览器断线后会重新连接及读取收件箱。[Nginx WebSocket 代理文档](https://nginx.org/en/docs/http/websocket.html)

```nginx
# 放在 http {} 中。
map $http_upgrade $usedcar_connection {
    default upgrade;
    '' close;
}

server {
    listen 443 ssl;
    server_name cars.example.org;
    ssl_certificate /etc/nginx/tls/fullchain.pem;
    ssl_certificate_key /etc/nginx/tls/privkey.pem;
    root /srv/used-car/frontend/dist;
    client_max_body_size 6m;

    proxy_set_header Host $http_host;
    proxy_set_header Forwarded "";
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $http_host;
    proxy_set_header X-Forwarded-Port $server_port;
    proxy_set_header X-Forwarded-Prefix "";

    location / { try_files $uri $uri/ /index.html; }
    location /api/ { proxy_pass http://127.0.0.1:8080; }
    location /ws/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $http_host;
        proxy_set_header Forwarded "";
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $http_host;
        proxy_set_header X-Forwarded-Port $server_port;
        proxy_set_header X-Forwarded-Prefix "";
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $usedcar_connection;
        proxy_read_timeout 600s;
    }
}
```

## 备份与恢复

维护窗口停止业务写入后，将 MySQL 导出与 `APP_UPLOADS` 文件一起备份。使用 `mysqldump --single-transaction --no-tablespaces --set-gtid-purged=OFF --result-file=usedcar.sql -u BACKUP_USER -p used_car` 导出 InnoDB 数据。恢复时先创建单独空库，再用 MySQL 客户端 `SOURCE` 执行导出文件，并把附件副本放到新实例的 `APP_UPLOADS`。恢复验证用 `SPRING_SQL_INIT_MODE=never` 启动应用，核对数据和附件。[MySQL mysqldump 文档](https://dev.mysql.com/doc/refman/8.0/en/mysqldump.html)

## 复跑毕业设计验收

Windows 在已完成前后端构建后，从项目根目录运行以下命令。脚本为本次验收单独初始化 MySQL、业务库和恢复库，使用临时浏览器和随机口令。前端和数据库均在回环地址运行。缺省证据目录是 `.runtime/acceptance-evidence`，测试运行日志和备份保存在此次 `.runtime/m9-*` 下。

```powershell
./tests/run-acceptance.ps1 -MySqlHome 'D:/MySQL' -ChromePath 'C:/Program Files/Google/Chrome/Application/chrome.exe'
```

使用 `-BuildRoot` 指定干净构建目录，目录内应有 `backend/target`、`frontend/dist` 和 `frontend/node_modules`。使用 `-EvidenceDir` 指定 JSON 和截图的输出目录。验收记录和截图入口见 [测试报告](test-report.md)。
