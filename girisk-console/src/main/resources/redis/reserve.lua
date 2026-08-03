-- 原子预占：校验本单返彩 < bMax 后写入 PENDING reserve，并临时累加 payout（带 TTL 由外层 expire）
-- KEYS[1] = risk:reserve:{orderId}
-- KEYS[2] = sports:payout:{match}:{market}:{line}:{selection}
-- ARGV: orderId, payout, bMax, ttlSec, matchCode, marketType, line, selection, stakeYuan
-- 返回: 0=ok, 1=duplicate, 2=over limit

if redis.call('EXISTS', KEYS[1]) == 1 then
  return 1
end

local payout = tonumber(ARGV[2])
local bMax = tonumber(ARGV[3])
if payout == nil or bMax == nil then
  return 2
end
-- 边界：payout >= bMax 拒
if payout >= bMax then
  return 2
end

local ttl = tonumber(ARGV[4]) or 30
redis.call('HSET', KEYS[1],
  'orderId', ARGV[1],
  'payoutYuan', ARGV[2],
  'stakeYuan', ARGV[9],
  'matchCode', ARGV[5],
  'marketType', ARGV[6],
  'line', ARGV[7],
  'selection', ARGV[8],
  'status', 'PENDING')
redis.call('EXPIRE', KEYS[1], ttl)

-- 预留期间累加 payout，TTL 到期后需由应用层清理；此处同步加到盘口以便并发可见
redis.call('INCRBYFLOAT', KEYS[2], payout)
redis.call('EXPIRE', KEYS[2], 604800)

return 0
