#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SPTools 交易记录上传服务器
接收并处理来自Minecraft服务器的交易记录数据
"""

import json
import logging
from datetime import datetime
from typing import Optional
from uuid import UUID

from flask import Flask, request, jsonify
from flask_cors import CORS

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# 创建Flask应用
app = Flask(__name__)
CORS(app)  # 启用跨域支持

# 交易记录存储（实际应用中应该使用数据库）
transactions_db = []


class TransactionRecord:
    """交易记录数据类"""

    def __init__(self, data: dict):
        self.transaction_id = data.get('transactionId')
        self.player_uuid = data.get('playerUuid')
        self.player_name = data.get('playerName')
        self.type = data.get('type')
        self.amount = data.get('amount')
        self.balance_before = data.get('balanceBefore')
        self.balance_after = data.get('balanceAfter')
        self.description = data.get('description')
        self.timestamp = data.get('timestamp')

    def validate(self) -> bool:
        """验证交易记录数据"""
        # 检查必填字段
        if not self.transaction_id:
            logger.warning("缺少 transactionId")
            return False
        if not self.player_uuid:
            logger.warning("缺少 playerUuid")
            return False
        if not self.player_name:
            logger.warning("缺少 playerName")
            return False
        if not self.type:
            logger.warning("缺少 type")
            return False
        if self.amount is None:
            logger.warning("缺少 amount")
            return False
        if self.balance_before is None:
            logger.warning("缺少 balanceBefore")
            return False
        if self.balance_after is None:
            logger.warning("缺少 balanceAfter")
            return False

        # 验证交易类型
        valid_types = ['ORDER', 'TRANSFER', 'PAYMENT_ORDER', 'REFUND']
        if self.type not in valid_types:
            logger.warning(f"无效的交易类型: {self.type}")
            return False

        # 验证UUID格式
        try:
            UUID(self.player_uuid)
        except ValueError:
            logger.warning(f"无效的UUID格式: {self.player_uuid}")
            return False

        return True

    def to_dict(self) -> dict:
        """转换为字典格式"""
        return {
            'transactionId': self.transaction_id,
            'playerUuid': self.player_uuid,
            'playerName': self.player_name,
            'type': self.type,
            'amount': self.amount,
            'balanceBefore': self.balance_before,
            'balanceAfter': self.balance_after,
            'description': self.description,
            'timestamp': self.timestamp
        }


@app.route('/transactions', methods=['POST'])
def receive_transaction():
    """接收交易记录"""
    try:
        # 获取JSON数据
        data = request.get_json()

        if not data:
            logger.warning("收到空的交易记录数据")
            return jsonify({'error': 'Empty data'}), 400

        # 解析交易记录
        transaction = TransactionRecord(data)

        # 验证数据
        if not transaction.validate():
            logger.warning(f"无效的交易记录数据: {data}")
            return jsonify({'error': 'Invalid transaction data'}), 400

        # 保存交易记录
        transactions_db.append(transaction.to_dict())

        logger.info(f"收到交易记录: {transaction.transaction_id} - "
                   f"{transaction.player_name} - {transaction.type} - {transaction.amount}")

        # 返回成功响应
        return jsonify({
            'status': 'success',
            'transactionId': transaction.transaction_id,
            'message': 'Transaction received successfully'
        }), 200

    except Exception as e:
        logger.error(f"处理交易记录时出错: {e}", exc_info=True)
        return jsonify({'error': str(e)}), 500


@app.route('/transactions', methods=['GET'])
def get_transactions():
    """获取所有交易记录（用于调试）"""
    return jsonify({
        'count': len(transactions_db),
        'transactions': transactions_db
    }), 200


@app.route('/transactions/<transaction_id>', methods=['GET'])
def get_transaction(transaction_id: str):
    """获取指定交易记录"""
    for transaction in transactions_db:
        if transaction['transactionId'] == transaction_id:
            return jsonify(transaction), 200

    return jsonify({'error': 'Transaction not found'}), 404


@app.route('/transactions', methods=['OPTIONS'])
def options_transaction():
    """处理OPTIONS请求（用于连接测试）"""
    return jsonify({'status': 'ok'}), 200


@app.route('/api/transfer', methods=['POST'])
def submit_transfer():
    """提交转账请求"""
    try:
        # 获取JSON数据
        data = request.get_json()

        if not data:
            logger.warning("收到空的转账请求数据")
            return jsonify({'error': 'Empty data'}), 400

        # 验证必填字段
        required_fields = ['senderUuid', 'senderName', 'receiverUuid', 'receiverName', 'amount']
        for field in required_fields:
            if field not in data or not data[field]:
                logger.warning(f"缺少必填字段: {field}")
                return jsonify({'error': f'Missing required field: {field}'}), 400

        # 验证UUID格式
        try:
            sender_uuid = str(UUID(data['senderUuid']))
            receiver_uuid = str(UUID(data['receiverUuid']))
        except ValueError as e:
            logger.warning(f"无效的UUID格式: {e}")
            return jsonify({'error': 'Invalid UUID format'}), 400

        # 验证金额
        try:
            amount = float(data['amount'])
            if amount <= 0:
                return jsonify({'error': 'Amount must be greater than 0'}), 400
        except (ValueError, TypeError):
            return jsonify({'error': 'Invalid amount format'}), 400

        # 获取可选字段
        description = data.get('description', f"转账给 {data['receiverName']}")
        balance_before = data.get('balanceBefore', 0.0)
        balance_after = data.get('balanceAfter', 0.0)

        # 创建转账记录
        transaction_id = str(UUID())
        transaction = {
            'transactionId': transaction_id,
            'playerUuid': sender_uuid,
            'playerName': data['senderName'],
            'type': 'TRANSFER',
            'amount': amount,
            'balanceBefore': balance_before,
            'balanceAfter': balance_after,
            'description': description,
            'timestamp': datetime.now().isoformat(),
            'receiverUuid': receiver_uuid,
            'receiverName': data['receiverName']
        }

        # 保存交易记录
        transactions_db.append(transaction)

        logger.info(f"收到转账请求: {transaction_id} - "
                   f"{data['senderName']} -> {data['receiverName']} - {amount}")

        # 返回成功响应
        return jsonify({
            'status': 'success',
            'transactionId': transaction_id,
            'message': 'Transfer request submitted successfully',
            'data': transaction
        }), 200

    except Exception as e:
        logger.error(f"处理转账请求时出错: {e}", exc_info=True)
        return jsonify({'error': str(e)}), 500


@app.route('/api/transfer', methods=['GET'])
def get_transfers():
    """获取所有转账记录"""
    transfers = [t for t in transactions_db if t.get('type') == 'TRANSFER']
    return jsonify({
        'count': len(transfers),
        'transfers': transfers
    }), 200


@app.route('/health', methods=['GET'])
def health_check():
    """健康检查端点"""
    return jsonify({
        'status': 'healthy',
        'timestamp': datetime.now().isoformat(),
        'total_transactions': len(transactions_db)
    }), 200


@app.route('/', methods=['GET'])
def index():
    """根路径"""
    return jsonify({
        'service': 'SPTools Transaction Server',
        'version': '1.0.0',
        'endpoints': {
            'POST /transactions': '接收交易记录',
            'GET /transactions': '获取所有交易记录',
            'GET /transactions/<id>': '获取指定交易记录',
            'POST /api/transfer': '提交转账请求',
            'GET /api/transfer': '获取所有转账记录',
            'GET /health': '健康检查'
        }
    }), 200


@app.errorhandler(404)
def not_found(error):
    """404错误处理"""
    return jsonify({'error': 'Endpoint not found'}), 404


@app.errorhandler(500)
def internal_error(error):
    """500错误处理"""
    logger.error(f"服务器内部错误: {error}", exc_info=True)
    return jsonify({'error': 'Internal server error'}), 500


def main():
    """启动服务器"""
    host = '0.0.0.0'
    port = 8080

    logger.info("=" * 50)
    logger.info("SPTools 交易记录上传服务器启动中...")
    logger.info(f"监听地址: http://{host}:{port}")
    logger.info(f"API端点: http://{host}:{port}/transactions")
    logger.info(f"健康检查: http://{host}:{port}/health")
    logger.info("=" * 50)

    # 启动Flask服务器
    app.run(
        host=host,
        port=port,
        debug=False,
        threaded=True
    )


if __name__ == '__main__':
    main()