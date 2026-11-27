"""Summarize only complete A/B pairs. All rows, including retries, stay in results.csv."""
import csv
from collections import defaultdict
from pathlib import Path
from statistics import median
import sys

for arg in sys.argv[1:]:
    folder=Path(arg)
    with (folder/"results.csv").open(encoding="utf-8-sig",newline="") as stream:
        rows=list(csv.DictReader(stream))
    groups=defaultdict(list)
    for row in rows:
        if row["status"]=="ok" and row["paired"]=="1":
            groups[int(row["connections"]),row["implementation"]].append(row)
    print(folder)
    print("clients backend pairs throughput_rps range_rps rtt_p99_ms range_p99_ms")
    for key,values in sorted(groups.items()):
        rps=[float(row["requests_per_s"]) for row in values]
        p99=[float(row["p99_us"])/1000 for row in values]
        print(*key, len(values), round(median(rps)), (round(min(rps)),round(max(rps))),
              round(median(p99),2), (round(min(p99),2), round(max(p99),2)))
    failures=[row for row in rows if row["status"]!="ok"]
    print("failed",len(failures),"full-pair requests",sum(int(r["samples"]) for v in groups.values() for r in v))
