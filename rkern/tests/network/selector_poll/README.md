# Selector poll validation

Compile `SelectorPollBenchmark.java` and `rkern/docs/jproring_selector_transport_20261120/SelectorTransportTest.java` with Java 21, `--enable-preview --release 21`, and the server classes and JARs on the classpath. Give the runner the same compiled fixture classes and dependency JARs for both production revisions. Put the baseline or candidate server classes after the fixture classes. Use `:` between classpath entries on Linux and `;` on Windows.

```bash
python3 rkern/tests/network/selector_poll/run.py \
  --baseline-cp "$fixture_classes:$baseline_classes:$jars" \
  --candidate-cp "$fixture_classes:$candidate_classes:$jars" \
  --output /tmp/selector-poll-matrix --profile matrix
```

Use `--profile smoke` for an initial connection and sequence check. `--profile matrix` covers 1, 16, 1,024 and 5,000 sockets, 256 B and 4,096 B per direction, window 1 and window 8, fixed offered load and closed-loop saturation, and paired 60-second mixed active/idle runs. `--linux-cpus SERVER,CLIENT` optionally pins the two processes to separate CPUs. `--polls` requires the test-only selector probe classes to be placed before the respective server classes. Keep those counter-enabled runs separate from the timing comparison.

The fixture uses production CIO, ciphering, parsing and SEND on an IPv4 loopback listener with two owners. A synthetic callback sends a checked response to each sequence. Every opened peer checks the initial key and every measured exchange checks the ciphertext. There is no database, game-world simulation or remote host. Active peers close after reading their outstanding replies. Their sockets use abortive close to avoid exhausting Windows ephemeral ports across repeated 5,000-socket cohorts. Run the transport correctness fixtures separately for graceful shutdown and partial writes.

`results.jsonl` records wire RTT separately from scheduled-arrival-to-reply time. For the fixed-rate profiles it also records traffic offered, completed before the cutoff, drained after the cutoff, skipped because the per-client window was full, missed by the client scheduler, or incomplete. The CPU and allocation counters come from the server process and its network owner threads. Each result includes timer progress on both owners, the number and reply latency of new connections during load, minimum and maximum replies across active sockets, and whether all send/receive leases were returned. Compare repeated measurements under the same Java, CPU and workload settings.
