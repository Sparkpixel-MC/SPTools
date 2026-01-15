#include <iostream>
#include <string>
#include <vector>
#include <memory>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <queue>
#include <fstream>
#include <chrono>
#include <ctime>
#include <sstream>
#include <iomanip>

#ifdef _WIN32
#include <winsock2.h>
#include <ws2_32.h>
#pragma comment(lib, "ws2_32.lib")
#else
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <fcntl.h>
#define SOCKET int
#define INVALID_SOCKET -1
#define SOCKET_ERROR -1
#define closesocket close
#endif

struct TransactionRecord {
    std::string transactionId;
    std::string playerUuid;
    std::string playerName;
    std::string type;
    double amount;
    double balanceBefore;
    double balanceAfter;
    std::string description;
    std::string timestamp;

    std::string toJson() const {
        std::ostringstream oss;
        oss << "{\n";
        oss << "  \"transactionId\": \"" << transactionId << "\",\n";
        oss << "  \"playerUuid\": \"" << playerUuid << "\",\n";
        oss << "  \"playerName\": \"" << playerName << "\",\n";
        oss << "  \"type\": \"" << type << "\",\n";
        oss << "  \"amount\": " << amount << ",\n";
        oss << "  \"balanceBefore\": " << balanceBefore << ",\n";
        oss << "  \"balanceAfter\": " << balanceAfter << ",\n";
        oss << "  \"description\": \"" << description << "\",\n";
        oss << "  \"timestamp\": \"" << timestamp << "\"\n";
        oss << "}";
        return oss.str();
    }
};

class TransactionServer {
private:
    int port;
    SOCKET serverSocket;
    bool running;
    std::thread serverThread;
    std::thread logThread;
    std::queue<TransactionRecord> transactionQueue;
    std::mutex queueMutex;
    std::condition_variable queueCV;
    std::ofstream logFile;

    void initializeWinsock() {
#ifdef _WIN32
        WSADATA wsaData;
        if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
            std::cerr << "WSAStartup failed" << std::endl;
            exit(1);
        }
#endif
    }

    void cleanupWinsock() {
#ifdef _WIN32
        WSACleanup();
#endif
    }

    std::string getCurrentTime() {
        auto now = std::chrono::system_clock::now();
        auto time = std::chrono::system_clock::to_time_t(now);
        std::ostringstream oss;
        oss << std::put_time(std::localtime(&time), "%Y-%m-%d %H:%M:%S");
        return oss.str();
    }

    void logTransaction(const TransactionRecord& record) {
        if (logFile.is_open()) {
            logFile << "[" << getCurrentTime() << "] " << record.toJson() << std::endl;
            logFile.flush();
        }
        std::cout << "[" << getCurrentTime() << "] Transaction: " << record.playerName
                  << " | " << record.type << " | " << record.amount << std::endl;
    }

    void logWorker() {
        while (running || !transactionQueue.empty()) {
            std::unique_lock<std::mutex> lock(queueMutex);
            queueCV.wait(lock, [this] { return !transactionQueue.empty() || !running; });

            while (!transactionQueue.empty()) {
                TransactionRecord record = transactionQueue.front();
                transactionQueue.pop();
                lock.unlock();

                logTransaction(record);

                lock.lock();
            }
        }
    }

    std::string extractJsonArray(const std::string& body) {
        size_t start = body.find('[');
        if (start == std::string::npos) return "[]";
        size_t end = body.rfind(']');
        if (end == std::string::npos) return "[]";
        return body.substr(start, end - start + 1);
    }

    std::vector<TransactionRecord> parseTransactions(const std::string& jsonArray) {
        std::vector<TransactionRecord> records;
        // 简化版解析，实际应用中应使用JSON库如nlohmann/json
        return records;
    }

    void handleClient(SOCKET clientSocket) {
        char buffer[8192];
        int bytesReceived = recv(clientSocket, buffer, sizeof(buffer) - 1, 0);

        if (bytesReceived > 0) {
            buffer[bytesReceived] = '\0';
            std::string request(buffer);

            if (request.find("OPTIONS") == 0) {
                std::string response = "HTTP/1.1 200 OK\r\n"
                    "Access-Control-Allow-Origin: *\r\n"
                    "Access-Control-Allow-Methods: POST, OPTIONS\r\n"
                    "Access-Control-Allow-Headers: Content-Type, Connection\r\n"
                    "Content-Length: 0\r\n"
                    "\r\n";
                send(clientSocket, response.c_str(), response.length(), 0);
            }
            else if (request.find("POST") != std::string::npos) {
                size_t contentLengthPos = request.find("Content-Length:");
                if (contentLengthPos != std::string::npos) {
                    size_t contentLengthStart = contentLengthPos + 16;
                    size_t contentLengthEnd = request.find("\r\n", contentLengthStart);
                    int contentLength = std::stoi(request.substr(contentLengthStart, contentLengthEnd - contentLengthStart));

                    size_t bodyStart = request.find("\r\n\r\n");
                    if (bodyStart != std::string::npos) {
                        bodyStart += 4;
                        std::string body = request.substr(bodyStart);

                        while (body.length() < contentLength && bytesReceived > 0) {
                            bytesReceived = recv(clientSocket, buffer, sizeof(buffer) - 1, 0);
                            if (bytesReceived > 0) {
                                buffer[bytesReceived] = '\0';
                                body += buffer;
                            }
                        }

                        std::cout << "[" << getCurrentTime() << "] Received " << body.length() << " bytes" << std::endl;

                        std::string response = "HTTP/1.1 200 OK\r\n"
                            "Access-Control-Allow-Origin: *\r\n"
                            "Content-Type: application/json\r\n"
                            "Content-Length: 27\r\n"
                            "Connection: keep-alive\r\n"
                            "\r\n"
                            "{\"status\":\"success\"}";
                        send(clientSocket, response.c_str(), response.length(), 0);
                    }
                }
            }
        }

        closesocket(clientSocket);
    }

    void serverLoop() {
        while (running) {
            sockaddr_in clientAddr;
            int clientAddrSize = sizeof(clientAddr);
            SOCKET clientSocket = accept(serverSocket, (sockaddr*)&clientAddr, &clientAddrSize);

            if (clientSocket != INVALID_SOCKET) {
                std::thread clientThread([this, clientSocket]() {
                    handleClient(clientSocket);
                });
                clientThread.detach();
            }
        }
    }

