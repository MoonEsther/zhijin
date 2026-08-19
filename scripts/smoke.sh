#!/usr/bin/env bash
# zhijin 全栈冒烟：校验三服务健康端点。
# 说明：全部中间件（PG/Redis/ES/Nacos/MinIO）均为外部既有基础设施，不在此冒烟范围内。
set -euo pipefail

SERVER="${SERVER_ADDR:-127.0.0.1:8080}"
AI="${AI_ADDR:-127.0.0.1:8001}"
WEB="${WEB_ADDR:-127.0.0.1:5173}"

echo "[1/3] 平台服务(8080)"
curl -fs "http://${SERVER}/actuator/health" || { echo "  ✗ 失败（连接被拒）"; exit 1; }
echo "  ✓ UP"

echo "[2/3] AI 服务(8001)"
curl -fs "http://${AI}/health" || { echo "  ✗ 失败（连接被拒）"; exit 1; }
echo "  ✓ UP"

echo "[3/3] 前端(5173)"
curl -fs "http://${WEB}/" > /dev/null || { echo "  ✗ 失败（连接被拒）"; exit 1; }
echo "  ✓ UP"

echo "== 冒烟通过 =="
