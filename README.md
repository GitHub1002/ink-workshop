# 墨坊

一个卖墨、纸、印泥的小店。浏览器里的店面和 `curl` 都只访问网关 `http://localhost:8080`。店铺、商品库和库存是各自的进程，各用各的数据库。下单后通过 RabbitMQ 扣库存；余额不够时，再发一条消息把库存加回去。

## 进程

| 进程 | 主类 | 端口 | 数据库 |
| --- | --- | --- | --- |
| 网关 | `ink.gateway.GatewayApplication` | 8080 | 无 |
| 库存 | `ink.inventory.InventoryApplication` | 8081 | `ink_inventory` |
| 商品库 | `ink.catalog.CatalogApplication` | 8082 | `ink_catalog` |
| 店铺 | `ink.workshop.InkWorkshopApplication` | 8083 | `ink_order` |
| 店铺第二份 | 同一个主类，`--server.port=8084` | 8084 | `ink_order` |
| 店面 | `web/` 里的 Vite 页面 | 5173 | 无 |

两份店铺使用同一个服务名 `ink-workshop`，注册到 Nacos。网关用 `lb://ink-workshop` 轮流转发。商品库和库存不注册，店铺用固定地址调用它们。店面不访问 8081–8084。

金额用整数分。新注册用户的余额是 50000 分（500.00 元）。商品库启动时写入四件商品：松烟墨 6800、油烟墨 12800、宣纸 3600、朱砂印泥 4500，每件库存 10。

订单状态：

| 状态 | 含义 |
| --- | --- |
| `PENDING` | 已收下订单，正在扣库存 |
| `RESERVED` | 库存已留下，可以付款 |
| `REJECTED` | 库存不够，订单结束 |
| `PAID` | 已付款 |
| `RELEASING` | 余额不够，正在把库存加回去 |
| `CANCELLED` | 已取消，库存已退回 |

## 环境

- JDK 21
- Maven 3.9
- Node.js，用于店面
- Docker，用于 Postgres、RabbitMQ、Nacos 和 Zipkin

练习用的口令如下，不要拿到正式环境里用。

| 组件 | 地址 | 账号 |
| --- | --- | --- |
| Postgres | `localhost:5433` | `ink` / `ink` |
| RabbitMQ 管理台 | http://localhost:15672 | `ink` / `ink` |
| Nacos 控制台 | http://localhost:18080 | `nacos` / `nacos` |
| Zipkin | http://localhost:9411 | 无 |

## 启动

先起基础设施：

```powershell
docker compose up -d postgres rabbitmq nacos zipkin
```

Nacos 3 第一次启动没有管理员密码。等 http://127.0.0.1:8848/nacos/v3/admin/core/state/liveness 返回 200 后执行一次：

```powershell
curl.exe -sS -X POST "http://127.0.0.1:8848/nacos/v3/auth/user/admin" -d "password=nacos"
```

已经初始化过会返回 409，可以忽略。然后用 `nacos` / `nacos` 打开控制台。服务列表在「服务管理」。配置管理一开始是空的，进程会继续使用 `src/main/resources` 里的本地配置。`nacos/` 目录中的四个文件可以放进配置中心，数据 ID 与文件名相同，分组是 `DEFAULT_GROUP`。

再起五个 Java 进程。在 Windows 上同时跑多个 `mvn spring-boot:run` 会锁住 `target/classes`，所以用下面的方式：

```powershell
mvn -q "-DincludeScope=runtime" "-Dmdep.outputFile=target/runtime-cp.txt" dependency:build-classpath
$cp = (Get-Content -Raw target\runtime-cp.txt).Trim()
```

开五个终端，分别执行：

```powershell
java -cp "target/classes;$cp" ink.catalog.CatalogApplication
java -cp "target/classes;$cp" ink.inventory.InventoryApplication
java -cp "target/classes;$cp" ink.workshop.InkWorkshopApplication
java -cp "target/classes;$cp" ink.workshop.InkWorkshopApplication --server.port=8084
java -cp "target/classes;$cp" ink.gateway.GatewayApplication
```

