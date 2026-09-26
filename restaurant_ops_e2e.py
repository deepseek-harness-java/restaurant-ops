#!/usr/bin/env python3
"""restaurant-ops E2E：通过业务应用 SSE 代理调用 DSH Agent，验证工具全链路。"""
import json, subprocess, sys

AGENT = "restaurant-copilot"
URL = "http://127.0.0.1:18092/api/assistant/stream"

CASES = [
    ("T1 桌台订单", "餐厅今天有哪些订单和桌台情况？简洁回答", ["o101", "包间"]),
    ("T2 订单详情", "查一下餐厅订单 o101 的消费明细，简洁回答", ["o101", "桌"]),
    ("T3 菜品查询", "餐厅招牌菜有哪些？哪些菜卖得最好？简洁回答", ["酸梅汤", "炒饭"]),
    ("T4 库存预警", "餐厅食材库存有哪些预警？简洁回答", ["库存", "鲈鱼"]),
    ("T5 营业统计", "餐厅今天营业情况怎么样？翻台率多少？简洁回答", ["流水", "翻台率"]),
]

def ask(message, timeout=170):
    payload = json.dumps({"message": message}, ensure_ascii=False)
    try:
        out = subprocess.run(
            ["curl", "-s", "--noproxy", "*", "-N", "-X", "POST", URL,
             "-H", "Content-Type: application/json", "-d", payload,
             "--max-time", str(timeout)],
            capture_output=True, text=True, timeout=timeout + 10).stdout
    except Exception as e:
        return "", f"curl 异常: {e}"
    text = []
    ev = ""
    for line in out.splitlines():
        line = line.rstrip("\r")
        if line.startswith("event:"):
            ev = line[6:].strip()
        elif line.startswith("data:"):
            s = line[5:].strip()
            if not s or s == "[DONE]" or ev != "chunk":
                continue
            try:
                j = json.loads(s)
                c = j.get("content", "")
                if c:
                    text.append(c)
            except Exception:
                pass
            ev = ""
    return "".join(text), out

def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    cases = CASES if not only else [c for c in CASES if c[0].startswith(only)]
    passed, failed = 0, []
    for name, q, keys in cases:
        reply, raw = ask(q)
        ok = all(k in reply for k in keys)
        print(f"[{'PASS' if ok else 'FAIL'}] {name}\n  Q: {q}\n  A: {reply[:200]}")
        if ok:
            passed += 1
        else:
            failed.append(name)
            if not reply:
                print(f"  raw 首行: {raw.splitlines()[:3] if raw else '(空)'}")
    print(f"\n===== restaurant-ops E2E: {passed}/{len(cases)} PASS =====")

if __name__ == "__main__":
    main()
