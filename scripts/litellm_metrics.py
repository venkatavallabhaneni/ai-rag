#!/usr/bin/env python3
"""
LiteLLM Gateway Metrics Parser
Captures and displays latency, token usage, and cost from LiteLLM proxy logs.
"""

import subprocess
import json
import re
from datetime import datetime
from statistics import mean, stdev
from typing import List, Dict, Any

# Model pricing (per 1K tokens)
MODEL_PRICING = {
    "gpt-4o-mini": {"input": 0.00015, "output": 0.0006},
    "gpt-4o": {"input": 0.003, "output": 0.006},
    "text-embedding-3-small": {"input": 0.00002, "output": 0},
    "text-embedding-3-large": {"input": 0.00013, "output": 0},
}

def get_litellm_logs() -> str:
    """Fetch raw logs from litellm container."""
    try:
        result = subprocess.run(
            ["docker", "compose", "logs", "--no-color", "litellm"],
            capture_output=True,
            text=True,
            timeout=10
        )
        return result.stdout + result.stderr
    except Exception as e:
        print(f"Error fetching logs: {e}")
        return ""

def parse_http_requests(logs: str) -> List[Dict[str, Any]]:
    """Parse HTTP request lines to extract status and timing."""
    requests = []
    # Pattern: INFO:     IP:PORT - "METHOD /path HTTP/1.1" STATUS MSG
    pattern = r'INFO:\s+[\d.:]+\s+-\s+"(\w+)\s+(/[\w/\.?=&\-]*)\s+HTTP/[\d.]+"?\s+(\d+)'
    
    for match in re.finditer(pattern, logs):
        method, path, status = match.groups()
        requests.append({
            "method": method,
            "path": path,
            "status": status,
            "timestamp": datetime.now().isoformat()
        })
    
    return requests

def parse_token_usage(logs: str) -> Dict[str, Any]:
    """Parse token usage metrics from logs including detailed cost."""
    total_prompt_tokens = 0
    total_completion_tokens = 0
    total_cost = 0.0
    request_costs = []
    
    # Match structured logs with token and model info
    # Pattern: model=..., prompt_tokens=..., completion_tokens=..., cost=...
    patterns = [
        r'model[:"]\s*([\w\-\.]+)',
        r'prompt_tokens[:"]\s*(\d+)',
        r'completion_tokens[:"]\s*(\d+)',
        r'cost[:"]\s*([\d.]+)',
    ]
    
    # Extract all occurrences
    models = re.findall(r'model[:"]\s*([\w\-\.]+)', logs)
    prompt_tokens = [int(m) for m in re.findall(r'prompt_tokens[:"]\s*(\d+)', logs)]
    completion_tokens = [int(m) for m in re.findall(r'completion_tokens[:"]\s*(\d+)', logs)]
    costs = [float(m) for m in re.findall(r'cost[:"]\s*([\d.eE\-+]+)', logs)]
    
    if prompt_tokens:
        total_prompt_tokens = sum(prompt_tokens)
    if completion_tokens:
        total_completion_tokens = sum(completion_tokens)
    if costs:
        total_cost = sum(costs)
    
    return {
        "total_prompt_tokens": total_prompt_tokens,
        "total_completion_tokens": total_completion_tokens,
        "total_tokens": total_prompt_tokens + total_completion_tokens,
        "total_cost_usd": total_cost,
        "model_count": len(set(models)),
        "models_used": list(set(models))[-5:] if models else []
    }

def calculate_metrics(requests: List[Dict]) -> Dict[str, Any]:
    """Calculate aggregate metrics."""
    if not requests:
        return {}
    
    total_requests = len(requests)
    successful = len([r for r in requests if r.get("status") == "200"])
    failed = total_requests - successful
    
    return {
        "total_requests": total_requests,
        "successful": successful,
        "failed": failed,
        "success_rate": f"{(successful/total_requests)*100:.1f}%" if total_requests else "N/A"
    }

def calculate_cost_estimate(tokens_data: Dict[str, Any]) -> float:
    """Estimate cost based on token usage and model pricing."""
    total_cost = 0.0
    
    if not tokens_data.get("models_used"):
        return 0.0
    
    # Simple estimate: distribute tokens equally across models
    avg_prompt = tokens_data.get("total_prompt_tokens", 0) / max(len(tokens_data.get("models_used", [1])), 1)
    avg_completion = tokens_data.get("total_completion_tokens", 0) / max(len(tokens_data.get("models_used", [1])), 1)
    
    for model in tokens_data.get("models_used", []):
        pricing = MODEL_PRICING.get(model, {"input": 0, "output": 0})
        model_cost = (
            (avg_prompt / 1000) * pricing.get("input", 0) +
            (avg_completion / 1000) * pricing.get("output", 0)
        )
        total_cost += model_cost
    
    return total_cost

def main():
    """Main entry point."""
    print("\n" + "="*70)
    print("LiteLLM Gateway Metrics Report")
    print("="*70)
    print(f"Timestamp: {datetime.now().isoformat()}\n")
    
    logs = get_litellm_logs()
    if not logs:
        print("No logs retrieved. Ensure litellm container is running.")
        return
    
    # Parse metrics
    requests = parse_http_requests(logs)
    metrics = calculate_metrics(requests)
    tokens_data = parse_token_usage(logs)
    
    print("📊 Request Metrics:")
    print(f"  Total Requests: {metrics.get('total_requests', 0)}")
    print(f"  Successful (200): {metrics.get('successful', 0)}")
    print(f"  Failed: {metrics.get('failed', 0)}")
    print(f"  Success Rate: {metrics.get('success_rate', 'N/A')}")
    
    print("\n📝 Endpoint Distribution:")
    endpoints = {}
    for req in requests:
        path = req.get("path", "unknown")
        endpoints[path] = endpoints.get(path, 0) + 1
    
    for endpoint, count in sorted(endpoints.items(), key=lambda x: -x[1]):
        print(f"  {endpoint}: {count} requests")
    
    print("\n💾 Token & Cost Metrics:")
    if tokens_data.get("total_tokens", 0) > 0:
        print(f"  Total Prompt Tokens: {tokens_data.get('total_prompt_tokens', 0):,}")
        print(f"  Total Completion Tokens: {tokens_data.get('total_completion_tokens', 0):,}")
        print(f"  Total Tokens: {tokens_data.get('total_tokens', 0):,}")
        print(f"  Estimated Cost: ${tokens_data.get('total_cost_usd', 0):.6f}")
        if tokens_data.get('models_used'):
            print(f"  Models Used: {', '.join(tokens_data['models_used'])}")
    else:
        print("  No token usage data captured yet")
        print("  (Enable via log_success_callback in lite-llm-config.yaml)")
    
    print("\n📍 Recent Requests (last 5):")
    for req in requests[-5:]:
        print(f"  {req['method']:6} {req['path']:30} → {req['status']}")
    
    print("\n💡 Available Commands:")
    print("  View full logs: docker compose logs litellm")
    print("  Save to file:   docker compose logs litellm > /tmp/litellm.log")
    print("  Filter embeddings: docker compose logs litellm 2>&1 | grep /v1/embeddings")
    print("  Re-run metrics:  python3 scripts/litellm_metrics.py")
    print("\n" + "="*70 + "\n")

if __name__ == "__main__":
    main()
