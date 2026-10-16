/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.tools;

import org.alice.stageide.modelresource.EnumConstantResourceKey;
import org.alice.stageide.modelresource.ResourceKey;
import org.lgna.story.resourceutilities.GalleryResourceTreeNode;
import org.lgna.story.resourceutilities.StorytellingResourcesTreeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The models in the installed gallery, each with a stable id such as
 * {@code Quadruped/Cat/AbyssinianCat}. Read from the same gallery tree the IDE's gallery browser
 * uses. See docs/spec/01-prompter-protocol.md §2.2.
 */
public final class GalleryCatalog {
  private final Map<String, EnumConstantResourceKey> byId;

  private GalleryCatalog(Map<String, EnumConstantResourceKey> byId) {
    this.byId = byId;
  }

  /** The gallery as installed with this copy of StoryStage. Call on the Swing thread. */
  public static GalleryCatalog load() {
    Map<String, EnumConstantResourceKey> map = new LinkedHashMap<>();
    try {
      collect(StorytellingResourcesTreeUtils.INSTANCE.getGalleryTree(), new ArrayList<>(), map);
    } catch (RuntimeException e) {
      // No gallery (for example in a test run): the catalog is simply empty.
    }
    return new GalleryCatalog(map);
  }

  /** A catalog from explicit entries, for tests. */
  static GalleryCatalog of(Map<String, EnumConstantResourceKey> entries) {
    return new GalleryCatalog(new LinkedHashMap<>(entries));
  }

  private static void collect(GalleryResourceTreeNode node, List<String> path, Map<String, EnumConstantResourceKey> out) {
    if (node == null) {
      return;
    }
    ResourceKey key = node.getResourceKey();
    List<String> here = path;
    String name = node.getName();
    if (name != null && !name.isEmpty() && node.getParent() != null) {
      here = new ArrayList<>(path);
      here.add(name.replace('/', '-'));
    }
    if (key instanceof EnumConstantResourceKey enumKey) {
      String id = String.join("/", here);
      if (id.isEmpty()) {
        id = enumKey.getInternalName();
      }
      out.putIfAbsent(id, enumKey);
    }
    for (GalleryResourceTreeNode child : node.childrenList()) {
      collect(child, here, out);
    }
  }

  public EnumConstantResourceKey get(String id) {
    return this.byId.get(id);
  }

  public List<String> ids() {
    return Collections.unmodifiableList(new ArrayList<>(this.byId.keySet()));
  }

  public boolean isEmpty() {
    return this.byId.isEmpty();
  }

  /** The catalog as system-prompt text: one gallery id per line. */
  public String render() {
    StringBuilder sb = new StringBuilder("## Gallery models (use these ids with add_model)\n");
    for (String id : this.byId.keySet()) {
      sb.append(id).append('\n');
    }
    return sb.toString();
  }
}
