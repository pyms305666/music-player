"""扫描全部磁盘条目，读取 PROJECT_GUIDE 的职责索引，生成可离线使用的 HTML。

只使用 Python 标准库。扫描隐藏目录和空目录，不展开压缩包、不跟随目录链接；
读取的是路径和文件属性，不读取凭据、数据库、用户音频或第三方文件内容。
生成失败时保留旧附录；--check 只核对覆盖，不修改附录。
"""

from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import subprocess
import sys

START = "<!-- inventory-index:start -->"
END = "<!-- inventory-index:end -->"
CATEGORIES = (
    "项目源码",
    "测试与诊断",
    "配置与资源",
    "项目说明",
    "工具与依赖",
    "缓存与构建",
    "发布附件",
    "版本与环境",
    "其他",
)
DIAGNOSTIC_DIRS = (
    "performance",
    "playlist-diagnostic",
    "playlist-qa",
    "playlist-verifier",
    "release-4.2.3",
    "signing-qa",
)


@dataclass(frozen=True)
class Role:
    """主说明人工索引的一行；用途及关联不会由扫描器自行猜测覆盖。"""

    kind: str
    component: str
    purpose: str
    relation: str
    evidence: str


@dataclass(frozen=True)
class Entry:
    """一个实际磁盘条目；目录与其中的每个文件分别占一项。"""

    path: str
    kind: str
    component: str
    category: str
    purpose: str
    relation: str
    evidence: str
    target: str = ""


def read_roles(guide: Path) -> dict[str, Role]:
    """解析指定标记之间的六列表格；重复路径、坏列和缺少索引都拒绝生成。"""
    source = guide.read_text(encoding="utf-8-sig")
    if source.count(START) != 1 or source.count(END) != 1:
        raise ValueError("主说明必须恰好包含一组 inventory-index 标记")
    block = source.split(START, 1)[1].split(END, 1)[0]
    roles = {}
    for line in block.splitlines():
        if not line.startswith("|"):
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if cells[0] == "路径" or re.fullmatch(r"[-: ]+", cells[0]):
            continue
        if len(cells) != 6:
            raise ValueError("职责索引必须有六列: " + line[:100])
        if not all(cells):
            raise ValueError("职责索引的六个字段均不能为空: " + line[:100])
        relative = cells[0].strip("`")
        if not relative or relative.startswith("/") or ".." in Path(relative).parts:
            raise ValueError("职责索引必须使用项目内相对路径: " + relative)
        if relative in roles:
            raise ValueError("重复职责路径: " + relative)
        roles[relative] = Role(*cells[1:])
    if not roles:
        raise ValueError("职责索引为空")
    return roles


def is_directory_link(path: Path) -> bool:
    """识别符号链接和 Windows junction；任何重解析目录都只记录自身。"""
    if path.is_symlink():
        return True
    attributes = getattr(path.lstat(), "st_file_attributes", 0)
    return bool(attributes & 0x400)


def scan_paths(root: Path) -> dict[str, tuple[str, str]]:
    """用 scandir 扫描真实条目；权限或读取错误直接报告，绝不静默漏项。"""
    paths = {".": ("目录", "")}
    pending = [root]
    while pending:
        directory = pending.pop()
        with os.scandir(directory) as children:
            for child in children:
                path = Path(child.path)
                relative = path.relative_to(root).as_posix()
                directory_entry = child.is_dir(follow_symlinks=False)
                link = child.is_symlink() or (directory_entry and is_directory_link(path))
                if link:
                    # 不访问目标内容；失效链接也必须保留在清单中。
                    try:
                        target = os.readlink(path)
                    except OSError as error:
                        target = "目标读取失败: " + str(error)
                    paths[relative] = (
                        "目录链接" if directory_entry or path.is_dir() else "文件链接",
                        target,
                    )
                elif directory_entry:
                    paths[relative] = ("目录", "")
                    pending.append(path)
                else:
                    paths[relative] = ("文件", "")
    return paths


