/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.context;

import org.lgna.project.ast.Node;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Short, stable ids for AST nodes, so Claude can refer to a block ("insert into block a1f3").
 * An id is the start of the node's UUID, lengthened only when two nodes would share it. See
 * docs/spec/01-prompter-protocol.md §2.3.
 */
public final class NodeIds {
  private static final int MIN_LENGTH = 4;
  private final Map<String, Node> byId = new HashMap<>();
  private final Map<UUID, String> byUuid = new HashMap<>();

  /** The short id for a node, assigning one if needed. */
  public synchronized String idOf(Node node) {
    UUID uuid = node.getId();
    String existing = this.byUuid.get(uuid);
    if (existing != null) {
      return existing;
    }
    String hex = uuid.toString().replace("-", "");
    for (int length = MIN_LENGTH; length <= hex.length(); length++) {
      String candidate = hex.substring(0, length);
      Node other = this.byId.get(candidate);
      if (other == null) {
        this.byId.put(candidate, node);
        this.byUuid.put(uuid, candidate);
        return candidate;
      }
    }
    // Two different nodes with the same UUID: use the full UUID text.
    String full = uuid.toString();
    this.byId.put(full, node);
    this.byUuid.put(uuid, full);
    return full;
  }

  /** The node with an id given earlier by {@link #idOf}, or null. */
  public synchronized Node lookup(String id) {
    if (id == null) {
      return null;
    }
    String key = id.trim().toLowerCase();
    if (key.startsWith("#")) {
      key = key.substring(1);
    }
    return this.byId.get(key);
  }

  public synchronized int size() {
    return this.byId.size();
  }
}
