"""
Simple Concurrency Load Test for Execution Engine Service
=========================================================
Usage:
    pip install requests
    python load_test.py

Runs in continuous waves until you press Ctrl+C.
Tokens are auto-fetched from GET /api/dev/token?userId=<N>
No manual JWT setup needed — just make sure the app is running.
"""

import concurrent.futures
import time
import requests
import signal
import sys
from collections import Counter

# ─────────────────────────── CONFIG ───────────────────────────
BASE_URL          = "http://localhost:8080"

BATCH_SIZE        = 20    # requests fired per wave
CONCURRENCY       = 10    # parallel workers (also = number of virtual users)
DELAY_BETWEEN_WAVES = 1   # seconds to wait between waves (0 = fire as fast as possible)
PROBLEM_ID     = 3     # a valid problemId in your DB
LANGUAGE       = "JAVA"
MODE           = "submit" # "run" = sample tests only (faster); "submit" = all tests

# A minimal Java solution — tweak to match problemId's expected output
SOURCE_CODE = """
import java.util.*;
import java.util.stream.*;
public class Solution {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = Integer.parseInt(sc.nextLine().trim());
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            String[] parts = sc.nextLine().trim().split(" ");
            scores.put(parts[0], Integer.parseInt(parts[1]));
        }
        scores.entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry<String, Integer>::getValue)
                .reversed()
                .thenComparing(Map.Entry::getKey))
            .map(Map.Entry::getKey)
            .forEach(System.out::println);
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
    tokens = fetch_tokens(CONCURRENCY)

    grand_total   = 0
    grand_codes   = Counter()
    grand_elapsed = []
    wave          = 0
    test_start    = time.perf_counter()

    print(f"\n🚀  Continuous load test  |  batch={BATCH_SIZE}  concurrency={CONCURRENCY}")
    print("    Press Ctrl+C to stop and see the final summary.\n")

    try:
        while True:
            wave += 1
            wave_results = []
            wave_start = time.perf_counter()

            with concurrent.futures.ThreadPoolExecutor(max_workers=CONCURRENCY) as pool:
                futures = {pool.submit(submit, grand_total + i, tokens): i for i in range(BATCH_SIZE)}
                for future in concurrent.futures.as_completed(futures):
                    r = future.result()
                    wave_results.append(r)
                    status = r["status_code"]
                    mark = "✅" if status == 202 else ("⚠️  429" if status == 429 else f"❌  {status}")
                    print(f"  [wave {wave:>4} | #{grand_total + len(wave_results):>6}]  {mark}  {r['elapsed_s']}s  id={r['execution_id'] or r['error']}")

            wave_time  = time.perf_counter() - wave_start
            grand_total += len(wave_results)
            for r in wave_results:
                grand_codes[r["status_code"]] += 1
                if r["status_code"] == 202:
                    grand_elapsed.append(r["elapsed_s"])

            ok           = sum(1 for r in wave_results if r["status_code"] == 202)
            wave_rps     = round(len(wave_results) / wave_time, 1)
            elapsed      = round(time.perf_counter() - test_start, 1)
            overall_rps  = round(grand_total / elapsed, 1) if elapsed > 0 else 0
            print(f"\n  ── wave {wave} done in {round(wave_time,2)}s"
                  f"  |  {wave_rps} req/s this wave"
                  f"  |  {overall_rps} req/s overall"
                  f"  |  accepted={ok}/{BATCH_SIZE}"
                  f"  |  total={grand_total}"
                  f"  |  elapsed={elapsed}s ──\n")

            if DELAY_BETWEEN_WAVES > 0:
                time.sleep(DELAY_BETWEEN_WAVES)

    except KeyboardInterrupt:
        pass

    # ── Final Summary ──
    total_time = round(time.perf_counter() - test_start, 2)
    print("\n" + "═" * 60)
    print("📊  FINAL RESULTS  (stopped by Ctrl+C)")
    print("═" * 60)
    print(f"  Waves completed  : {wave}")
    print(f"  Total requests   : {grand_total}")
    print(f"  Concurrency      : {CONCURRENCY}")
    print(f"  Wall-clock time  : {total_time}s")
    print(f"  Throughput       : {round(grand_total / total_time, 1)} req/s")
    print()
    print("  HTTP status breakdown:")
    for code, count in sorted(grand_codes.items(), key=lambda x: str(x[0])):
        print(f"    {code}  →  {count} requests")
    if grand_elapsed:
        print()
        print(f"  Accepted (202) response times:")
        print(f"    min  {min(grand_elapsed)}s")
        print(f"    max  {max(grand_elapsed)}s")
        print(f"    avg  {round(sum(grand_elapsed)/len(grand_elapsed), 3)}s")
    print("═" * 60)


if __name__ == "__main__":
    run_load_test()