对外请求只发到 http://localhost:8080 。响应头 `X-Instance-Port` 是实际处理请求的店铺端口，用来区分 8083 和 8084。

## 店面

页面在 `web/`，开发服务器固定使用 5173。这个端口被占用时 Vite 会直接退出。另开一个终端：

```powershell
cd web
npm install
npm run dev
```

浏览器打开 http://localhost:5173 。网关允许 `http://localhost:5173` 和 `http://127.0.0.1:5173` 跨域访问。

页面上可以：

- 查看四件商品和各自还剩多少。
- 入队或登舰。登录状态只留在当前浏览器标签页。
- 按库存上限选数量，发射订单，等到「库存已留」或「库存不足」。
- 对已留库存的订单付款。余额不够时，页面会等到库存退回。
- 在航行记录里看到每件货的名称、数量、单价和下单时间。刷新页面后，还在扣库存或退库存的订单会继续等到结果。
- 登舰后在「舰务」里修改可售数量，或上架新货。新货的库存先是 0，需要再补给。

## 用接口走一遍

注册并登录：

```powershell
curl.exe -sS -H "Content-Type: application/json" -d "{\"email\":\"buyer@example.com\",\"password\":\"password1\",\"name\":\"buyer\"}" http://localhost:8080/users
curl.exe -sS -H "Content-Type: application/json" -d "{\"email\":\"buyer@example.com\",\"password\":\"password1\"}" http://localhost:8080/sessions
```

密码需要 8 到 72 个字节。登录响应里的 `token` 放到后面的 `Authorization` 头。下单和付款都需要 `Idempotency-Key`，长度 1 到 80。同一把钥匙加上同一份请求体会得到同一张订单。

```powershell
curl.exe -sS -H "Authorization: Bearer <token>" -H "Idempotency-Key: order-1" -H "Content-Type: application/json" -d "{\"lines\":[{\"productId\":1,\"quantity\":1}]}" http://localhost:8080/orders
```

新订单返回 202，状态是 `PENDING`。库存扣成功后变成 `RESERVED`，库存不够则变成 `REJECTED`。查询自己的订单：

```powershell
curl.exe -sS -H "Authorization: Bearer <token>" http://localhost:8080/orders
curl.exe -sS -H "Authorization: Bearer <token>" http://localhost:8080/orders/1
```

余额够时付款返回 200，订单变成 `PAID`。余额不够时返回 202，状态先是 `RELEASING`，库存加回去之后变成 `CANCELLED`。

```powershell
curl.exe -sS -X POST -H "Authorization: Bearer <token>" -H "Idempotency-Key: pay-1" http://localhost:8080/orders/1/payments
```

一笔 51200 分的订单会超过 50000 分的初始余额，可以用来看退库存。例如四块油烟墨：`{"lines":[{"productId":2,"quantity":4}]}`。

网关对店铺路径每秒放行 20 个请求，超出返回 429，响应码 `RATE_LIMITED`。店铺连续失败后熔断，返回 503，响应码 `SHOP_UNAVAILABLE`。限流数字写在 Nacos 的 `ink-gateway.yaml` 里，改完后网关会重新加载，不用重启。

## 测试

```powershell
mvn test
```

测试自己拉起 Postgres 和 RabbitMQ，不连接本机的 Nacos。

## Kubernetes

`k8s/platform.yaml` 是 Postgres、RabbitMQ、Nacos 和 Zipkin。`k8s/apps.yaml` 是商品库、库存、两份店铺和网关。两份店铺用各自 Pod 的地址注册。网关的 NodePort 是 30080。

这份清单还没有在集群里部署过。有集群时：

```powershell
docker build -t ink-workshop:0.1.0 .
kubectl apply -f k8s/platform.yaml -f k8s/apps.yaml
```

集群里的 Nacos 同样要先初始化管理员密码。
