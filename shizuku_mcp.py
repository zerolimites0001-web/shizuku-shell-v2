#!/usr/bin/env python3
"""shizuku_mcp.py — ponte MCP (Model Context Protocol) local para o Shizuku Shell.

Uma IA (ou você no Termux) chama ferramentas MCP; este script traduz para
broadcasts Android que o app Shizuku Shell executa via Shizuku (root/adb).

Ferramentas:
  run(cmd, timeout)         -> executa comando shell, retorna stdout/stderr/exitCode
  run_script(path, timeout) -> executa arquivo .sh, retorna saída
  write_file(path, content) -> cria script/arquivo de texto no /sdcard
  read_file(path)           -> lê saída anterior

Uso standalone (sem servidor MCP):
  python3 shizuku_mcp.py run "id && whoami"
  python3 shizuku_mcp.py run-script /sdcard/job.sh
  python3 shizuku_mcp.py write /sdcard/job.sh "id\necho oi"

Uso como servidor MCP (stdio) p/ clientes MCP:
  python3 shizuku_mcp.py serve
"""
import json, os, subprocess, sys, tempfile, time

PKG = "com.arena.shizuku_shell_local"
RUN = PKG + ".RUN"
RESULT = PKG + ".RESULT"
OUT = "/sdcard/mcp_last.txt"


def am(cmd_args, timeout=90):
    return subprocess.run(["am"] + cmd_args, capture_output=True, text=True, timeout=timeout)


def run(cmd, timeout=60):
    out = OUT + "." + str(os.getpid())
    try:
        os.remove(out)
    except OSError:
        pass
    am(["broadcast", "-a", RUN, "--es", "cmd", cmd,
        "--es", "out", out, "--ei", "timeout", str(timeout)])
    t0 = time.time()
    while time.time() - t0 < timeout + 15:
        if os.path.exists(out):
            time.sleep(0.3)
            try:
                with open(out) as f:
                    return f.read()
            except OSError as e:
                return "erro lendo saída: " + str(e)
        time.sleep(0.5)
    return "timeout aguardando resultado (app instalado? Shizuku ativo? permissão concedida?)"


def run_script(path, timeout=120):
    r = am(["startservice", "-n", PKG + "/.McpService",
            "--es", "path", path, "--es", "out", OUT, "--ei", "timeout", str(timeout)])
    t0 = time.time()
    while time.time() - t0 < timeout + 15:
        if os.path.exists(OUT) and os.path.getmtime(OUT) >= t0:
            time.sleep(0.3)
            with open(OUT) as f:
                return f.read()
        time.sleep(0.5)
    return "timeout aguardando resultado. am disse: " + (r.stdout or "")[:300]


def write_file(path, content):
    with open(path, "w") as f:
        f.write(content)
    return "escrito %d bytes em %s" % (len(content), path)


def read_file(path):
    with open(path) as f:
        return f.read()


# --- servidor MCP mínimo via stdio (JSON-RPC) ---
TOOLS = [
    {"name": "run", "description": "Executa comando shell via Shizuku (root/adb). Suporta criar scripts, rodar, etc.",
     "inputSchema": {"type": "object", "properties": {
         "cmd": {"type": "string"}, "timeout": {"type": "integer", "default": 60}}, "required": ["cmd"]}},
    {"name": "run_script", "description": "Executa arquivo .sh via Shizuku.",
     "inputSchema": {"type": "object", "properties": {
         "path": {"type": "string"}, "timeout": {"type": "integer", "default": 120}}, "required": ["path"]}},
    {"name": "write_file", "description": "Cria/escreve arquivo de texto (ex: script .sh).",
     "inputSchema": {"type": "object", "properties": {
         "path": {"type": "string"}, "content": {"type": "string"}}, "required": ["path", "content"]}},
    {"name": "read_file", "description": "Lê arquivo de texto.",
     "inputSchema": {"type": "object", "properties": {"path": {"type": "string"}}, "required": ["path"]}},
]


def serve():
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            msg = json.loads(line)
        except Exception:
            continue
        mid = msg.get("id")
        method = msg.get("method", "")
        params = msg.get("params", {})

        def resp(result=None, error=None):
            o = {"jsonrpc": "2.0", "id": mid}
            if error is not None:
                o["error"] = error
            else:
                o["result"] = result
            sys.stdout.write(json.dumps(o) + "\n")
            sys.stdout.flush()

        if method == "initialize":
            resp({"protocolVersion": "2024-11-05",
                  "capabilities": {"tools": {}}, "serverInfo": {"name": "shizuku-mcp", "version": "1.1.0"}})
        elif method == "tools/list":
            resp({"tools": TOOLS})
        elif method == "tools/call":
            name = params.get("name")
            args = params.get("arguments", {})
            try:
                if name == "run":
                    out = run(args["cmd"], int(args.get("timeout", 60)))
                elif name == "run_script":
                    out = run_script(args["path"], int(args.get("timeout", 120)))
                elif name == "write_file":
                    out = write_file(args["path"], args["content"])
                elif name == "read_file":
                    out = read_file(args["path"])
                else:
                    resp(error={"code": -32602, "message": "tool desconhecida: " + str(name)})
                    continue
                resp({"content": [{"type": "text", "text": out}]})
            except Exception as e:
                resp(error={"code": -32000, "message": str(e)})
        elif method in ("notifications/initialized", "notifications/cancelled"):
            pass
        else:
            resp(error={"code": -32601, "message": "método: " + method})


if __name__ == "__main__":
    a = sys.argv[1] if len(sys.argv) > 1 else "serve"
    if a == "run":
        print(run(sys.argv[2] if len(sys.argv) > 2 else "id"))
    elif a == "run-script":
        print(run_script(sys.argv[2]))
    elif a == "write":
        print(write_file(sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else ""))
    elif a == "read":
        print(read_file(sys.argv[2]))
    elif a == "serve":
        serve()
    else:
        print(__doc__)
