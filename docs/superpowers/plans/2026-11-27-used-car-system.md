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

## 用户习惯与业务边界

艾媒咨询 2025 年的调查中，受访者购买二手车时考虑价格和养护成本，交易中关心报价是否合理、车况信息是否真实。中国汽车流通协会的行业报告也指出维保记录等车况资料获取困难、售后保障是实际痛点。本项目据此把预算筛选、报告查阅、到店看车和送修进度作为买家端的主要用例。[二手车消费行为调查](https://www.iimedia.cn/c400/105754.html) · [二手车流通行业发展报告](https://www.cada.cn/Trends/info_92_9340.html)

| 角色和阶段 | 系统中的操作 |
| --- | --- |
| 用户买车 | 按品牌、城市、价格、年份、里程和销售方式选车，查看已审核的报告，预约看车或下单。 |
| 同一账号卖车 | 录入车辆资料、上传照片，登记查博士等第三方报告的来源、日期、摘要和附件。选择直销，或提交代销委托后等待审核。 |
| 工作人员经办 | 审核车源、报告和委托，处理看车预约、贷款登记、模拟过户及售前和售后工单。 |
| 交易双方跟进 | 在个人中心查看买入、卖出订单及站内通知。贷款、送修与审计可追溯到对应业务记录。 |

交付形态为响应式 Web 网页。车况和贷款采用人工登记，支付及过户采用系统内的模拟操作。MySQL 的外键关联车源、发布人、订单、贷款和送修工单，品牌、城市和角色作为被引用的字典记录。并发抢同一辆车时使用车辆行锁、唯一车辆锁及订单幂等键，验收结果见 [测试报告](../../test-report.md#并发抢车)。

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

- [x] **后端**　提供个人订单创建、分页、详情、取消和模拟支付接口，以及后台分页、详情、过户确认和已支付订单取消接口。使用 Idempotency-Key 幂等下单并保存服务端报价。订单、车源、车辆锁、审计和通知一起提交。取消、支付及成交均核对车辆锁归属。
- [x] **前端**　车源详情提供下单确认，失败重试沿用同一次请求的标识。个人中心展示买入和卖出订单，可模拟支付或取消。后台可筛选订单，对待过户订单确认成交或填写原因后取消。买卖双方可在订单详情查看取消原因。
- [x] **验收**　H2 全量通过 24 项后端测试，MySQL 8.0.46 通过 5 项订单专项测试。20 个并发下单请求产生一笔订单和一条车辆锁。前端 5 项测试和生产构建通过，Chrome 实测直销下单与成交、代销取消与恢复在售、响应丢失后的幂等重试及窄屏布局。

### 本地验收下单与成交

1. 以普通用户登录，从在售车源详情选择 **立即下单 → 确认下单**。在 **个人中心 → 我的订单** 查看买入记录和服务端保存的报价。
2. 买家可在详情点击 **模拟支付**。工作人员从 **管理端 → 交易订单 → 办理过户** 核对后确认过户。卖家可在 **我的订单 → 卖出订单** 查看最后结果。
3. 另用一笔已模拟支付的订单演示后台取消。工作人员在同一详情页点击 **取消已支付订单** 并填写原因。买卖双方可从站内通知进入对应订单，查看取消原因及原模拟支付时间。
4. 支付前买卖双方均可取消。取消后车源若仍具有上架资格，会恢复在售。代销委托到期时，已经下单的交易可以继续办理，取消后车辆转为下架。

运行专用 MySQL 订单测试时，先将 `ORDER_TEST_MYSQL_URL`、`ORDER_TEST_MYSQL_USER`、`ORDER_TEST_MYSQL_PASSWORD` 指向独立测试库，再在后端目录执行 `mvn "-Dtest=TradeOrderMySqlIT" test`。测试会在该库初始化表结构并写入测试数据。

## 模块 6 贷款登记

**文件**　后端 controller/LoanController.java、service/LoanService.java。前端 modules/loans 下的 LoanApplicationPage.vue、MyLoansPage.vue、LoanQueuePage.vue。

- [x] **后端**　提供订单贷款登记、本人分页及详情、补充重提、后台队列和办理更新。每个订单最多一份登记，金额不得超过服务端保存的订单金额，期限为 1–84 期。仅申请人与授权工作人员可读取资料。
- [x] **前端**　买家从订单详情发起登记，在「我的贷款」查看进度、补充资料和办理说明。工作人员从「贷款办理」退回补充、开始办理、完成或关闭申请。
- [x] **验收**　H2 全量 27 项、MySQL 8.0.46 交易与贷款专项 8 项测试通过，覆盖重复、超额、期限、越权、旧版本和取消联动。前端 5 项测试及生产构建通过，Chrome 实测提交、退回补充、重提、开始办理和登记完成。

### 本地验收贷款登记

1. 买家在 **个人中心 → 我的订单 → 订单详情 → 贷款登记** 填写申请金额、期限和说明。
2. 工作人员在 **管理端 → 贷款办理 → 办理** 选择 **退回补充**，填写需要补充的内容。买家从 **我的贷款 → 补充资料** 修改后重新提交，申请编号保持一致。
3. 工作人员再次打开记录，先选择 **开始办理**，核对后选择 **登记完成** 并填写办理说明。买家可在 **我的贷款** 查看结果。

贷款采用人工登记，办理进度和模拟支付各自记录。订单取消时，事务内同步关闭未完结贷款并保存原因，已经办结的结果保留。贷款集成场景纳入现有交易测试，配置独立 MySQL 测试库后可用 `mvn "-Dtest=TradeOrderMySqlIT" test` 验证。

## 模块 7 售前整备与售后送修

**文件**　后端 controller/RepairController.java、service/RepairService.java。前端 modules/repairs 下的 MyRepairsPage.vue、RepairForm.vue、RepairQueuePage.vue。

- [x] **后端**　提供工单申请、本人分页与详情、取消，以及后台队列、详情与接单、完工或取消操作。售前绑定发布人名下符合状态条件的车辆，售后绑定买家本人的已成交订单及其车辆。状态变更同步写入审计和相关通知。
- [x] **前端**　「我的车源」提供售前整备入口，已成交订单向买家提供售后送修入口。用户在「我的工单」查看进度并取消待受理工单，工作人员从「整备与送修」办理。
- [x] **验收**　H2 全量 30 项、MySQL 8.0.46 交易、贷款与工单专项 11 项测试通过，覆盖跨车辆引用、未成交订单、越权、非法状态跳转及并发受理与取消。前端 5 项测试和生产构建通过。Chrome 实测售前、售后提交、后台完工、本人取消、列表筛选、窄屏布局和 Swagger。

### 本地验收整备与送修

1. 发布人从 **个人中心 → 我的车源 → 售前整备** 选择预计到店时间，填写整备要求并提交。
2. 买家从 **我的订单 → 已成交订单详情 → 售后送修** 选择到店时间并登记故障或送修要求。
3. 工作人员在 **管理端 → 整备与送修** 按类型筛选工单。打开详情后先受理，再登记完工结果。申请人可从 **我的工单** 查看时间及说明。
4. 申请人可主动取消待受理工单。工作人员可取消待受理或处理中的工单，并须填写说明。完工时保存办理说明和实际完工时间。

表单采用北京时间，预计到店时间须晚于提交时间。提交和受理售前整备时，车辆须处于待审核、已退回、在售或已下架状态。工单进度与车辆、订单状态分别记录。

## 模块 8 通知、操作日志与统计

**文件**　后端 controller/NotificationController.java、service/NotificationPushService.java、config/WebSocketConfig.java、controller/AuditController.java、controller/DashboardController.java。前端 modules/notifications/NotificationsPage.vue、modules/admin/DashboardPage.vue 和 AuditLogPage.vue。

- [x] **后端**　提供个人通知分页、已读更新、后台统计及审计查询，并注册需要认证的同源 WebSocket。通知按接收人与事件标识去重，提交后由后台任务领取，传输失败后安排原记录重试。
- [x] **前端**　主导航展示未读数，收件箱支持筛选、标记已读和打开关联记录。断线恢复后重新读取通知。工作台统计链接打开对应状态的队列，审计页支持按类型、编号、动作和日期筛选。
- [x] **验收**　H2 全量 33 项、MySQL 8.0.46 专项 14 项通过，覆盖重试去重、幂等已读、统计与队列一致及审计权限。前端 5 项测试和生产构建通过。Chrome 实测同源握手、在线推送、断线后过户通知补读、工作台状态跳转、角色字段及窄屏布局。

### 本地验收通知、审计与统计

1. 买家打开顶部 **通知**，另用工作人员账号确认过户。在线时，页面自动刷新，并在通知列表显示办理结果和未读数量。
2. 关闭买家标签页，在另一笔交易中由工作人员确认过户。买家重新打开通知后补读该事件，点击 **标为已读** 后核对未读数字。
3. 工作人员从 **管理端 → 工作台** 点选状态及数量，核对车源、订单、贷款、工单对应队列。到 **操作日志** 按业务类型、编号、操作代码或日期查询。工作人员查看业务审计摘要，管理员可查看账号类日志和完整办理说明。

通知正文从受保护的 HTTP 接口读取，WebSocket 帧只提示刷新。通知记录与业务事务一起落库，后台领取已提交的未读记录。失败自动安排退避重试，离线用户可在重连时立即读取列表。

## 模块 9 联调、性能与答辩交付

**文件**　docs/deployment.md、docs/test-report.md、backend/src/main/resources/demo-data.sql 和 tests/ 下的验收脚本。

- [x] **联调**　已准备四个演示角色。真实业务接口验证发布、审核、预约、直销、代销、模拟支付、过户、贷款、送修、通知和审计，覆盖代销委托到期。
- [x] **性能与恢复**　MySQL 8.0.46 上 20 个请求竞争同车，实际保留一笔订单和一条匹配车辆锁。1 万条车源、每轮 100 并发共测 500 次，零错误，P95 为 1088.95 ms。另从备份恢复 16 张表和 8 个附件，用恢复后的应用实测读取车源和原图。
- [x] **交付**　干净 Maven/npm 依赖目录重建成功，H2 33 项、MySQL 专项 14 项及前端 5 项测试通过。已交付演示数据、Windows 验收脚本、部署说明、测试报告、4 张实际界面截图和结构化证据。

[部署与答辩演示](../../deployment.md) · [测试报告与截图](../../test-report.md)

## 页面基线

- **首页**　轮播展示展厅、城市通勤和周末出行场景。访客可手动切换、暂停或打开短片。预算、里程和代销快捷入口连接市场，近期上架从公开列表读取可购买车源。
- **车源列表**　桌面筛选栏与结果并排，手机端按需展开。已选条件显示在结果上方并可逐项移除。从卡片进入详情再返回时可继续使用原筛选。
- **车辆详情**　报价与销售方式在上方展示，相册支持切图及查看大图。车辆参数、车况报告、预约和下单均有明确入口，页面随屏幕宽度调整布局。
- **展示参考**　参考公开车源网站将预算等快捷条件和结果一同展示，以及购车前先看基础信息和车况报告的阅读顺序。项目使用自有车图、深墨绿配色和页面组件。[CarMax 车源浏览](https://www.carmax.com/cars) · [瓜子二手车购车核验顺序](https://www.guazi.com/article/rmwz/6a98d44ecec065002d47cfd0.html)
- **车源审核**　管理端以侧边导航和紧凑表格组织待办。审核可查阅车源资料，退回时填写原因。

## 业务规则

- 委托自然到期停止新交易，已有锁定订单可继续办理。存在在办订单时拒绝卖方撤回委托。订单取消后若委托无效，车源转为下架。
- 模拟支付前买卖双方均可取消。模拟支付后、确认过户前，工作人员可确认成交或填写原因后取消。取消保留模拟支付时间，双方均收到通知，未办结贷款同步关闭。
- 预约由工作人员处理。贷款办理进度和模拟支付状态独立记录。
