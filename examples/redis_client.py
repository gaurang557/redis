"""Run against MiniRedis: python3 examples/redis_client.py [host] [port]."""
import sys
import redis

with redis.Redis(
    host=sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1",
    port=int(sys.argv[2]) if len(sys.argv) > 2 else 6379,
    protocol=2,
    decode_responses=True,
    socket_connect_timeout=3,
    socket_timeout=3,
) as client:
    print("PING:", client.ping())
    client.set("gaurang", "20")
    print("GET:", client.get("gaurang"))
    print("INCR:", client.incr("gaurang"))
    client.expire("gaurang", 60)
    print("TTL:", client.ttl("gaurang"))

    # Ordinary pipelining is supported; MULTI/EXEC transactions are not.
    with client.pipeline(transaction=False) as pipeline:
        pipeline.set("greeting", "hello world")
        pipeline.get("greeting")
        print("Pipeline:", pipeline.execute())