public:
    TransactionServer(int port) : port(port), serverSocket(INVALID_SOCKET), running(false) {
        initializeWinsock();
    }

    ~TransactionServer() {
        stop();
        cleanupWinsock();
    }

    bool start() {
        serverSocket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
        if (serverSocket == INVALID_SOCKET) {
            std::cerr << "Failed to create socket" << std::endl;
            return false;
        }

        int opt = 1;
        setsockopt(serverSocket, SOL_SOCKET, SO_REUSEADDR, (char*)&opt, sizeof(opt));

        sockaddr_in serverAddr;
        serverAddr.sin_family = AF_INET;
        serverAddr.sin_addr.s_addr = INADDR_ANY;
        serverAddr.sin_port = htons(port);

        if (bind(serverSocket, (sockaddr*)&serverAddr, sizeof(serverAddr)) == SOCKET_ERROR) {
            std::cerr << "Failed to bind socket" << std::endl;
            closesocket(serverSocket);
            return false;
        }

        if (listen(serverSocket, 10) == SOCKET_ERROR) {
            std::cerr << "Failed to listen" << std::endl;
            closesocket(serverSocket);
            return false;
        }

        logFile.open("transactions.log", std::ios::app);
        if (!logFile.is_open()) {
            std::cerr << "Failed to open log file" << std::endl;
        }

        running = true;
        serverThread = std::thread(&TransactionServer::serverLoop, this);
        logThread = std::thread(&TransactionServer::logWorker, this);

        std::cout << "Server started on port " << port << std::endl;
        return true;
    }

    void stop() {
        running = false;
        queueCV.notify_all();

        if (serverSocket != INVALID_SOCKET) {
            closesocket(serverSocket);
            serverSocket = INVALID_SOCKET;
        }

        if (serverThread.joinable()) {
            serverThread.join();
        }

        if (logThread.joinable()) {
            logThread.join();
        }

        if (logFile.is_open()) {
            logFile.close();
        }

        std::cout << "Server stopped" << std::endl;
    }
};

int main(int argc, char* argv[]) {
    int port = 8080;

    if (argc > 1) {
        port = std::atoi(argv[1]);
    }

    std::cout << "=====================================" << std::endl;
    std::cout << "Transaction Record Server" << std::endl;
    std::cout << "Port: " << port << std::endl;
    std::cout << "=====================================" << std::endl;

    TransactionServer server(port);

    if (!server.start()) {
        std::cerr << "Failed to start server" << std::endl;
        return 1;
    }

    std::cout << "Press Enter to stop the server..." << std::endl;
    std::cin.get();

    server.stop();

    return 0;
}