def independently_scan(root: Path) -> set[str]:
    """用 os.walk 独立核对路径集合，主动剪掉 junction，不复用 scandir 的结果。"""
    found = {"."}

    def fail(error):
        """使 os.walk 的权限或读取错误导致核对失败。"""
        raise error

    for directory, dirs, files in os.walk(root, followlinks=False, onerror=fail):
        base = Path(directory)
        for name in dirs + files:
            found.add((base / name).relative_to(root).as_posix())
        dirs[:] = [name for name in dirs if not is_directory_link(base / name)]
    return found


def authored_paths(root: Path, paths: dict) -> set[str]:
    """要求 Git 文件及已知自写源码位置逐项有说明，工具内部源码使用组件推断。"""
    required = set()
    if (root / ".git").exists():
        result = subprocess.run(
            [
                "git",
                "-C",
                str(root),
                "ls-files",
                "-z",
                "--cached",
                "--others",
                "--exclude-standard",
            ],
            capture_output=True,
            check=True,
        )
        required.update(p for p in result.stdout.decode("utf-8").split("\0") if p in paths)
    for relative, (kind, _) in paths.items():
        if kind != "文件":
            continue
        suffix = Path(relative).suffix.lower()
        if suffix not in {".java", ".py", ".ps1", ".gradle", ".xml", ".css", ".yml", ".pro"}:
            continue
        parts = Path(relative).parts
        if any(part in {"build", "__pycache__", ".gradle"} for part in parts):
            continue
        if parts[0] in {"src", "shared", "scripts", "android-app", ".github"}:
            required.add(relative)
        elif (
            suffix in {".java", ".py", ".ps1"}
            and parts[0] == ".tools"
            and (len(parts) == 2 or (len(parts) >= 3 and parts[1] in DIAGNOSTIC_DIRS))
        ):
            if (
                "/aosp-api33/" not in relative
                and "/native-controls-" not in relative
                and "/androidtransport-jvm/" not in relative
            ):
                required.add(relative)
    # 自写目录（包括空目录）也需要有目录职责；工具内部海量目录按组件解释。
    for relative in list(required):
        parent = Path(relative).parent
        while parent.as_posix() != ".":
            if parent.as_posix() in paths:
                required.add(parent.as_posix())
            parent = parent.parent
    required.add(".")
    return required


def check_roles(paths: dict, roles: dict[str, Role], required: set[str]) -> None:
    """检查自写文件说明遗漏，以及索引中已不存在的旧路径。"""
    missing = sorted(required - roles.keys())
    stale = sorted(roles.keys() - paths.keys())
    if missing or stale:
        raise ValueError(
            "职责索引覆盖失败\n遗漏: " + "\n".join(missing) + "\n已不存在: " + "\n".join(stale)
        )


def component_for(relative: str) -> str:
    """按实际路径确定条目所在模块或工具组件，便于离线筛选。"""
    parts = Path(relative).parts
    if not parts:
        return "项目根目录"
    first = parts[0]
    if first == ".tools":
        return "本机工具 / " + (parts[1] if len(parts) > 1 else ".tools")
    return {
        "src": "桌面端",
        "shared": "共享模块",
        "android-app": "Android 端",
        "scripts": "构建与诊断脚本",
        "docs": "项目文档",
        "release": "历史发布",
        ".git": "Git 仓库",
        ".gradle": "Gradle 缓存",
        "build": "桌面构建与本轮证据",
        ".idea": "IDE 环境",
        ".github": "CI",
        "packaging": "Windows 打包",
        "assets": "品牌素材",
        "gradle": "Gradle Wrapper",
    }.get(first, "项目根目录 / " + first)


