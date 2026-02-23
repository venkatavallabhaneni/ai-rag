#!/usr/bin/env python3
"""
LiteLLM Gateway Cost & Performance Monitor
Captures latency, token usage, and calculates costs from actual API responses.
"""

import subprocess
import json
import time
import sys
from datetime import datetime
from typing import Dict, List, Any

# Model pricing (per 1K tokens)
MODEL_PRICING = {
    "gpt-4o-mini": {"input": 0.00015, "output": 0.0006},
    "gpt-4o": {"input": 0.003, "output": 0.006},
    "text-embedding-3-small": {"input": 0.00002, "output": 0},
    "text-embedding-3-large": {"input": 0.00013, "output": 0},
}

def test_embedding_with_metrics(litellm_url: str = "http://localhost:4000", 
                                api_key: str = "local-test-key",
                                num_requests: int = 3) -> List[Dict[str, Any]]:
    """Send test embedding requests and capture metrics."""
    results = []
    
    for i in range(num_requests):
        payload = {
            "model": "text-embedding-3-small",
            "input": f"Sample text for embedding test #{i+1} with some content"
        }
        
        start_time = time.time()
        try:
            result = subprocess.run([
                "curl", "-sS", "-X", "POST",
                f"{litellm_url}/v1/embeddings",
                "-H", "Content-Type: application/json",
                "-H", f"Authorization: Bearer {api_key}",
                "-d", json.dumps(payload)
            ], capture_output=True, text=True, timeout=10)
            
            latency_ms = (time.time() - start_time) * 1000
            
            if result.returncode == 0:
                response_data = json.loads(result.stdout)
                
                # Extract usage data
                usage = response_data.get("usage", {})
                prompt_tokens = usage.get("prompt_tokens", 0)
                completion_tokens = usage.get("completion_tokens", 0)
                total_tokens = usage.get("total_tokens", 0)
                
                # Calculate cost
                pricing = MODEL_PRICING.get("text-embedding-3-small", {"input": 0, "output": 0})
                request_cost = (prompt_tokens / 1000) * pricing.get("input", 0)
                
                results.append({
                    "request_num": i + 1,
                    "latency_ms": latency_ms,
                    "prompt_tokens": prompt_tokens,
                    "completion_tokens": completion_tokens,
                    "total_tokens": total_tokens,
                    "cost_usd": request_cost,
                    "status": "success"
                })
            else:
                results.append({"request_num": i+1, "status": "failed", "error": result.stderr})
        
        except Exception as e:
            results.append({"request_num": i+1, "status": "error", "error": str(e)})
        
        if i < num_requests - 1:
            time.sleep(0.5)
    
    return results

def print_metrics_report(results: List[Dict[str, Any]]):
    """Print formatted metrics report."""
    print("\n" + "="*75)
    print("LiteLLM Gateway - Cost & Performance Metrics")
    print("="*75)
    print(f"Timestamp: {datetime.now().isoformat()}\n")
    
    successful_requests = [r for r in results if r.get("status") == "success"]
    failed_requests = [r for r in results if r.get("status") != "success"]
    
    print("📊 Overall Metrics:")
    print(f"  Total Requests: {len(results)}")
    print(f"  Successful: {len(successful_requests)}")
    print(f"  Failed: {len(failed_requests)}")
    print(f"  Success Rate: {(len(successful_requests)/len(results)*100):.1f}%\n")
    
    if successful_requests:
        latencies = [r.get("latency_ms", 0) for r in successful_requests]
        tokens_list = [r.get("total_tokens", 0) for r in successful_requests]
        costs = [r.get("cost_usd", 0) for r in successful_requests]
        
        print("⚡ Latency Metrics:")
        print(f"  Avg Latency: {sum(latencies)/len(latencies):.2f} ms")
        print(f"  Min Latency: {min(latencies):.2f} ms")
        print(f"  Max Latency: {max(latencies):.2f} ms")
        
        print("\n💾 Token Usage:")
        print(f"  Total Tokens Used: {sum(tokens_list):,}")
        print(f"  Avg Tokens per Request: {sum(tokens_list)/len(tokens_list):.0f}")
        
        print("\n💰 Cost Breakdown:")
        total_cost = sum(costs)
        print(f"  Total Cost: ${total_cost:.6f}")
        print(f"  Avg Cost per Request: ${total_cost/len(successful_requests):.6f}")
        print(f"  Cost per 1M tokens: ${(total_cost / sum(tokens_list) * 1_000_000):.2f}")
        
        print("\n📋 Detailed Results:")
        print(f"  {'Req':<4} {'Latency':<10} {'Tokens':<8} {'Cost':<10}")
        print(f"  {'-'*4} {'-'*10} {'-'*8} {'-'*10}")
        for r in successful_requests:
            print(f"  {r['request_num']:<4} {r['latency_ms']:>7.2f}ms {r.get('total_tokens', 0):>7,} ${r['cost_usd']:>8.6f}")
    
    if failed_requests:
        print("\n⚠️  Failed Requests:")
        for r in failed_requests:
            print(f"  Request {r['request_num']}: {r.get('error', 'Unknown error')}")
    
    print("\n🔗 Container Logs:")
    print("  docker compose logs litellm | tail -50")
    print("  docker compose logs litellm > /tmp/litellm_detailed.log")
    
    print("\n" + "="*75 + "\n")

def main():
    """Main entry point."""
    try:
        print("\n📡 Testing LiteLLM Gateway with metrics capture...")
        print("   Sending 3 embedding requests...\n")
        
        # Get API key from environment
        import os
        api_key = os.environ.get("LITELLM_MASTER_KEY", "local-test-key")
        
        results = test_embedding_with_metrics(api_key=api_key, num_requests=3)
        print_metrics_report(results)
        
    except KeyboardInterrupt:
        print("\n\nInterrupted by user")
        sys.exit(1)
    except Exception as e:
        print(f"\n❌ Error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
