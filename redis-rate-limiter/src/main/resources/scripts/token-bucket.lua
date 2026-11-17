-- =========================================================================
-- 토큰 버킷 Rate Limiter (최소 버전)
--
-- [변수 이름표]
--   key                   : 버킷 키 (클라이언트별)
--   capacity              : 최대 토큰 수
--   refillTokensPerSecond : 초당 충전량 (토큰/초)
--   nowMillis             : 현재 시각(ms)
--   storedTokens          : 저장돼 있던 토큰 수
--   lastRefillAt          : 마지막 계산 시각(ms)
--   elapsedSeconds        : 경과 시간(초)
--   refilledTokens        : 충전 후 토큰 수
--   allowed               : 허용 여부 (1 허용 / 0 거절)
--   remainingTokens       : 소비 후 남은 토큰 수
-- =========================================================================

local MILLIS_PER_SECOND = 1000                                    -- 1초 = 1000ms
local TTL_MILLIS = 60 * 60 * MILLIS_PER_SECOND                    -- 요청이 없으면 1시간 뒤 키 삭제 (메모리 정리용)


-- -------------------------------------------------------------------------
-- 1. 입력값 읽기
-- -------------------------------------------------------------------------
local key = KEYS[1]                                               -- 예: "rate-limit:token-bucket:{clientId}"
local capacity = tonumber(ARGV[1])                                -- 예: 10  (버킷 최대 토큰 수)
local refillTokensPerSecond = tonumber(ARGV[2])                   -- 예: 1   (초당 1개 충전 → 10초면 가득 참)


-- -------------------------------------------------------------------------
-- 2. 현재 시각과 기존 버킷 상태를 읽는다.
-- -------------------------------------------------------------------------
local time = redis.call('TIME')                                   -- {초, 마이크로초}
-- 예: {1731400000, 123456} → 1731400000 * 1000 + 123 = 1731400000123 (ms)
local nowMillis = tonumber(time[1]) * MILLIS_PER_SECOND + math.floor(tonumber(time[2]) / 1000)

local state = redis.call('HMGET', key, 'tokens', 'lastRefillAt')  -- 해시에서 두 필드를 한 번에 읽음
local storedTokens = tonumber(state[1]) or capacity               -- 없으면 = 첫 요청 → 가득 찬 버킷
local lastRefillAt = tonumber(state[2]) or nowMillis              -- 없으면 지금


-- -------------------------------------------------------------------------
-- 3. 흐른 시간만큼 충전한다.
-- -------------------------------------------------------------------------
-- 예: (13000 - 10000) / 1000 = 3초
--    시계가 뒤로 가서 음수가 나오면 0초로 본다.
local elapsedSeconds = math.max(0, nowMillis - lastRefillAt) / MILLIS_PER_SECOND

-- 예: 잔량 2개 + 3초 * 초당 1개 = 5개
--    잔량 8개 + 5초 * 1개 = 13개 → 최대치 10개로 자름
local refilledTokens = math.min(capacity, storedTokens + elapsedSeconds * refillTokensPerSecond)


-- -------------------------------------------------------------------------
-- 4. 1개 이상 있으면 1개 소비하고 허용, 없으면 거절한다.
-- -------------------------------------------------------------------------
local allowed, remainingTokens
if refilledTokens >= 1 then
    allowed = 1                                                   -- 허용
    remainingTokens = refilledTokens - 1                          -- 토큰 1개 차감
else
    allowed = 0                                                   -- 거절
    remainingTokens = refilledTokens                              -- 차감 없이 그대로
end


-- -------------------------------------------------------------------------
-- 5. 상태 저장 (거절돼도 저장해야 충전된 소수 토큰이 사라지지 않는다.)
-- -------------------------------------------------------------------------
redis.call('HSET', key, 'tokens', tostring(remainingTokens), 'lastRefillAt', tostring(nowMillis)) -- 토큰 수·계산 시각 저장
redis.call('PEXPIRE', key, TTL_MILLIS)                            -- 만료 시간 갱신


-- -------------------------------------------------------------------------
-- 6. 응답: {허용 여부, 남은 토큰(정수로 내림)}
-- -------------------------------------------------------------------------
return {allowed, math.floor(remainingTokens)}
