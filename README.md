# 智能宿舍管理系统（Smart Dorm System）

基于 Spring Boot 和 Vue 3 的宿舍管理系统，包含管理员端和学生端，支持楼栋、房间、学生、报修、请假、公告及数据看板管理。

## 技术栈

- 后端：Java 17、Spring Boot 4.0.8、Spring Security、Spring Data JPA、MySQL 8
- 前端：Vue 3、TypeScript、Element Plus、Pinia、Vue Router、Axios、ECharts、Vite
- 认证：服务端内存 UUID Token，密码使用 BCrypt 哈希保存

Token 有效期为 2 小时，后端重启后现有 Token 会失效。该方式适合课程设计和单机部署；多实例部署时需要改用共享会话或其他统一认证方案。

## 项目结构

![智能宿舍管理系统项目架构](outputs/architecture/smart-dorm.png)

上图展示主要请求路径：双角色页面 → API 与登录状态 → 请求认证 → 接口与业务处理 → JPA → MySQL。
Controller 与 Service 在图中合并展示，部分 Controller 会直接调用 Repository；登录接口存在认证放行规则。

[查看原始高清图片](outputs/architecture/smart-dorm.png) · [交互式架构图 HTML](outputs/architecture/smart-dorm.html)（下载后用浏览器打开）

```text
SmartDormSystem/
├─ backend/
│  ├─ src/main/java/org/example/bwxw/
│  │  ├─ controller/     API 接口
│  │  ├─ service/        业务逻辑
│  │  ├─ repository/     数据访问
│  │  ├─ entity/         JPA 实体
│  │  ├─ dto/            请求和响应对象
│  │  ├─ config/         安全与 Web 配置
│  │  └─ filter/         Token 认证过滤器
│  └─ src/main/resources/
├─ frontend/
│  └─ src/
│     ├─ views/admin/    管理员页面
│     ├─ views/student/  学生页面
│     ├─ layouts/        双端布局
│     ├─ api/            API 调用
│     ├─ stores/         登录状态
│     └─ router/         路由与权限守卫
└─ outputs/architecture/ 交互式项目架构图
```

## 本地启动

需要安装 Java 17、Node.js 24 和 MySQL 8。

日常使用可直接双击根目录的 `启动项目-开发模式.bat`，停止服务时运行 `停止项目-开发模式.bat`。首次配置或需要排查问题时，也可以按下面的命令手动启动。

1. 创建数据库：

   ```sql
   CREATE DATABASE alone CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. 复制 `backend/config/application-local.example.properties` 为 `backend/config/application-local.properties`，填写本机 MySQL 密码。该本地文件已被 Git 忽略。

3. 启动后端：

   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run
   ```

4. 启动前端：

   ```powershell
   cd frontend
   npm ci
   npm run dev
   ```

5. 打开 <http://localhost:5173>。后端地址为 <http://localhost:8080>。

如果数据库中还没有管理员，请在启动前设置环境变量 `SMART_DORM_BOOTSTRAP_USERNAME` 和 `SMART_DORM_BOOTSTRAP_PASSWORD`。程序只会创建一次首个超级管理员，并要求首次登录后修改临时密码。创建成功后应清空临时密码环境变量。

项目不会自动导入测试学生；可在管理员页面使用学生批量导入功能。

## 构建

```powershell
cd frontend
npm ci
npm run build

cd ..\backend
.\mvnw.cmd test
.\mvnw.cmd package
```

后端产物为 `backend/target/backend-0.0.1-SNAPSHOT.jar`，前端产物位于 `frontend/dist/`。

## Docker

当前 Compose 只启动后端和前端，数据库使用宿主机上的 MySQL。完整步骤见 [README-Docker.md](README-Docker.md)。

## 账号安全

- 管理员创建或重置账号时必须设置 8–128 位临时密码。
- 新管理员首次登录后必须修改临时密码。
- 新学生默认密码由导入或管理流程设置；使用默认密码登录时只能只读浏览，修改密码后才能提交数据。
- 不要在公开仓库、截图或部署文档中记录真实账号和密码。

## 验证

项目提交前应通过：

```powershell
cd frontend
npm run build

cd ..\backend
.\mvnw.cmd test
```

GitHub Actions 会在推送和 Pull Request 时执行相同检查。

## 许可证

版本变更与数据库升级要求见 [更新日志](CHANGELOG.md)。

本项目使用 [MIT License](LICENSE)。

## 联系方式

技术支持：<hewen2797950552@gmail.com>
