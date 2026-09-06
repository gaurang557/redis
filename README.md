# MiniRedis

A Java 25 in-memory key-value server implementing a small, binary-safe RESP2 subset.
It accepts Redis client connections directly; a separate Redis server is not needed.

## Build and run

Requires JDK 25 and Maven.

```sh
mvn clean package
java -jar target/miniredis.jar
```

Default address: `127.0.0.1:6379`. Optional arguments are port and bind address:

```sh
java -jar target/miniredis.jar 6380 127.0.0.1
```

The source root remains the project directory, with Maven compiling only `com/**/*.java`.
Tests live separately under `test/com/`.

## redis-cli

With `redis-cli` installed, open another terminal:

```sh
redis-cli -2 -h 127.0.0.1 -p 6379
```

```text
PING
SET gaurang 20
GET gaurang
INCR gaurang
SET greeting "hello world"
GET greeting
EXPIRE gaurang 60
TTL gaurang
DEL gaurang
```

`PING` returns `PONG`, `GET gaurang` initially returns `"20"`, and `INCR` returns `21`.
Missing keys return a null bulk string, displayed as `(nil)`; empty values remain empty strings.
Commands are case-insensitive; keys are case-sensitive. Values and keys can contain arbitrary bytes.
The previous inline netcat command syntax is no longer supported. Use `redis-cli` or send RESP frames.
CLI command metadata/autocompletion (`COMMAND`) is not implemented; supported commands still execute.

## Connect from an application

Install the Python client in your virtual environment:

```sh
python3 -m venv .venv
.venv/bin/pip install redis==7.3.0
.venv/bin/python examples/redis_client.py
```

```python
import redis

client = redis.Redis(
    host="127.0.0.1", port=6379, db=0, protocol=2,
    decode_responses=True, socket_connect_timeout=3, socket_timeout=3,
)
client.set("gaurang", "20")
print(client.get("gaurang"))  # 20
print(client.incr("gaurang"))  # 21
client.close()
```

Use `decode_responses=False` for arbitrary binary values. Select RESP2 and database 0;
leave username/password unset. Other Redis libraries need equivalent settings and must
only use the commands below. RESP2 support is not a claim of full Redis compatibility.

Python pipelines must use `client.pipeline(transaction=False)` because `MULTI`/`EXEC`
transactions are not supported. `client.incr()` uses `INCRBY`, which is implemented.

For another machine on a trusted private network, bind to your LAN address and configure
the client with that address instead of `localhost`:

```sh
java -jar target/miniredis.jar 6379 192.168.1.50
```

Replace the example with an address assigned to your machine and allow that port in your
firewall. There is no authentication or TLS; use a private network or SSH tunnel rather
than exposing this service publicly.

## Supported commands

| Command | Supported form / behavior |
| --- | --- |
| PING | `PING [message]` |
| ECHO | `ECHO message` |
| SET | `SET key value`; clears previous TTL; no options |
| GET | `GET key`; null for missing or expired keys |
| DEL | `DEL key [key ...]`; count of live keys removed |
| INCR / INCRBY | Signed 64-bit integer operations; preserve TTL; reject invalid numbers/overflow |
| EXPIRE | `EXPIRE key seconds`; 0 for missing keys, 1 for success; nonpositive timeout deletes |
| TTL | Remaining seconds; -1 without expiration, -2 when missing/expired |
| SELECT | Database 0 only |
| HELLO | `HELLO` or `HELLO 2`; minimal server/version/protocol fields; RESP3/options rejected |
| CLIENT | `SETINFO LIB-NAME/LIB-VER value`, `SETNAME name`, `GETNAME`; metadata held per connection |
| QUIT | Replies OK and closes the connection |

Unknown commands and unsupported options return RESP errors. Command errors leave the
connection usable; malformed protocol frames return a protocol error and close that connection.

## Implementation and limits

`RespReader` reads byte-counted argument arrays; `RespWriter` writes typed RESP replies.
The connection loop supports fragmented input and pipelining. Requests allow at most
1,024 arguments and 8 MiB of combined argument payload, with bounded numeric headers.

`MiniRedis` keeps values and expiration together. A single synchronized store lock makes
operations atomic, including concurrent increments. Byte-array copies prevent callers from
mutating stored keys or values. Expiration is lazy: expired entries are removed on access.
There is no background reclamation, persistence, memory budget, connection limit, or idle
timeout. Data is lost when the process stops. RESP3, AUTH, multiple databases, transactions,
pub/sub, clustering and advanced SET/EXPIRE options are not implemented.

## Tests

```sh
mvn test
# After building the JAR and installing redis-py:
.venv/bin/python test/redis_client_smoke.py
```

JUnit tests cover framing, fragmentation, malformed/oversized requests, exact response bytes,
binary data, expiration, numeric failures, concurrent increments, and real TCP connections.
The Python smoke test starts its own packaged server on an ephemeral loopback port and checks
a real Redis client's initialization, commands, pipelining, concurrency and the example script.

Protocol reference: https://redis.io/docs/latest/develop/reference/protocol-spec/