def category_for(relative: str, kind: str) -> str:
    """区分源码、测试、依赖和产物；分类仅用于阅读，不决定是否扫描。"""
    if relative.startswith("release/"):
        return "发布附件"
    if relative.startswith(".git/") or relative.startswith((".idea/", ".agents/", ".codex/")):
        return "版本与环境"
    if (
        relative.startswith((".gradle/", "build/", ".wix/", "__pycache__/"))
        or "/build/" in relative
        or "gradle-cache" in relative
    ):
        return "缓存与构建"
    if relative.startswith(".tools/"):
        if (
            any("/" + part + "/" in relative for part in DIAGNOSTIC_DIRS)
            or Path(relative).suffix in {".ps1", ".py"}
            and len(Path(relative).parts) == 2
        ):
            return "测试与诊断"
        return "工具与依赖"
    if "/test/" in relative or "/androidTest/" in relative or relative.startswith("scripts/"):
        return "测试与诊断"
    if relative.startswith("docs/") or Path(relative).suffix == ".md":
        return "项目说明"
    if (
        Path(relative).suffix == ".java"
        or kind == "目录"
        and relative.startswith(("src/", "shared/", "android-app/"))
    ):
        return "项目源码"
    if Path(relative).suffix in {
        ".xml",
        ".css",
        ".png",
        ".ico",
        ".gradle",
        ".properties",
        ".yml",
        ".pro",
        ".bin",
        ".json",
    }:
        return "配置与资源"
    return "其他"


def inferred_role(relative: str, kind: str) -> Role:
    """按组件和文件类型解释工具、缓存与产物；不把未经阅读的细节说成事实。"""
    component = component_for(relative)
    name = Path(relative).name
    suffix = Path(relative).suffix.lower()
    parent = Path(relative).parent.as_posix()
    relation = "位于 " + parent + "；具体内容未逐字核实。"
    evidence = "推断：依据磁盘路径、所属组件和文件类型；未读取文件内容。"
    if kind.endswith("链接"):
        purpose = "链接条目；指向路径见目标字段。为避免重复及越界，扫描不进入目标目录。"
    elif kind == "目录":
        purpose = component + "的子目录；容纳路径下逐项列出的文件和目录，可能为空。"
    else:
        types = {
            ".jar": "Java 类库或打包文件",
            ".class": "Java 编译字节码",
            ".apk": "Android 安装包",
            ".exe": "Windows 可执行程序",
            ".dll": "Windows 原生库",
            ".so": "平台原生共享库",
            ".zip": "ZIP 压缩附件（本清单只记录压缩包自身）",
            ".gz": "压缩数据附件",
            ".jmod": "JDK 模块归档",
            ".dex": "Android 字节码",
            ".aar": "Android 库归档",
            ".pyc": "Python 自动生成字节码",
            ".lock": "工具运行锁或状态标记",
            ".log": "运行或诊断日志",
            ".hprof": "堆转储诊断数据",
            ".db": "数据库数据",
            ".sqlite": "SQLite 数据文件",
            ".xml": "结构化配置、资源或测试报告",
            ".json": "结构化配置、元数据或诊断结果",
            ".html": "HTML 页面或工具报告",
            ".java": "Java 源码或工具携带的源码副本",
            ".py": "Python 工具源码",
            ".ps1": "PowerShell 工具脚本",
            ".properties": "键值配置或工具元数据",
            ".txt": "文本说明、日志或工具列表",
            ".bin": "二进制状态或工具数据",
            ".png": "位图素材或测试截图",
            ".svg": "矢量图形",
            ".mp3": "音频文件",
            ".wav": "音频或播放测试夹具",
            ".lrc": "歌词文本",
            ".pom": "Maven 依赖元数据",
            ".bat": "Windows 命令入口",
            ".sh": "Unix 命令入口",
            ".md": "Markdown 工具说明",
        }
        purpose = component + "中的" + types.get(suffix, "文件") + "：" + name + "。"
    if relative.startswith(".git/"):
        purpose += " Git 用它维护对象、引用、配置或工作区状态；不是应用输入。"
    elif "/aosp-api33/" in relative:
        purpose += " AOSP API 33 通知实现参考资料，属于第三方源码，未修改。"
    elif "/native-controls-" in relative:
        relation = "由 .tools/playlist-verifier/NativeControlsProbe.ps1 和 scripts/PlaylistControlsSmoke.java 生成的历史夹具或结果。"
    elif "/androidtransport-jvm/" in relative:
        relation = (
            "由 .tools/performance/run-androidtransport-jvm.py 生成或编译，用于 JVM 传输回归。"
        )
    elif relative.startswith("build/readability/originals/"):
        relation = (
            "本轮修改前备份；对应项目路径 "
            + relative.removeprefix("build/readability/originals/")
            + "，不参与应用构建。"
        )
    elif relative.startswith(".tools/android-sdk/"):
        relation = "Android SDK 管理器提供；由 Android 构建、lint、adb 或独立模拟器按子组件使用。"
    elif relative.startswith((".tools/jdk-", ".tools/gradle-")):
        relation = "本机 JDK / Gradle 发行或其运行缓存；scripts/build-common.ps1 选择相关工具。"
    elif "/build/" in relative or relative.startswith("build/"):
        relation = "构建或测试步骤产生；实际生产任务由路径所属工程的 build.gradle、测试或本轮诊断工具决定。"
    return Role(kind, component, purpose, relation, evidence)


