# SmartDormSystem Docker 部署

当前 `docker-compose.yml` 启动两个容器：

- `smartdorm-backend`：Spring Boot API，端口 8080
- `smartdorm-frontend`：Nginx 和 Vue 静态页面，端口 80

MySQL 不在 Compose 中创建，后端通过 `host.docker.internal:3306` 连接宿主机数据库。

## 前置条件

1. 安装 Docker Desktop 或支持 Compose v2 的 Docker Engine。
2. 在宿主机启动 MySQL 8，并创建 `alone` 数据库。
3. 确保数据库已有当前项目所需表结构。Docker 后端使用 `ddl-auto=validate`，不会自动建表或改表。
4. 复制 `.env.example` 为 `.env`，填写 `SMART_DORM_DB_PASSWORD`。`.env` 已被 Git 忽略。

如果数据库中还没有管理员，同时填写 `SMART_DORM_BOOTSTRAP_USERNAME` 和 `SMART_DORM_BOOTSTRAP_PASSWORD`。首次管理员创建成功并修改密码后，应清空这两个临时值。

首次建立表结构时，可先按主 README 使用开发模式启动一次后端。

从无乐观锁的旧版升级时，请先停止旧版后端并备份数据库，再执行
`backend/config/upgrade-optimistic-lock.sql`，为 `users` 和 `rooms` 添加 `version` 列。
如果已通过开发模式自动添加了这两列，不要重复执行 SQL。升级完成后再启动新版容器。

## 启动与检查

```powershell
docker compose up -d --build
docker compose ps
docker compose logs -f backend
```

打开：

- 前端：<http://localhost>
- 后端：<http://localhost:8080>

## 停止

```powershell
docker compose down
```

`upload-data` 卷保存上传文件；数据库数据保存在宿主机 MySQL 中。删除该上传卷不会删除 MySQL 数据，但会删除容器内持久化的上传文件。

## 常见问题

- 后端连接失败：检查 MySQL 是否监听 3306、密码是否正确，以及防火墙是否允许 Docker 访问宿主机。
- 表结构校验失败：先在开发模式启动后端，让 JPA 更新现有数据库，再重新启动容器。
- 前端无法调用 API：检查 `smartdorm-backend` 是否正常运行，并查看 `docker compose logs backend`。

项目不会自动创建演示账号或学生数据。不要把真实密码写入 Compose、README 或提交到 Git。
