#!/bin/bash
# Script to capture and display LiteLLM gateway metrics (latency, tokens, cost)

set -e

METRICS_FILE="${1:-/tmp/litellm_metrics.log}"
LITELLM_LOG_CMD="docker compose logs --no-color litellm"

echo "=== LiteLLM Gateway Metrics ==="
echo "Collecting metrics from container logs..."
echo ""

# Filter logs for response times and token counts
$LITELLM_LOG_CMD 2>&1 | grep -iE "(POST|GET|HEAD|completion_tokens|prompt_tokens|total_tokens|\[request|response|latency)" | tail -50 | while read line; do
    # Extract timestamps and relevant info
    if [[ $line =~ \"(POST|GET|HEAD).*HTTP/1\.1\"\ ([0-9]+) ]]; then
        method="${BASH_REMATCH[1]}"
        status="${BASH_REMATCH[2]}"
        echo "[HTTP] $method $status"
    fi
    if [[ $line =~ (completion_tokens|prompt_tokens|total_tokens) ]]; then
        echo "[TOKENS] $line"
    fi
done

echo ""
echo "=== Docker Compose Logs (Recent) ==="
$LITELLM_LOG_CMD 2>&1 | tail -30

echo ""
echo "To capture metrics to a file:"
echo "  docker compose logs --no-color litellm > /tmp/litellm_all_logs.txt"
echo ""
echo "To query with custom filters:"
echo "  docker compose logs litellm 2>&1 | grep 'POST /v1'"