def entries_for(paths: dict, roles: dict[str, Role]) -> list[Entry]:
    """人工说明优先；每个实际路径输出恰好一行，不用通配符合并文件。"""
    entries = []
    for relative in sorted(paths, key=lambda path: (path != ".", path.casefold(), path)):
        kind, target = paths[relative]
        role = roles.get(relative) or inferred_role(relative, kind)
        entries.append(
            Entry(
                relative,
                kind,
                role.component,
                category_for(relative, kind),
                role.purpose,
                role.relation,
                role.evidence,
                target,
            )
        )
    return entries


HTML = r"""<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>音乐播放器 · 全量文件附录</title><style>
:root{color-scheme:light;--ink:#183244;--muted:#516776;--border:#ccd9df;--accent:#145f78}
*{box-sizing:border-box}body{margin:0;background:#f4f7f9;color:var(--ink);font:15px/1.65 system-ui,"Microsoft YaHei",sans-serif}
header,main,footer{max-width:1200px;margin:auto;padding:22px}header{padding-bottom:12px}h1{font-size:27px;margin:0 0 8px}
p{margin:6px 0}label{display:flex;flex-direction:column;gap:5px}input,select,button{font:inherit;border:1px solid var(--border);border-radius:7px;padding:9px;background:white;color:var(--ink)}
input{width:100%}button{cursor:pointer}button:disabled{opacity:.45;cursor:default}.controls{display:grid;grid-template-columns:2fr 1fr 1fr 1fr;gap:12px;margin:14px 0}
.status,.muted{color:var(--muted)}.pagination{display:flex;align-items:center;flex-wrap:wrap;gap:10px;margin:15px 0}
article{background:white;border:1px solid var(--border);border-radius:9px;padding:14px 17px;margin:9px 0}h2{font:600 15px/1.6 ui-monospace,Consolas,monospace;word-break:break-all;margin:0 0 7px}
.tags{display:flex;gap:8px;flex-wrap:wrap;color:var(--accent);font-size:13px}.tags span{background:#edf5f7;border-radius:5px;padding:1px 7px}dl{display:grid;grid-template-columns:82px 1fr;gap:5px 10px;margin:10px 0 0}dt{color:var(--muted)}dd{margin:0;overflow-wrap:anywhere}
.notice{padding:12px;border-left:3px solid var(--accent);background:#eaf2f5}a{color:var(--accent)}footer{font-size:13px;color:var(--muted)}#error{color:#a52626}
@media(max-width:760px){.controls{grid-template-columns:1fr 1fr}.search{grid-column:1/-1}header,main,footer{padding:16px}dl{grid-template-columns:65px 1fr}}
</style></head><body><header><h1>音乐播放器 · 全量文件附录</h1><p id="snapshot" class="status">正在载入本地快照…</p>
<p class="notice">目录和文件各占一项，包含隐藏条目与空目录。压缩包只列自身，目录链接不展开。说明依据明确区分人工核实与推断。页面无需联网。</p>
</header><main><div class="controls"><label class="search">路径或用途搜索<input id="query" type="search" placeholder="例如 MainActivity、歌词、.tools" autocomplete="off"></label>
<label>分类<select id="category" aria-label="分类"><option value="">全部分类</option></select></label><label>类型<select id="kind" aria-label="类型"><option value="">全部类型</option></select></label>
<label>所属组件<select id="component" aria-label="所属组件"><option value="">全部组件</option></select></label></div>
<div class="pagination"><button id="previous">上一页</button><button id="next">下一页</button><span id="page" aria-live="polite"></span>
<label>每页<select id="size" aria-label="每页"><option>50</option><option selected>100</option><option>200</option></select></label><label>跳到页<input id="jump" type="number" min="1" value="1" style="width:90px"></label><button id="go">跳转</button><button id="reset">重置筛选</button></div>
<p id="matches" class="status" aria-live="polite"></p><p id="error" role="alert"></p><section id="results" aria-label="文件条目"></section></main>
<footer><a href="PROJECT_GUIDE.md">查看主说明</a> · 修改源码或构建后运行 scripts/generate-project-inventory.py 重新生成。此页是扫描时的快照。</footer>
<script id="inventory-data" type="application/json">__PAYLOAD__</script>
<script>
'use strict';
try {
 const data=JSON.parse(document.getElementById('inventory-data').textContent), rows=data.entries;
 const byId=id=>document.getElementById(id), query=byId('query'), category=byId('category'), kind=byId('kind'), component=byId('component');
 let filtered=rows, page=1, timer;
 byId('snapshot').textContent=`扫描时间：${data.scannedAt} · 根目录：${data.root} · 共 ${rows.length.toLocaleString()} 项（${data.counts['文件']||0} 文件 / ${data.counts['目录']||0} 目录 / ${data.links} 链接）`;
 const searchable=rows.map(row=>(row.path+' '+row.purpose+' '+row.component).toLocaleLowerCase());
 for(const [select,key] of [[category,'category'],[kind,'kind'],[component,'component']]) {
   for(const value of [...new Set(rows.map(row=>row[key]))].sort()) {const option=document.createElement('option');option.value=value;option.textContent=value;select.append(option);}
 }
 function render(){
  const size=Number(byId('size').value), pages=Math.max(1,Math.ceil(filtered.length/size));page=Math.max(1,Math.min(page,pages));
  const start=(page-1)*size, fragment=document.createDocumentFragment();
  for(const row of filtered.slice(start,start+size)){
   const article=document.createElement('article'), heading=document.createElement('h2');heading.textContent=row.path;article.append(heading);
   const tags=document.createElement('div');tags.className='tags';for(const value of [row.kind,row.category,row.component]){const tag=document.createElement('span');tag.textContent=value;tags.append(tag);}article.append(tags);
   const dl=document.createElement('dl');for(const [label,value] of [['作用',row.purpose],['关联 / 来源',row.relation],['说明依据',row.evidence],['链接目标',row.target]]){if(!value)continue;const dt=document.createElement('dt'),dd=document.createElement('dd');dt.textContent=label;dd.textContent=value;dl.append(dt,dd);}article.append(dl);fragment.append(article);
  }
  byId('results').replaceChildren(fragment);byId('page').textContent=`第 ${page} / ${pages} 页`;
  byId('matches').textContent=`符合条件 ${filtered.length.toLocaleString()} 项 · 当前显示 ${filtered.length?start+1:0}–${Math.min(start+size,filtered.length)} 项`;
  byId('previous').disabled=page<=1;byId('next').disabled=page>=pages;byId('jump').value=page;byId('jump').max=pages;
 }
 function filter(){const text=query.value.trim().toLocaleLowerCase();filtered=rows.filter((row,index)=>(!text||searchable[index].includes(text))&&(!category.value||row.category===category.value)&&(!kind.value||row.kind===kind.value)&&(!component.value||row.component===component.value));page=1;render();}
 query.addEventListener('input',()=>{clearTimeout(timer);timer=setTimeout(filter,180);});for(const select of [category,kind,component])select.addEventListener('change',filter);
 byId('previous').onclick=()=>{page--;render();};byId('next').onclick=()=>{page++;render();};byId('size').onchange=()=>{page=1;render();};
 byId('go').onclick=()=>{page=Number(byId('jump').value)||1;render();};byId('jump').addEventListener('keydown',event=>{if(event.key==='Enter')byId('go').click();});
 byId('reset').onclick=()=>{query.value='';category.value='';kind.value='';component.value='';filter();};render();
}catch(error){document.getElementById('error').textContent='快照加载失败：'+error.message;}
</script></body></html>"""


