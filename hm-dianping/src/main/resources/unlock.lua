-- 比较当前线程标识与redis中存储的线程标识是否一致
if(redis.call('get', KEYS[1]) == ARGV[1]) then
    return redis.call('del', KEYS[1])
end
return 0