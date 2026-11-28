# 二手车交易管理系统开发计划

> 按模块推进，使用复选框跟踪后端、前端和联调验收。执行时可使用 superpowers:executing-plans。

**目标**  
交付可运行的二手车平台。答辩时能演示发布、审核、直销与代销成交、贷款、送修和通知。

**架构**  
用户端与管理端使用同一个 Vue 工程和两套页面布局。Spring Boot 按 Controller、Service、Mapper 三层组织 REST API，MySQL 保存交易及服务记录。订单和车辆状态在同一事务更新，通知入库后再推送。

**技术栈**  
Vue 3、JavaScript、Vite 7、Node.js 22、Vue Router、PrimeVue 4 与 Aura 主题。后端使用 Java 17、Spring Boot 3.5.16、Maven、MyBatis-Plus 3.5.18、Spring Security、Bean Validation 和 WebSocket。通过 springdoc-openapi 2.10.1 提供 OpenAPI 3 和 Swagger UI。MySQL 8.0 用于部署和并发验收，H2 用于本地演示。接口测试使用 JUnit 5 和 MockMvc。

**设计依据**  
[数据库设计与 ER 图](../../database-design.md) · [建表脚本](../../../backend/src/main/resources/schema.sql) · [开题报告](../../../../润色修订稿/开题报告（润色版）.docx)

## 全局约定

- 保留已完成的 16 张表作为数据库基线。字段或约束变化时同时更新 SQL 和数据库设计说明。
- 前端访问同源的 /api 和 /ws，开发时使用现有 Vite 代理。登录采用 Session、BCrypt 和 CSRF 校验，后端按角色与记录归属授权。
- 列表统一返回 items、total、page、pageSize，从第 1 页开始。金额以元为单位，库内使用 DECIMAL。里程以公里存储，页面显示万公里。页面业务时间采用 Asia/Shanghai。
- 未登录返回 401，无角色权限返回 403，资源不存在或对该账号不可见返回 404，状态冲突返回 409，字段校验失败返回 400。
- 车况和贷款人工登记，支付与过户采用模拟确认。演示使用构造数据或授权脱敏数据。
- 每个模块按 **后端接口与测试 → 前端页面 → 联调验收** 推进。

## 代码目录与验证

后端根包位于 backend/src/main/java/cn/edu/usedcar/，HTTP 集成测试位于 backend/src/test/java/cn/edu/usedcar/controller/。前端页面位于 frontend/src/modules/，路由位于 frontend/src/router/index.js，公共请求处理位于 frontend/src/api/http.js。

| 后端目录 | 职责 |
| --- | --- |
| controller | 定义接口路由、校验请求、将业务结果转换为响应。 |
| service | 执行业务规则与权限判断，组织数据操作并管理业务事务。 |
| mapper、entity | Mapper 使用 MyBatis-Plus BaseMapper 完成常规读写和分页，复杂关联及行锁使用参数化 SQL。Entity 对应持久化表。 |
| dto、model | 分别定义接口输入输出和内部业务数据。 |
| security、config、bootstrap | 分别负责会话及认证、Spring 配置与启动初始化。 |

后端在 backend 目录运行 **mvn test** 和 **mvn spring-boot:run**，演示环境可用 **mvn "-Dspring-boot.run.profiles=demo" spring-boot:run**。前端在 frontend 目录首次运行 **npm install** 生成锁文件，再运行 **npm run dev** 和 **npm run build**，有锁文件后用 **npm ci** 重建依赖。

## 重点审查

| 场景 | 应有行为 | 验收模块 |
| --- | --- | --- |
| 改用他人的订单、贷款或附件编号 | 拒绝访问且不返回资料。 | 3、5、6 |
| 同一辆车遇到并发下单，或同一请求再次提交 | 最多一笔有效锁，相同请求返回原订单，重用标识购买其他车辆返回 409。 | 5 |
| 旧订单取消与新订单锁车交错 | 旧订单无法解除新订单的锁。 | 5 |
| 委托到期与下单同时发生 | 新订单检查当期委托，已有订单按约定继续。 | 4、5 |
| 用户离线或推送失败 | 重新登录后可补读，重试不产生重复通知。 | 8 |

## 模块 1 项目启动与账号权限

**文件**　后端 controller/AuthController.java、controller/AdminUserController.java、service/AuthService.java、service/AdminUserService.java、mapper/AccountMapper.java、service/AuditLogService.java、service/NotificationService.java、security/SessionLoginHandler.java、config/SecurityConfig.java、config/MybatisPlusConfig.java、config/OpenApiConfig.java。自定义 SQL 位于 backend/src/main/resources/mapper/AccountMapper.xml。前端 src/main.js、src/router/index.js、src/api/http.js 及 modules/auth 下的 LoginPage.vue、RegisterPage.vue。