def html_document(entries: list[Entry], root: Path, scanned_at: str) -> str:
    """嵌入 JSON 并转义 HTML 脚本边界；界面用 textContent 显示所有路径和说明。"""
    data = {
        "schema": 1,
        "root": str(root),
        "scannedAt": scanned_at,
        "counts": dict(Counter(entry.kind for entry in entries)),
        "links": sum(entry.kind.endswith("链接") for entry in entries),
        "entries": [asdict(entry) for entry in entries],
    }
    payload = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    for character, escaped in [
        ("&", "\\u0026"),
        ("<", "\\u003c"),
        (">", "\\u003e"),
        ("\u2028", "\\u2028"),
        ("\u2029", "\\u2029"),
    ]:
        payload = payload.replace(character, escaped)
    return HTML.replace("__PAYLOAD__", payload)


def verify_inventory(html: Path, root: Path) -> int:
    """独立扫描并核对已生成 HTML 的路径集合和重复行；变化会明确报错。"""
    source = html.read_text(encoding="utf-8")
    match = re.search(
        r'<script id="inventory-data" type="application/json">(.*?)</script>', source, re.S
    )
    if not match:
        raise ValueError("附录缺少可核对的 inventory-data")
    entries = json.loads(match.group(1))["entries"]
    actual = [entry["path"] for entry in entries]
    expected = independently_scan(root)
    if len(actual) != len(set(actual)) or set(actual) != expected:
        raise ValueError(
            "附录路径覆盖失败\n遗漏: "
            + "\n".join(sorted(expected - set(actual)))
            + "\n多余: "
            + "\n".join(sorted(set(actual) - expected))
        )
    return len(actual)


