"""
Simple Concurrency Load Test for Execution Engine Service
=========================================================
Usage:
    pip install requests
    python load_test.py

Runs in continuous waves until you press Ctrl+C.
Tracks final verdicts (ACCEPTED / WRONG_ANSWER / etc) via status polling.
Tokens are auto-fetched from GET /api/dev/token?userId=<N>
"""

import concurrent.futures
import threading
import time
import queue
import requests
import subprocess
from collections import Counter

# ─────────────────────────── CONFIG ───────────────────────────
BASE_URL             = "http://localhost:8080"

BATCH_SIZE           = 50   # requests fired per wave  (30 req/s burst)
CONCURRENCY          = 50   # parallel workers (= number of virtual users)
DELAY_BETWEEN_WAVES  = 1     # 1s pause between waves → ~30 req/s sustained burst rate
POLL_CONTAINER_TRACE = True  # show which sandbox container handled each execution
RESULT_POLL_WORKERS  = 10    # background threads polling final verdicts
RESULT_POLL_TIMEOUT  = 30    # max seconds to wait for a verdict before giving up

PROBLEM_ID = 3
LANGUAGE   = "JAVA"
MODE       = "submit"  # "run" = sample tests only; "submit" = all test cases

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


# ── Shared verdict tracking (thread-safe) ──────────────────────
verdict_counter  = Counter()
verdict_lock     = threading.Lock()
result_queue     = queue.Queue()   # (execution_id, token) tuples to resolve


def poll_result_worker():
    """
    Background worker: polls /api/executions/{id}/status until terminal state.
    When COMPLETED the endpoint now returns verdict + score + totalRuntimeMs directly
    from the Redis KV (same data pushed via WebSocket), so no second DB call needed.
    """
    while True:
        item = result_queue.get()
        if item is None:
            break
        execution_id, token = item
        headers = {"Authorization": f"Bearer {token}"}
        deadline = time.perf_counter() + RESULT_POLL_TIMEOUT
        verdict = "TIMEOUT"

        while time.perf_counter() < deadline:
            try:
                r = requests.get(
                    f"{BASE_URL}/api/executions/{execution_id}/status",
                    headers=headers, timeout=5
                )
                if r.ok:
                    data   = r.json()
                    status = data.get("status", "")
                    if status in ("COMPLETED", "FAILED"):
                        verdict = data.get("verdict") or status
                        score   = data.get("score", "?")
                        rt_ms   = data.get("totalRuntimeMs", "?")
                        mark    = "✔" if verdict == "ACCEPTED" else "✘"
                        print(f"  {mark} {execution_id[:8]}  {verdict:<25}  score={score}  {rt_ms}ms")
                        break
                time.sleep(0.5)
            except Exception:
                time.sleep(1)
        else:
            print(f"  ✘ {execution_id[:8]}  TIMEOUT  (no result in {RESULT_POLL_TIMEOUT}s)")

        with verdict_lock:
            verdict_counter[verdict] += 1
        result_queue.task_done()


def trace_containers_from_logs(execution_ids: list):
    try:
        result = subprocess.run(
            ["docker", "logs", "--tail", "1000", "codeval-execution-engine"],
            capture_output=True, text=True, timeout=5
        )
        logs = result.stdout + result.stderr
        print("  [Container trace]")
        found = 0
        for eid in execution_ids:
            for line in logs.splitlines():
                if "Acquired container" in line and eid in line:
                    parts = line.split("Acquired container ")
                    if len(parts) > 1:
                        cid = parts[1].split(" ")[0][:12]
                        print(f"    {eid[:8]}  ->  container {cid}")
                        found += 1
                        break
        if found == 0:
            print("    (executions still queued in Kafka)")
    except Exception as e:
        print(f"    (container trace unavailable: {e})")


def fetch_tokens(n: int) -> list:
    print(f"Fetching {n} dev tokens from {BASE_URL}/api/dev/token ...")
    tokens = []
    for user_id in range(1, n + 1):
        resp = requests.get(f"{BASE_URL}/api/dev/token", params={"userId": user_id}, timeout=10)
        resp.raise_for_status()
        token = resp.json().get("token") or resp.text.strip().strip('"')
        tokens.append(token)
    print(f"   Got {len(tokens)} tokens\n")
    return tokens


def submit(task_id: int, tokens: list) -> dict:
    token = tokens[task_id % len(tokens)]
    headers = {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}
    payload = {"problemId": PROBLEM_ID, "language": LANGUAGE, "mode": MODE, "sourceCode": SOURCE_CODE}
    t0 = time.perf_counter()
    try:
        resp = requests.post(f"{BASE_URL}/api/executions", json=payload, headers=headers, timeout=30)
        elapsed = time.perf_counter() - t0
        eid = resp.json().get("executionId") if resp.ok else None
        # Queue for result polling
        if eid:
            result_queue.put((eid, token))
        return {
            "task_id": task_id, "status_code": resp.status_code,
            "elapsed_s": round(elapsed, 3), "execution_id": eid,
            "error": None if resp.ok else resp.text[:200],
        }
    except Exception as exc:
        return {
            "task_id": task_id, "status_code": None,
            "elapsed_s": round(time.perf_counter() - t0, 3),
            "execution_id": None, "error": str(exc),
        }