- [x] **后端**　提供 GET /api/auth/csrf、POST /api/auth/register、POST /api/auth/login、GET /api/auth/me、POST /api/auth/logout。注册固定分配 USER。管理员可停用账号和调整角色，保证已有会话同步生效。提供共用审计和事务内通知写入方法。
- [x] **前端**　接入 Vue Router 与 PrimeVue Aura，完成登录、注册、退出、会话恢复和用户端、管理端入口。
- [x] **验收**　H2 和 MySQL 8.0.46 各通过 11 项后端集成测试，覆盖多角色账号分页和 OpenAPI。已验证打包应用的 Swagger UI 在演示模式开启及在默认模式关闭。前端 3 项请求测试及 Vite 构建通过，Chrome 实测注册、登录、刷新、退出、越权跳转、后台改角色和身份查询失败后的恢复。

### 本地运行模块 1

使用 Java 17。在项目的 backend 目录打开 PowerShell 7，启动 H2 演示后端。只使用普通用户功能时，可直接运行最后一行。第一次需要演示管理端时，启动前设置管理员用户名与密码。

```powershell
$env:APP_ADMIN_USERNAME = 'car_admin'
$env:APP_ADMIN_PASSWORD = Read-Host '设置初始管理员密码' -MaskInput
mvn "-Dspring-boot.run.profiles=demo" spring-boot:run
```

在另一个终端进入 frontend 目录，运行 **npm ci** 和 **npm run dev**，打开 **http://127.0.0.1:5173**。注册入口固定创建普通用户。配置的初始管理员仅在数据库尚无管理员时创建，后续启动保留原密码。停止后端后，可用 **Remove-Item Env:APP_ADMIN_PASSWORD** 清除当前终端中的密码变量。

使用 MySQL 时，预先创建空数据库，设置 **DB_URL**、**DB_USER** 和 **DB_PASSWORD**，再运行 **mvn spring-boot:run**。管理接口使用 **GET /api/admin/users** 分页查询，**PUT /api/admin/users/{id}/roles** 调整角色，**PUT /api/admin/users/{id}/active** 停用或启用账号。

### Swagger 接口调试

