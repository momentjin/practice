-- 시간 처리 ---------------------------------------------------------------
local MILLIS_PER_SECOND = 1000

local function nowMillis()
    local time = redis.call('TIME')
    local seconds = tonumber(time[1])
    local microseconds = tonumber(time[2])
    return seconds * MILLIS_PER_SECOND + math.floor(microseconds / 1000)
end

local function elapsedSeconds(previousMillis, currentMillis)
    return (currentMillis - previousMillis) / MILLIS_PER_SECOND
end

local function secondsToMillisCeil(seconds)
    return math.ceil(seconds * MILLIS_PER_SECOND)
end

-- 버킷 계산: Redis 접근이나 현재 시각 조회 없이 숫자만 계산한다. -----------
local function refillTokens(tokens, elapsed, capacity, refillRate)
    return math.min(capacity, tokens + elapsed * refillRate)
end

local function consumeTokens(tokens, requested)
    if tokens < requested then
        return 0, tokens
    end
    return 1, tokens - requested
end

local function secondsUntilAvailable(tokens, requested, refillRate)
    return math.max(0, requested - tokens) / refillRate
end

-- Redis 상태 읽기 / 저장 -------------------------------------------------
local function loadBucket(key, capacity, now)
    local state = redis.call('HMGET', key, 'tokens', 'lastRefillAt')
    -- 첫 요청은 가득 찬 버킷으로 시작한다.
    return tonumber(state[1]) or capacity, tonumber(state[2]) or now
end

local function saveBucket(key, tokens, updatedAt, ttlMillis)
    redis.call('HSET', key, 'tokens', tostring(tokens), 'lastRefillAt', tostring(updatedAt))
    redis.call('PEXPIRE', key, ttlMillis)
end

-- 실행 흐름 ---------------------------------------------------------------
-- KEYS[1]: 사용자별 키 / ARGV: 최대 용량, 초당 충전량, 요청 소비량

local key = KEYS[1]
local capacity = ARGV[1]
local refillRate = ARGV[2]
local requested = ARGV[3]

if not capacity or not refillRate or not requested
    or capacity <= 0 or refillRate <= or requested <= 0
    or requested > capacity then
        return redis.error_reply('Invalid token bucket policy')
end

local now = nowMillis()
local tokens = toNumber(redis.call('HGET', key, 'tokens')) or capacity
local lastRefillAt = toNumber(redis.call('HGET', key, 'lastRefillAt')) or now

local updatedAt = math.max(now, lastRefillAt)
local clockDelayMillis = updatedAt - now
local elapsed = (updatedAt - lastRefillAt) / MILLIS_PER_SECOND

local retryAfterMillis = 0


