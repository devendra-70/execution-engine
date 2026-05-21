"""
Simple Concurrency Load Test for Execution Engine Service
=========================================================
Usage:
    pip install requests
    python load_test.py

Tokens are auto-fetched from GET /api/dev/token?userId=<N>
No manual JWT setup needed — just make sure the app is running.
"""

import concurrent.futures
import time
import requests
from collections import Counter

# ─────────────────────────── CONFIG ───────────────────────────
BASE_URL       = "http://localhost:8080"

TOTAL_REQUESTS = 100   # total submissions to fire
CONCURRENCY    = 10    # parallel workers  (also = number of virtual users)
PROBLEM_ID     = 1     # a valid problemId in your DB
LANGUAGE       = "JAVA"
MODE           = "run" # "run" = sample tests only (faster); "submit" = all tests

# A minimal Java solution — tweak to match problemId's expected output
SOURCE_CODE = """
public class Solution {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}
"""
# ──────────────────────────────────────────────────────────────


def fetch_tokens(n: int) -> list[str]:
    """
    Auto-fetch N JWT tokens from the dev endpoint.
    Each token belongs to a different userId (1..N) so that
    the 5 req/min per-user rate-limit is spread across all users.
    """
    print(f"🔑  Fetching {n} dev tokens from {BASE_URL}/api/dev/token …")
    tokens = []
    for user_id in range(1, n + 1):
        resp = requests.get(f"{BASE_URL}/api/dev/token", params={"userId": user_id}, timeout=10)
        resp.raise_for_status()
        token = resp.json().get("token") or resp.text.strip().strip('"')
        tokens.append(token)
    print(f"   ✅  Got {len(tokens)} tokens\n")
    return tokens


def submit(task_id: int, tokens: list[str]) -> dict:
    token = tokens[task_id % len(tokens)]
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    }
    payload = {
        "problemId": PROBLEM_ID,
        "language": LANGUAGE,
        "mode": MODE,
        "sourceCode": SOURCE_CODE,
    }
    t0 = time.perf_counter()
    try:
        resp = requests.post(f"{BASE_URL}/api/executions", json=payload, headers=headers, timeout=30)
        elapsed = time.perf_counter() - t0
        return {
            "task_id": task_id,
            "status_code": resp.status_code,
            "elapsed_s": round(elapsed, 3),
            "execution_id": resp.json().get("executionId") if resp.ok else None,
            "error": None if resp.ok else resp.text[:200],
        }
    except Exception as exc:
        return {
            "task_id": task_id,
            "status_code": None,
            "elapsed_s": round(time.perf_counter() - t0, 3),
            "execution_id": None,
            "error": str(exc),
        }


def run_load_test():
    # Step 1: auto-fetch one token per virtual user
    tokens = fetch_tokens(CONCURRENCY)

    print(f"🚀  Starting load test: {TOTAL_REQUESTS} requests  |  concurrency={CONCURRENCY}\n")
    results = []
    start = time.perf_counter()

    with concurrent.futures.ThreadPoolExecutor(max_workers=CONCURRENCY) as pool:
        futures = {pool.submit(submit, i, tokens): i for i in range(TOTAL_REQUESTS)}
        for idx, future in enumerate(concurrent.futures.as_completed(futures), 1):
            r = future.result()
            results.append(r)
            status = r["status_code"]
            mark = "✅" if status == 202 else ("⚠️ 429" if status == 429 else f"❌ {status}")
            print(f"  [{idx:>4}/{TOTAL_REQUESTS}]  {mark}  {r['elapsed_s']}s  id={r['execution_id'] or r['error']}")

    total_time = round(time.perf_counter() - start, 2)

    # ── Summary ──
    codes = Counter(r["status_code"] for r in results)
    elapsed_ok = [r["elapsed_s"] for r in results if r["status_code"] == 202]

    print("\n" + "═" * 60)
    print("📊  RESULTS")
    print("═" * 60)
    print(f"  Total requests   : {TOTAL_REQUESTS}")
    print(f"  Concurrency      : {CONCURRENCY}")
    print(f"  Wall-clock time  : {total_time}s")
    print(f"  Throughput       : {round(TOTAL_REQUESTS / total_time, 1)} req/s")
    print()
    print("  HTTP status breakdown:")
    for code, count in sorted(codes.items(), key=lambda x: str(x[0])):
        print(f"    {code}  →  {count} requests")
    if elapsed_ok:
        print()
        print(f"  Accepted (202) response times:")
        print(f"    min  {min(elapsed_ok)}s")
        print(f"    max  {max(elapsed_ok)}s")
        print(f"    avg  {round(sum(elapsed_ok)/len(elapsed_ok), 3)}s")
    print("═" * 60)

    # Optionally poll status for the first 5 accepted executions
    accepted = [r for r in results if r["execution_id"]][:5]
    if accepted:
        print("\n⏳  Polling status for first 5 accepted executions (up to 30s)…\n")
        token = tokens[0]
        headers = {"Authorization": f"Bearer {token}"}
        for r in accepted:
            eid = r["execution_id"]
            for _ in range(10):
                time.sleep(3)
                try:
                    sr = requests.get(f"{BASE_URL}/api/executions/{eid}/status", headers=headers, timeout=10)
                    data = sr.json()
                    st = data.get("status", "?")
                    print(f"  {eid}  →  {st}")
                    if st not in ("PENDING", "RUNNING", "QUEUED"):
                        break
                except Exception as e:
                    print(f"  {eid}  →  poll error: {e}")
                    break


if __name__ == "__main__":
    run_load_test()






