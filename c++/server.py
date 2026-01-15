#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import socket
import threading
import json
import time
import argparse
from datetime import datetime
from queue import Queue
from typing import Dict, Any, List

class TransactionRecord:
    def __init__(self, data: Dict[str, Any]):
        self.transaction_id = data.get("transactionId", "")
        self.player_uuid = data.get("playerUuid", "")
        self.player_name = data.get("playerName", "")
        self.type = data.get("type", "")
        self.amount = float(data.get("amount", 0.0))
        self.balance_before = float(data.get("balanceBefore", 0.0))
        self.balance_after = float(data.get("balanceAfter", 0.0))
        self.description = data.get("description", "")
        self.timestamp = data.get("timestamp", "")

    def to_json(self) -> str:
        return json.dumps({
            "transactionId": self.transaction_id,
            "playerUuid": self.player_uuid,
            "playerName": self.player_name,
            "type": self.type,
            "amount": self.amount,
            "balanceBefore": self.balance_before,
            "balanceAfter": self.balance_after,
            "description": self.description,
            "timestamp": self.timestamp
        }, ensure_ascii=False, indent=2)

class TransactionServer:
    def __init__(self, port: int):
        self.port = port
        self.server_socket = None
        self.running = False
        self.server_thread = None
        self.log_thread = None
        self.transaction_queue = Queue()
        self.log_file = None

    def get_current_time(self) -> str:
        return datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    def log_transaction(self, record: TransactionRecord):
        if self.log_file:
            try:
                self.log_file.write("[" + self.get_current_time() + "] " + record.to_json() + "\n")
                self.log_file.flush()
            except Exception as e:
                print("[" + self.get_current_time() + "] 写入日志文件失败: " + str(e))
        print("[" + self.get_current_time() + "] Transaction: " + record.player_name + " | " + record.type + " | " + str(record.amount))

    def log_worker(self):
        while self.running or not self.transaction_queue.empty():
            try:
                record = self.transaction_queue.get(timeout=1)
                self.log_transaction(record)
                self.transaction_queue.task_done()
            except:
                continue

    def parse_http_request(self, request: str) -> tuple:
        lines = request.split("\r\n")
        if not lines:
            return None, None, None, None
        request_line = lines[0].split(" ")
        if len(request_line) < 2:
            return None, None, None, None
        method = request_line[0]
        path = request_line[1]
        headers = {}
        body_start = 0
        for i, line in enumerate(lines[1:], 1):
            if line == "":
                body_start = i + 1
                break
            if ":" in line:
                key, value = line.split(":", 1)
                headers[key.strip()] = value.strip()
        body = "\r\n".join(lines[body_start:]) if body_start > 0 else ""
        return method, path, headers, body

    def handle_client(self, client_socket: socket.socket, client_address: tuple):
        try:
            data = b""
            while True:
                chunk = client_socket.recv(8192)
                if not chunk:
                    break
                data += chunk
                if len(data) > 0 and b"\r\n\r\n" in data:
                    try:
                        request_str = data.decode("utf-8", errors="ignore")
                        if "Content-Length:" in request_str:
                            content_length_start = request_str.find("Content-Length:") + 16
                            content_length_end = request_str.find("\r\n", content_length_start)
                            content_length = int(request_str[content_length_start:content_length_end])
                            body_start = request_str.find("\r\n\r\n") + 4
                            current_body_length = len(request_str) - body_start
                            if current_body_length >= content_length:
                                break
                        else:
                            break
                    except:
                        break
            if not data:
                return
            request_str = data.decode("utf-8", errors="ignore")
            method, path, headers, body = self.parse_http_request(request_str)
            if method is None:
                return
            if method == "OPTIONS":
                response = "HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: POST, OPTIONS\r\nAccess-Control-Allow-Headers: Content-Type, Connection\r\nContent-Length: 0\r\n\r\n"
                client_socket.sendall(response.encode("utf-8"))
                return
            if method == "POST":
                print("[" + self.get_current_time() + "] Received " + str(len(body)) + " bytes")
                try:
                    transactions = json.loads(body)
                    if isinstance(transactions, list):
                        for trans_data in transactions:
                            record = TransactionRecord(trans_data)
                            self.transaction_queue.put(record)
                except json.JSONDecodeError as e:
                    print("[" + self.get_current_time() + "] JSON 解析失败: " + str(e))
                response = "HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\nContent-Type: application/json\r\nContent-Length: 27\r\nConnection: keep-alive\r\n\r\n{\"status\":\"success\"}"
                client_socket.sendall(response.encode("utf-8"))
        except Exception as e:
            print("[" + self.get_current_time() + "] 处理客户端时出错: " + str(e))
        finally:
            client_socket.close()

    def server_loop(self):
        while self.running:
            try:
                client_socket, client_address = self.server_socket.accept()
                client_thread = threading.Thread(target=self.handle_client, args=(client_socket, client_address))
                client_thread.daemon = True
                client_thread.start()
            except OSError:
                if self.running:
                    print("[" + self.get_current_time() + "] 接受连接时出错")
                break

    def start(self) -> bool:
        try:
            self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self.server_socket.bind(("0.0.0.0", self.port))
            self.server_socket.listen(10)
            self.log_file = open("transactions.log", "a", encoding="utf-8")
            self.running = True
            self.server_thread = threading.Thread(target=self.server_loop)
            self.server_thread.daemon = True
            self.server_thread.start()
            self.log_thread = threading.Thread(target=self.log_worker)
            self.log_thread.daemon = True
            self.log_thread.start()
            print("服务器已在端口 " + str(self.port) + " 上启动")
            return True
        except Exception as e:
            print("启动服务器失败: " + str(e))
            return False

    def stop(self):
        self.running = False
        if self.server_socket:
            try:
                self.server_socket.close()
            except:
                pass
        if self.log_file:
            try:
                self.log_file.close()
            except:
                pass
        print("服务器已停止")

def main():
    parser = argparse.ArgumentParser(description="Transaction Record Server")
    parser.add_argument("--port", type=int, default=8080, help="服务器端口（默认: 8080）")
    args = parser.parse_args()
    port = args.port
    print("=" * 37)
    print("Transaction Record Server")
    print("Port: " + str(port))
    print("=" * 37)
    server = TransactionServer(port)
    if not server.start():
        print("启动服务器失败")
        return 1
    try:
        print("按 Ctrl+C 停止服务器...")
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n正在停止服务器...")
        server.stop()
    return 0

if __name__ == "__main__":
    exit(main())
