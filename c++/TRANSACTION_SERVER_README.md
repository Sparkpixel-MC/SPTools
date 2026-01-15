# SPTools 交易记录上传服务器

用于接收并处理来自Minecraft服务器的交易记录数据的Python HTTP服务器。

## 功能特性

- 接收交易记录数据（POST请求）
- 数据验证（UUID格式、交易类型等）
- 健康检查端点
- 支持跨域请求（CORS）
- 线程安全处理
- 详细的日志记录

## 安装依赖

```bash
pip install -r requirements.txt
```

或手动安装：

```bash
pip install flask flask-cors
```

## 运行服务器

```bash
python transaction_server.py
```

服务器将在 `http://0.0.0.0:8080` 启动。

## API端点

### POST /transactions
接收交易记录数据

**请求体（JSON格式）：**
```json
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440000",
  "playerUuid": "550e8400-e29b-41d4-a716-446655440001",
  "playerName": "Steve",
  "type": "ORDER",
  "amount": 100.0,
  "balanceBefore": 1000.0,
  "balanceAfter": 900.0,
  "description": "购买物品",
  "timestamp": "2026-01-11T13:46:00"
}
```

**交易类型（type）：**
- `ORDER` - 订单
- `TRANSFER` - 转账
- `PAYMENT_ORDER` - 支付订单
- `REFUND` - 退款

**响应（成功）：**
```json
{
  "status": "success",
  "transactionId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Transaction received successfully"
}
```

### GET /transactions
获取所有交易记录（用于调试）

**响应：**
```json
{
  "count": 1,
  "transactions": [...]
}
```

### GET /transactions/<transaction_id>
获取指定交易记录

**响应（成功）：**
```json
{
  "transactionId": "550e8400-e29b-41d4-a716-446655440000",
  ...
}
```

### GET /health
健康检查端点

**响应：**
```json
{
  "status": "healthy",
  "timestamp": "2026-01-11T13:46:00",
  "total_transactions": 1
}
```

## 配置SPTools插件

在 `config.yml` 中配置：

```yaml
# 是否启用交易记录上传功能
transaction_upload_enabled: true

# HTTP长连接服务器地址
transaction_upload_url: "http://your-server-ip:8080/transactions"

# 连接重试间隔（秒）
transaction_upload_retry_interval: 10
```

## 注意事项

1. 当前版本使用内存存储交易记录，重启服务器会丢失数据
2. 生产环境建议使用数据库存储（如MySQL、PostgreSQL）
3. 建议添加身份验证机制以保护API端点
4. 服务器默认监听所有网络接口（0.0.0.0），生产环境建议使用反向代理（如Nginx）

## 使用数据库存储（示例）

如需使用数据库存储，可以修改服务器代码：

```python
# 安装依赖
pip install sqlalchemy pymysql

# 在代码中添加数据库连接
from sqlalchemy import create_engine, Column, String, Float, DateTime, Integer
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker

Base = declarative_base()

class TransactionDB(Base):
    __tablename__ = 'transactions'
    id = Column(Integer, primary_key=True)
    transaction_id = Column(String(36), unique=True)
    player_uuid = Column(String(36))
    player_name = Column(String(64))
    type = Column(String(20))
    amount = Column(Float)
    balance_before = Column(Float)
    balance_after = Column(Float)
    description = Column(String(256))
    timestamp = Column(DateTime)

# 创建数据库连接
engine = create_engine('mysql+pymysql://user:password@localhost/transactions')
Base.metadata.create_all(engine)
Session = sessionmaker(bind=engine)
```

## 许可证

与SPTools插件保持一致