def generate(root: Path, guide: Path, output: Path, check_only: bool = False) -> int:
    """先检查说明和两次独立扫描，再原子替换附录；扫描期间的目录变化也会失败。"""
    roles = read_roles(guide)
    if check_only:
        paths = scan_paths(root)
        check_roles(paths, roles, authored_paths(root, paths))
        return verify_inventory(output, root)
    output.parent.mkdir(parents=True, exist_ok=True)
    if not output.exists():
        output.touch()  # 首次生成也必须包含附录自身。
    paths = scan_paths(root)
    check_roles(paths, roles, authored_paths(root, paths))
    independent = independently_scan(root)
    if set(paths) != independent:
        raise ValueError("两次扫描不一致；请等待构建或其他文件写入完成后重试")
    entries = entries_for(paths, roles)
    content = html_document(
        entries, root, datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")
    )
    temporary = output.with_name(output.name + ".tmp")
    try:
        temporary.write_text(content, encoding="utf-8", newline="\n")
        temporary.replace(output)
    finally:
        if temporary.exists():
            temporary.unlink()
    return verify_inventory(output, root)


def main() -> int:
    """解析命令行并打印覆盖结果；失败返回非零退出码，便于脚本和 CI 使用。"""
    # 让命令输出及重定向日志与 HTML 一样明确使用 UTF-8。
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--guide", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--check", action="store_true", help="只检查现有附录和职责索引")
    args = parser.parse_args()
    root = args.root.resolve()
    guide = args.guide or root / "docs/PROJECT_GUIDE.md"
    output = args.output or root / "docs/PROJECT_INVENTORY.html"
    try:
        count = generate(root, guide, output, args.check)
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        print(str(error), file=sys.stderr)
        return 1
    print(
        ("检查通过" if args.check else "生成并独立核对通过")
        + ": "
        + str(count)
        + " 个条目；"
        + str(output)
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
