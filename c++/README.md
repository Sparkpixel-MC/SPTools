# Transaction Record Server

用于接收和记录Minecraft服务器交易记录的C++服务端。

## 功能特性

- 接收来自Minecraft插件的HTTP POST请求
- 支持长连接（keep-alive）
- 将交易记录保存到日志文件
- 跨平台支持（Windows/Linux）

## 编译

### 使用CMake编译

```bash
cd c++
mkdir build
cd build
cmake ..
cmake --build .
```

### Windows直接编译

```bash
g++ -o server.exe server.cpp -lws2_32
```

### Linux编译

```bash
g++ -o server server.cpp -pthread
```

## 运行

### 默认端口（8080）

```bash
./server
```

### 指定端口

```bash
./server 9090
```

## 配置Minecraft插件

在SPTools的`config.yml`中配置：

```yaml
transaction_upload_enabled: true
transaction_upload_url: "http://localhost:8080/transactions"
transaction_upload_retry_interval: 10
transaction_cache_max_size: 1000
```

## API接口

### POST /transactions

接收交易记录数据。

**请求头：**
```
Content-Type: application/json
Connection: keep-alive
```

**请求体：**
```json
[
  {
    "transactionId": "uuid",
    "playerUuid": "uuid",
    "playerName": "player",
    "type": "DEPOSIT",
    "amount": 100.0,
    "balanceBefore": 1000.0,
    "balanceAfter": 1100.0,
    "description": "余额变动监控",
    "timestamp": "2026-01-10T16:00:00"
  }
]
```

**响应：**
```
HTTP/1.1 200 OK
Content-Type: application/json
Connection: keep-alive

{"status":"success"}
```

## 日志文件

交易记录会保存到`transactions.log`文件中，格式为：

```
[2026-01-10 16:00:00] {
  "transactionId": "uuid",
  "playerUuid": "uuid",
  "playerName": "player",
  ...
}
```

## 注意事项

- 确保防火墙允许指定端口的连接
- 日志文件会持续增长，建议定期清理或轮转
- 生产环境建议使用专业的JSON解析库（如nlohmann/json）