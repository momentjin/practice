"""백엔드, Redis, 두 Gateway 실행 후 python3 scripts/verify_gateway_rate_limit.py.
표준 라이브러리만 사용하며 테스트마다 새 사용자 ID를 사용합니다.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import math
import time
import uuid
from urllib.error import HTTPError
from urllib.request import Request, urlopen


def request(base, client=None):
    headers = {} if client is None else {"X-Client-Id": client}
    req = Request(base + "/api/practice/ping", headers=headers)
    try:
        response = urlopen(req, timeout=10)
    except HTTPError as error:
        response = error
    with response:
        body = response.read()
        if response.status == 200:
            data = json.loads(body)
            assert data["clientId"] == client, data
            assert "requestId" in data, data
            assert response.headers.get("X-RateLimit-Remaining") is not None
        return response.status


def verify(base, shared_client):
    # WebFlux 내장 리미터는 초 단위이므로 경계에 걸리면 도중에 충전될 수 있습니다.
    started = time.monotonic()
    statuses = [request(base, shared_client) for _ in range(30)]
    elapsed = time.monotonic() - started
    assert statuses[:10] == [200] * 10, statuses
    assert 429 in statuses and set(statuses) <= {200, 429}, statuses
    assert statuses.count(200) <= 10 + math.ceil(elapsed), statuses
    assert request(base, "isolated-" + str(uuid.uuid4())) == 200
    time.sleep(1.1)
    assert request(base, shared_client) == 200, "시간 경과 후 충전 실패"
    assert request(base) == 403, "사용자 키 없는 요청이 차단되지 않음"
    assert request(base, " ") == 403, "공백 사용자 키가 차단되지 않음"

    concurrent_client = "concurrent-" + str(uuid.uuid4())
    started = time.monotonic()
    with ThreadPoolExecutor(max_workers=16) as executor:
        concurrent = list(executor.map(lambda _: request(base, concurrent_client), range(40)))
    elapsed = time.monotonic() - started
    assert set(concurrent) <= {200, 429}, concurrent
    assert 10 <= concurrent.count(200) <= 10 + math.ceil(elapsed), concurrent
    assert 429 in concurrent, concurrent
    print(f"{base}: burst / 429 / refill / user isolation / missing key / concurrent OK")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--webflux", default="http://localhost:8081")
    parser.add_argument("--mvc", default="http://localhost:8082")
    args = parser.parse_args()
    # 같은 사용자로 두 Gateway를 검사: 한쪽의 소비가 다른 쪽을 소진하면 실패합니다.
    shared_client = "gateway-test-" + str(uuid.uuid4())
    verify(args.webflux.rstrip("/"), shared_client)
    verify(args.mvc.rstrip("/"), shared_client)
    print("두 Gateway의 버킷 분리 확인 완료")
