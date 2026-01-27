#!/bin/bash
# 脚本名称: index.cgi
# 　　版本: 1.0.2
# 　　作者: FNOSP/MR_XIAOBO
# 创建日期: 2025-11-18
# 最后修改: 2026-01-26
# 　　描述: 脚本用于反向代理页面请求到Java后端服务

# 1. 拆分REQUEST_URI为【路径部分】和【查询字符串部分】
#    示例：/cgi/ThirdParty/App.Native.flyNarwhalServer/index.cgi/index.html?foo=bar
#    - URI_PATH: /cgi/ThirdParty/App.Native.flyNarwhalServer/index.cgi/index.html
#    - URI_QUERY: foo=bar
URI_PATH="${REQUEST_URI%%\?*}"  # 提取?前的路径部分
URI_QUERY="${REQUEST_URI#*\?}"   # 提取?后的查询字符串（无则为空）

# 默认代理路径（未匹配到index.cgi时使用）
REL_PATH="/"

# 2. 提取index.cgi后的路径作为代理路径
case "$URI_PATH" in
    *index.cgi*)
        # 截取index.cgi后的部分，例如：
        # /cgi/xxx/index.cgi/index.html → /index.html
        REL_PATH="${URI_PATH#*index.cgi}"
        ;;
esac

# 3. 路径默认值处理
if [ -z "$REL_PATH" ] || [ "$REL_PATH" = "/" ]; then
    REL_PATH="/download.html"
fi

# 4. 安全防御：禁止../越级访问（防止恶意路径）
if echo "$REL_PATH" | grep -q '\.\.'; then
    echo "Status: 400 Bad Request"
    echo "Content-Type: text/plain; charset=utf-8"
    echo ""
    echo "Bad Request: 禁止越级访问"
    exit 0
fi

# 5. 读取代理端口配置文件
PROXY_PORT_CONFIG_FILE="/var/apps/App.Native.flyNarwhalServer/target/ui/server_port.conf"
if [ -f "${PROXY_PORT_CONFIG_FILE}" ]; then
    source "${PROXY_PORT_CONFIG_FILE}"
else
    CGI_PROXY_PORT="5365"
fi

# 6. 构建代理目标URL
PROXY_URL="http://localhost:${CGI_PROXY_PORT}${REL_PATH}"
# 如果有查询字符串，拼接到URL后
if [ -n "$URI_QUERY" ] && [ "$URI_QUERY" != "$URI_PATH" ]; then
    PROXY_URL="${PROXY_URL}?${URI_QUERY}"
fi

# 7. 使用curl请求代理地址并转发响应
if [ "$REL_PATH" = "/api/config/auth-code" ]; then
    RESPONSE=$(curl -X POST -sSL --fail -i "$PROXY_URL")
else
    RESPONSE=$(curl -sSL --fail -i "$PROXY_URL")
fi
CURL_EXIT_CODE=$?

# 8. 处理curl请求失败的情况
if [ $CURL_EXIT_CODE -ne 0 ]; then
    case $CURL_EXIT_CODE in
        6)  # 无法解析localhost（极少发生）
            echo "Status: 502 Bad Gateway"
            ERROR_MSG="无法解析代理地址 localhost"
            ;;
        7)  # 服务尚未完全启动
            echo "Status: 503 Service Unavailable"
            ERROR_MSG="服务尚未完全启动，请稍等片刻"
            ;;
        22) # 目标URL返回4xx/5xx错误
            echo "Status: 404 Not Found"
            ERROR_MSG="代理目标路径不存在: ${REL_PATH}"
            ;;
        *)  # 其他未知错误
            echo "Status: 500 Internal Server Error"
            ERROR_MSG="代理请求失败，curl 退出码: ${CURL_EXIT_CODE}"
            ;;
    esac
    echo "Content-Type: text/plain; charset=utf-8"
    echo ""
    echo "$ERROR_MSG"
    exit 0
fi

# 9. 输出代理响应（包含HTTP头+内容，符合CGI规范）
echo "$RESPONSE"