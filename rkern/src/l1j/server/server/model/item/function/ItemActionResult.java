package l1j.server.server.model.item.function;

/** COMPLETE runs the legacy delay/charge tail; STOP preserves an early return.
 * Neither value implies that a pending CacheD operation has committed.
 */
enum ItemActionResult { COMPLETE, STOP }