def run_load_test():
    tokens = fetch_tokens(CONCURRENCY)

    # Start background result-polling threads
    poll_threads = []
    for _ in range(RESULT_POLL_WORKERS):
        t = threading.Thread(target=poll_result_worker, daemon=True)
        t.start()
        poll_threads.append(t)

    grand_total   = 0
    grand_codes   = Counter()
    grand_elapsed = []
    wave          = 0
    test_start    = time.perf_counter()

    print(f"\nContinuous load test  |  batch={BATCH_SIZE}  concurrency={CONCURRENCY}  result_poll_workers={RESULT_POLL_WORKERS}")
    print("Press Ctrl+C to stop.\n")

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
                    mark = "[202]" if status == 202 else (f"[429]" if status == 429 else f"[{status}]")
                    print(f"  {mark}  {r['elapsed_s']}s  id={r['execution_id'] or r['error']}")

            wave_time = time.perf_counter() - wave_start
            grand_total += len(wave_results)
            for r in wave_results:
                grand_codes[r["status_code"]] += 1
                if r["status_code"] == 202:
                    grand_elapsed.append(r["elapsed_s"])

            ok          = sum(1 for r in wave_results if r["status_code"] == 202)
            wave_rps    = round(len(wave_results) / wave_time, 1)
            elapsed     = round(time.perf_counter() - test_start, 1)
            overall_rps = round(grand_total / elapsed, 1) if elapsed > 0 else 0

            with verdict_lock:
                vc = dict(verdict_counter)
            verdict_summary = "  ".join(f"{k}={v}" for k, v in sorted(vc.items())) or "pending..."

            print(f"\n  -- wave {wave} done in {round(wave_time,2)}s"
                  f"  |  {wave_rps} req/s this wave  |  {overall_rps} req/s overall"
                  f"  |  accepted={ok}/{BATCH_SIZE}  |  total={grand_total}")
            print(f"     verdicts so far: {verdict_summary}")
            print(f"     queue pending:   {result_queue.qsize()} executions awaiting result\n")

            if POLL_CONTAINER_TRACE and wave % 2 == 0:
                accepted_ids = [r["execution_id"] for r in wave_results if r["execution_id"]][:5]
                if accepted_ids:
                    trace_containers_from_logs(accepted_ids)

            if DELAY_BETWEEN_WAVES > 0:
                time.sleep(DELAY_BETWEEN_WAVES)

    except KeyboardInterrupt:
        pass

    # Drain remaining results (up to 30s)
    print("\nWaiting for remaining results to resolve (up to 30s)...")
    try:
        result_queue.join()
    except Exception:
        pass

    # Stop poll workers
    for _ in poll_threads:
        result_queue.put(None)

    # ── Final Summary ──
    total_time = round(time.perf_counter() - test_start, 2)
    with verdict_lock:
        final_verdicts = dict(verdict_counter)

    print("\n" + "=" * 65)
    print("FINAL RESULTS  (Ctrl+C received)")
    print("=" * 65)
    print(f"  Waves completed  : {wave}")
    print(f"  Total requests   : {grand_total}")
    print(f"  Concurrency      : {CONCURRENCY}")
    print(f"  Wall-clock time  : {total_time}s")
    print(f"  Throughput       : {round(grand_total / total_time, 1)} req/s")
    print()
    print("  HTTP status breakdown (submit response):")
    for code, count in sorted(grand_codes.items(), key=lambda x: str(x[0])):
        print(f"    {code}  ->  {count} requests")
    if grand_elapsed:
        print()
        print(f"  Submit response times (202 only):")
        print(f"    min  {min(grand_elapsed)}s")
        print(f"    max  {max(grand_elapsed)}s")
        print(f"    avg  {round(sum(grand_elapsed)/len(grand_elapsed), 3)}s")
    print()
    print("  Execution verdicts (actual results):")
    total_resolved = sum(final_verdicts.values())
    for verdict, count in sorted(final_verdicts.items(), key=lambda x: -x[1]):
        pct = round(count * 100 / total_resolved, 1) if total_resolved else 0
        print(f"    {verdict:<25} {count:>5}  ({pct}%)")
    unresolved = grand_total - total_resolved
    if unresolved > 0:
        print(f"    {'UNRESOLVED (no result yet)':<25} {unresolved:>5}")
    print("=" * 65)


if __name__ == "__main__":
    run_load_test()
