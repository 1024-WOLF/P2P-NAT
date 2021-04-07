#!/bin/bash

# 获取项目所在的目录
# 目录下有 bin/ lib/ conf/ pages/
BASE_DIR=$(cd ..; pwd)
CONF_DIR=${BASE_DIR}/conf
LIB_DIR=${BASE_DIR}/lib
LOG_DIR=${BASE_DIR}/logs
# 标准输出
STDOUT_FILE=${LOG_DIR}/stdout.log

# lib 目录下的所有jar包
# grep 过滤 jar包
# awk 格式输出（格式化为绝对路径）
# xargs 在一行内空格或者tab分割每个输出
# sed 替换 空格为 :
# 主要目的是 -classpath 的参数有要求
# 比如：-classpath 绝对路径.jar:绝对路径.jar:绝对路径.jar
LIB_JARS=`ls $LIB_DIR|grep .jar|awk '{print "'$LIB_DIR'/"$0}'| xargs | sed "s/ /:/g"`

# 静态资源目录
STATIC_DIR=${BASE_DIR}/pages

# 如果日志目录不存在，创建
if [ ! -d ${LOG_DIR} ]; then
    mkdir ${LOG_DIR}
fi

# 主类的全类名
MAIN_CLASS=com.github.tangmonkmeat.ProxyServerBootstrap

# 通过-cp、扩展CLASSPATH、指定主类的方式启动项目, 
# 并通过“-D”的方式向此程序的运行时环境中设置当前项目的路径,
# 即可在程序中通过System.getProperty("base.dir")获取此路径 

# 启动项目（后台运行）
# 指定运行的环境变量和 classpath
# 标准输出重定向到文件，错误重定向到标准输出
echo -e "starting the proxy server ...\n\c"
nohup java -Dbase.dir=${BASE_DIR} -classpath ${CONF_DIR}:${LIB_JARS} ${MAIN_CLASS} >${STDOUT_FILE} 2>&1 &
sleep 1
# 打印进程ID
PID=$(ps -ef | grep java | grep ${MAIN_CLASS} | awk '{print $2}')
echo "started"
echo "PID=${PID}"