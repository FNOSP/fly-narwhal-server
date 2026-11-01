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
# 剥掉系统前缀 /cgi/ThirdParty/<appname>（生产名与 -dev 名不同，不写死）
URI_PATH="${URI_PATH#/cgi/ThirdParty/*/}"

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
#    不写死应用名：同一份包也以 -dev 名称安装（开发版），安装路径随之不同。
#    本文件位于 /var/apps/<appname>/target/ui/index.cgi，按自身真实路径推出应用名。
APPNAME="$(basename "$(dirname "$(dirname "$(readlink -f "$0")")")")"
PROXY_PORT_CONFIG_FILE="/var/apps/${APPNAME}/target/ui/server_port.conf"
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

# 7. 透传请求并输出响应（-N 关闭缓冲，弹幕 SSE 才能边推边到）
#    响应直接写到 stdout，不经过 shell 变量：command substitution 会丢弃 NUL 字节
#    并重解释换行，PNG/JPG/MP4 经它往返后长度对不上声明的 Content-Length，浏览器
#    会以 net::ERR_CONTENT_LENGTH_MISMATCH 丢掉整个文件。
#    唯一例外是 4xx/5xx：curl --fail 会丢掉错误响应体，这时只补一段纯文本提示，
#    否则用户看到的是一片空白。
METHOD="${REQUEST_METHOD:-GET}"
CONTENT_LEN="${CONTENT_LENGTH:-0}"
CURL_ARGS=(-sS -N --fail -i -X "${METHOD}")

if [ "${CONTENT_LEN}" -gt 0 ] 2>/dev/null; then
    CURL_ARGS+=(-H "Content-Type: ${CONTENT_TYPE:-application/json}" --data-binary @-)
    PROXY_BODY="$(cat /dev/stdin)"
    if [ -n "${PROXY_BODY}" ]; then
        printf '%s' "${PROXY_BODY}" | curl "${CURL_ARGS[@]}" "$PROXY_URL"
    else
        curl "${CURL_ARGS[@]}" "$PROXY_URL"
    fi
else
    curl "${CURL_ARGS[@]}" "$PROXY_URL"
fi
CURL_EXIT_CODE=$?

# 8. 处理curl请求失败的情况：流式的响应头已经写出去的部分保持原样，只补一条说明
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
fi
exit 0