H2 演示模式默认开放 [Swagger UI](http://127.0.0.1:8080/swagger-ui.html) 和 [OpenAPI JSON](http://127.0.0.1:8080/v3/api-docs)。普通运行模式默认关闭文档，调试 MySQL 环境时可在启动前设置 **$env:APP_API_DOCS_ENABLED = 'true'**。

使用同一个浏览器页面调试时，先执行 **GET /api/auth/csrf**，把响应的 token 填入登录请求的 **X-CSRF-TOKEN** 请求头。登录成功后浏览器会携带会话 Cookie，重新获取一次 CSRF 令牌即可测试后续写请求。管理员接口仍需要管理员身份。

## 模块 2 车源与基础字典

**文件**　后端 controller/VehicleController.java、service/VehicleService.java、mapper/VehicleMapper.java、controller/DictionaryController.java。前端 modules/vehicles 下的 VehicleListPage.vue、VehicleDetailPage.vue、PublishVehiclePage.vue、MyVehiclesPage.vue、VehicleReviewPage.vue。

- [x] **后端**　提供公开字典、车源列表和详情、本人车源、发布、重提、下架和后台审核接口。组合筛选支持品牌、城市、价格、年份、里程及经营方式。审核版本、业务状态、审计与审核通知均由服务端维护。
- [x] **前端**　完成车源市场、详情、发布、我的车源、后台审核及品牌城市字典维护页面。
- [x] **验收**　H2 和 MySQL 8.0.46 各通过 14 项后端回归测试，覆盖归属校验、审核版本及组合筛选。Chrome 实测字典维护、发布、退回、重提、上架、筛选、详情和下架。前端测试与 Vite 生产构建通过。

### 本地验收车源流程

1. 用初始化的管理员登录，在 **管理端 → 基础字典** 添加至少一个启用的品牌和城市。
2. 切换到普通用户，从 **车源市场 → 发布车源** 填写车辆资料。去 **个人中心 → 我的车源** 查看状态和审核说明。
3. 用工作人员或管理员从 **管理端 → 车源审核** 处理待审记录。直销审核通过后即可在公开市场查到，退回须填写原因。代销车源在完成委托流程后上架。

新版资料重新提交会生成新的审核版本，后台旧页面需要刷新后重新审核。发布人可下架自己的在售车源，并在修改后重提。游客可以读取在售车源，发布、本人车源与后台管理仍按会话和角色授权。

## 模块 3 照片与车况报告

**文件**　后端 controller/VehicleReportController.java、service/ReportService.java、controller/AttachmentController.java、service/AttachmentService.java。前端 modules/vehicles/VehicleGallery.vue 及 modules/reports 下的 ReportEditor.vue、ReportReviewPage.vue。

- [x] **后端**　提供照片与报告上传、列表、受控读取，以及后台报告分页和审核接口。照片支持 JPEG、PNG 和 WebP，报告附件增加 PDF。校验文件内容、大小、记录归属和下载权限。发布时一并提交的车辆、照片和报告在同一事务保存。
- [x] **前端**　发布页可同时上传照片、人工登记查博士等报告及附件。车辆详情展示相册、审核通过的报告和下载入口。卖家可在我的车源补充资料，后台可审核或填写退回原因。
- [x] **验收**　H2 和 MySQL 8.0.46 各通过 17 项后端测试，覆盖超限和错误类型拒绝、待审资料可见范围、跨车辆附件访问及上传回滚。前端 4 项测试和生产构建通过。Chrome 实测发布上传、相册显示、报告审核、公开下载和窄屏布局。

### 本地验收照片和报告流程

1. 在 **发布车源** 中选择实拍照片，打开 **同时登记车况报告**，填写来源、日期和摘要。可一起选取检测报告 PDF 或图片。
2. 工作人员在 **管理端 → 车源审核** 查看实拍图并处理车源，在 **管理端 → 车况报告** 查看报告附件并通过或退回。买家可在在售详情下载已通过的报告。
3. 卖家可从 **我的车源 → 详情** 查看报告审核结果。待审报告可补充附件，已退回报告可重新登记为一条新报告。在售车源须先下架，再补充实拍图并重新提交。

文件默认保存在后端的 **data/uploads**，可通过 **APP_UPLOADS** 自定义目录。默认单文件 5 MB，同一次 multipart 请求 6 MB。上传图片在限制尺寸内进行解码验证，PDF 报告通过受控下载接口提供。

## 模块 4 代销与看车预约

**文件**　后端 controller/ConsignmentController.java、service/ConsignmentService.java、controller/AppointmentController.java、service/AppointmentService.java。前端 modules/consignments 下的 ConsignmentPage.vue、ConsignmentReviewPage.vue，以及 modules/appointments 下的 MyAppointmentsPage.vue、AppointmentQueuePage.vue。

- [x] **后端**　提供委托提交、历史查询、审核和撤回接口。代销上架和下单资格校验统一检查委托状态、有效期与车辆归属。提供预约提交、个人与后台分页、买家取消、后台经办接口。两个流程复用已有数据表、会话角色、审计和通知。
- [x] **前端**　卖家在车源详情进入委托表单，填写期限、费用和约定。买家在公开详情提交看车时间，在个人中心查看进度。工作人员通过管理端的「代销委托」「看车预约」处理记录。
- [x] **验收**　Java 17 上 H2 和 MySQL 8.0.46 各通过 19 项后端测试，覆盖委托未生效与到期、重复提交与审核、在办订单撤回、预约权限和时间、经办状态流转。前端 4 项测试和生产构建通过。Chrome 验证委托提交、审核上架、预约提交、确认、取消、窄屏布局及 Swagger 接口文档。

### 本地验收代销和预约流程

1. 以卖家身份发布代销车源，从 **我的车源 → 详情 → 办理代销委托** 填写期限、费用和约定。
2. 工作人员从 **管理端 → 代销委托** 审核。委托到达生效时间后，再从 **车源审核** 通过并上架。撤回有效委托会同步下架，委托到期会立即从公开市场隐藏该车。换新委托时重新提交车源审核。
3. 买家从在售车源详情提交预约，工作人员从 **管理端 → 看车预约** 确认并可填写到访说明。买家从 **个人中心 → 我的预约** 查看并可在到访前取消。已确认且到访时间已到的预约由工作人员登记完成。

委托和预约统一使用北京时间。每辆车可保留一份待审核或未到期的委托，代销看车时间须在有效期内。同一买家对同一车辆可保留一份未到访的待处理或已确认预约。模块 5 的下单服务可在订单事务内调用 `ConsignmentService.requireForOrder`，绑定通过资格校验的委托编号。

## 模块 5 订单与交易状态

**文件**　后端 controller/TradeOrderController.java、service/TradeOrderService.java、mapper/TradeOrderMapper.java，测试 controller/TradeOrderMySqlIT.java。前端 modules/orders 下的 MyOrdersPage.vue、OrderDetailPage.vue、OrderQueuePage.vue。

- [ ] **后端**　提供 POST /api/orders、GET /api/orders、GET /api/orders/{id}、POST /api/orders/{id}/cancel、POST /api/orders/{id}/pay、POST /api/admin/orders/{id}/complete。下单携带 Idempotency-Key，服务读取当前报价。事务内竞争车辆状态、写订单和锁、保存通知。取消和成交按车辆号与订单号核对锁归属。
- [ ] **前端**　详情页提供下单，重试沿用本次提交的请求标识。个人中心展示买入和卖出订单、模拟支付和取消。后台提供成交确认入口。
- [ ] **验收**　跑完直销和代销。用 20 个请求争抢同一辆车，验证最多一笔有效锁及订单。覆盖幂等重试、旧单误解锁、未支付成交和非交易参与者访问。用 **mvn "-Dtest=TradeOrderMySqlIT" test** 单独运行 MySQL 集成测试。

## 模块 6 贷款登记

**文件**　后端 controller/LoanController.java、service/LoanService.java。前端 modules/loans 下的 LoanApplicationPage.vue、MyLoansPage.vue、LoanQueuePage.vue。

- [ ] **后端**　提供 POST /api/orders/{id}/loan、GET /api/loans、GET /api/loans/{id}、PUT /api/loans/{id}、POST /api/admin/loans/{id}/status。一笔订单最多一份申请，补充资料更新原记录。金额不得超过订单金额，期限为 1–84 期。申请人和授权工作人员可访问资料。
- [ ] **前端**　订单页提供贷款申请入口，个人中心显示补充、办理中及办理结果，后台填写办理说明。
- [ ] **验收**　跑完提交、退回补充、重提和办理完成，拒绝重复申请、超额申请和他人访问。

## 模块 7 售前整备与售后送修

**文件**　后端 controller/RepairController.java、service/RepairService.java。前端 modules/repairs 下的 MyRepairsPage.vue、RepairForm.vue、RepairQueuePage.vue。

- [ ] **后端**　提供 POST /api/repairs、GET /api/repairs、GET /api/repairs/{id}、POST /api/repairs/{id}/cancel、POST /api/admin/repairs/{id}/status。售前整备绑定发布人名下车辆，售后绑定本人作为买方的已成交订单及其车辆。
- [ ] **前端**　我的车源提供售前整备入口，成交订单提供售后送修入口。用户查看进度，工作人员在后台工单队列处理。
- [ ] **验收**　分别跑完售前和售后流程，拒绝跨车辆引用、未成交订单、越权及非法状态跳转。

## 模块 8 通知、操作日志与统计

**文件**　后端 controller/NotificationController.java、service/NotificationPushService.java、config/WebSocketConfig.java、controller/AuditController.java、controller/DashboardController.java。前端 modules/notifications/NotificationsPage.vue、modules/admin/DashboardPage.vue 和 AuditLogPage.vue。

- [ ] **后端**　提供 GET /api/notifications、POST /api/notifications/{id}/read、GET /api/admin/dashboard、GET /api/admin/audit-logs 和认证后的 /ws/notifications。按接收人与事件标识去重，推送失败安排重试。
- [ ] **前端**　实现通知入口、未读状态和断线恢复刷新。后台展示待审核车源、在办订单、贷款和送修待办，提供审计日志查询。
- [ ] **验收**　断开买家连接后改变订单状态，重连后可补读。重试只保留一条事件记录，统计值与明细一致，日志与敏感字段按权限显示。

## 模块 9 联调、性能与答辩交付

**文件**　docs/deployment.md、docs/test-report.md、backend/src/main/resources/demo-data.sql 和 tests/ 下的验收脚本。

- [ ] **联调**　准备卖家、买家、工作人员和管理员演示账号。按发布、审核、选车、下单、模拟支付与过户顺序展示，再展示贷款、送修与通知。代销另跑委托到期分支。
- [ ] **性能与恢复**　在 MySQL 中复测 20 个请求竞争同车。使用 1 万条构造车源和 100 个并发查询，记录环境、分页、索引、错误率和 P95，目标为 P95 不高于 2 秒。恢复测试备份并核对记录与关联。
- [ ] **交付**　从空库和干净依赖环境初始化、启动和构建，保存实际执行结果、答辩截图、并发测试证据、演示数据及部署说明。

## 页面基线

- **车源列表**　白底、深蓝主色、大图、价格和关键参数。筛选条件集中在首屏，列表卡片展示车型、年份、里程、地区和交易方式。
- **车辆详情**　上方是相册与报价，接着展示车辆参数、车况报告和预约、下单入口。
- **车源审核**　管理端以侧边导航和紧凑表格组织待办。审核可查阅车源资料，退回时填写原因。

## 待确认的业务默认值

- 委托自然到期停止新交易，已有锁定订单可继续办理。存在在办订单时拒绝卖方撤回委托。订单取消后若委托无效，车源转为下架。
- 模拟支付前买方可取消。模拟支付后由工作人员根据处理原因取消或完成过户。
- 预约由工作人员处理。贷款办理进度和模拟支付状态独立记录。
