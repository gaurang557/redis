"""End-to-end test using redis-py and the packaged JAR; starts an isolated server."""
import concurrent.futures
from pathlib import Path
import selectors
import subprocess
import sys
import redis

root = Path(__file__).resolve().parents[1]
server = subprocess.Popen(
    ["java", "-jar", str(root / "target/miniredis.jar"), "0", "127.0.0.1"],
    stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
)
try:
    with selectors.DefaultSelector() as selector:
        selector.register(server.stdout, selectors.EVENT_READ)
        assert selector.select(10), "server did not start within 10 seconds"
    line = server.stdout.readline().strip()
    assert line.startswith("MiniRedis listening on "), line
    port = int(line.rsplit(":", 1)[1])
    with redis.Redis(host="127.0.0.1", port=port, protocol=2,
                     socket_timeout=3, socket_connect_timeout=3,
                     client_name="smoke-test") as client:
        assert client.ping()
        assert client.client_getname() == "smoke-test"
        assert client.execute_command("HELLO", 2) == [b"server", b"miniredis", b"version", b"1.0.0", b"proto", 2]
        for key, value in [(b"binary\xff\x00", bytes(range(256))),
                           ("unicode", "नमस्ते 🌍".encode()),
                           (b"empty", b""), (b"multiline", b"a\r\nb c")]:
            assert client.set(key, value)
            assert client.get(key) == value
        assert client.get("missing") is None
        assert client.ttl("missing") == -2
        assert client.set("counter", "20")
        assert client.incr("counter") == 21
        assert client.execute_command("INCR", "counter") == 22
        assert client.ttl("counter") == -1
        assert client.expire("counter", 60)
        assert 59 <= client.ttl("counter") <= 60
        assert client.expire("counter", 0)
        assert client.get("counter") is None
        assert not client.expire("missing", 10)
        for command in [("DEL",), ("EXPIRE", "empty", "abc"), ("SELECT", 1),
                        ("HELLO", 3), ("SET", "k", "v", "NX"), ("INCR", "unicode")]:
            try:
                client.execute_command(*command)
            except redis.ResponseError:
                pass
            else:
                raise AssertionError(f"Expected an error for {command}")
            assert client.ping(), "command error made the connection unusable"
        with client.pipeline(transaction=False) as pipeline:
            pipeline.set("pipelined", "value")
            pipeline.get("pipelined")
            pipeline.delete("pipelined", "empty")
            assert pipeline.execute() == [True, b"value", 2]
        def increment_many(_):
            for _ in range(100):
                client.incr("concurrent")
        with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
            list(pool.map(increment_many, range(10)))
        assert client.get("concurrent") == b"1000"
    subprocess.run([sys.executable, str(root / "examples/redis_client.py"), "127.0.0.1", str(port)],
                   check=True, timeout=15)
    print(f"redis-py {redis.__version__}: binary, Unicode, handshakes, errors, pipeline, TTL, concurrency and example passed")
finally:
    server.terminate()
    try:
        server.wait(timeout=5)
    except subprocess.TimeoutExpired:
        server.kill()
        server.wait()
