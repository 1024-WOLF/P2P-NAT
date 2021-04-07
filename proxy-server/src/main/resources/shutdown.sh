#!/bin/bash

cd ..
# 项目目录
BASE_DIR=$(pwd)
# 日志目录 log/
LOG_DIR=${BASE_DIR}/logs
# 如果日志目录不存在，则创建
if [ ! -d "${LOG_DIR}" ]; then
    echo "directory \"${LOG_DIR}\" not exists"
    mkdir ${LOG_DIR}
fi
# 标准输出 log 文件  stdout.log
STDOUT_FILE=${LOG_DIR}/stdout.log

# 主类全类名称
MAIN_CLASS=com.github.tangmonkmeat.ProxyServerBootstrap
# 尝试获取 进程ID
# 避免目录改名后，无法找到
PID=$(ps -ef | grep java | grep ${BASE_DIR}/lib | awk '{print $2}' || ps -ef | grep java | grep ${MAIN_CLASS} | awk '{print $2}')

# 如果判断进程不存在，则退出
if [ -z "${PID}" ]; then
    echo "ERROR: The proxy server does not started !"
    exit 1
fi

# 进程存在
echo -e "stopping proxy server...\n\c"
sleep 1s
# 正常结束进程
kill -15 ${PID} > ${STDOUT_FILE} 2>&1

COUNT=0

# 如果进程还存在，重复 5次 kill
while [ -n "$(ps -f -p ${PID} | grep java)" ]; do
    kill -15 ${PID} > ${STDOUT_FILE} 2>&1
    COUNT=$(( COUNT + 1 ))
    sleep 1
    if [ ${COUNT} -ge 5 ]; then
        break
    fi
done

if [ ${COUNT} -ge 5 ]; then
    echo "ERROR: loop kill ${PID} failed!"
else
    echo "stopped"
    echo "PID=$PID"
fi