/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.tools;

import edu.cmu.cs.dennisc.java.util.logging.Logger;
import org.alice.stageide.modelresource.ClassResourceKey;
import org.alice.stageide.modelresource.EnumConstantResourceKey;
import org.alice.stageide.modelresource.ResourceKey;
import org.lgna.story.resources.ModelResource;
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
    GalleryResourceTreeNode root;
    try {
      root = StorytellingResourcesTreeUtils.INSTANCE.getGalleryTree();
    } catch (RuntimeException e) {
      // No gallery (for example in a unit test run): the catalog is simply empty.
      Logger.throwable(e, "StoryStage Prompter: no gallery");
      return new GalleryCatalog(map);
    }
    if (root != null) {
      // The root has no resource of its own (asking for its key fails), as in TreeUtilities.
      for (GalleryResourceTreeNode child : root.childrenList()) {
        collect(child, new ArrayList<>(), map);
      }
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
    ResourceKey key;
    try {
      key = node.getResourceKey();
    } catch (RuntimeException e) {
      // One unusual node must not hide the rest of the gallery.
      Logger.throwable(e, "StoryStage Prompter: skipped gallery node", node.getName());
      key = null;
    }
    List<String> here = path;
    String name = node.getName();
    if (name != null && !name.isEmpty()) {
      here = new ArrayList<>(path);
      here.add(name.replace('/', '-'));
    }
    if (key instanceof EnumConstantResourceKey enumKey) {
      String id = String.join("/", here);
      if (id.isEmpty()) {
        id = enumKey.getInternalName();
      }
      out.putIfAbsent(id, enumKey);
    } else if (key instanceof ClassResourceKey classKey && classKey.getModelResourceCls().isEnum()) {
      // Most gallery leaves are resource classes; each enum constant is one model (spec 01 §2.2).
      addConstants(classKey.getModelResourceCls(), String.join("/", here), out);
    }
    for (GalleryResourceTreeNode child : node.childrenList()) {
      collect(child, here, out);
    }
  }

  @SuppressWarnings("unchecked")
  static void addConstants(Class<? extends ModelResource> cls, String path, Map<String, EnumConstantResourceKey> out) {
    ModelResource[] constants = cls.getEnumConstants();
    String base = path.isEmpty() ? cls.getSimpleName().replace("Resource", "") : path;
    for (ModelResource constant : constants) {
      Enum<? extends ModelResource> e = (Enum<? extends ModelResource>) constant;
      String id = constants.length == 1 ? base : base + "/" + e.name();
      out.putIfAbsent(id, new EnumConstantResourceKey(e));
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

  /**
   * The catalog as system-prompt text. Variants of one model share a line, as in
   * {@code Flyer/Penguin/{ADULT|BABY}}, which keeps the prompt (and every request's cost) small.
   */
  public String render() {
    Map<String, List<String>> byParent = new LinkedHashMap<>();
    for (String id : this.byId.keySet()) {
      int slash = id.lastIndexOf('/');
      String parent = slash > 0 ? id.substring(0, slash) : "";
      byParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(id);
    }
    StringBuilder sb = new StringBuilder("## Gallery models (ids for add_model)\n");
    sb.append("A line like Flyer/Penguin/{ADULT|BABY} means the ids Flyer/Penguin/ADULT and Flyer/Penguin/BABY.\n");
    for (Map.Entry<String, List<String>> e : byParent.entrySet()) {
      List<String> ids = e.getValue();
      if (ids.size() == 1 || e.getKey().isEmpty()) {
        for (String id : ids) {
          sb.append(id).append('\n');
        }
      } else {
        StringBuilder names = new StringBuilder();
        for (String id : ids) {
          names.append(names.length() == 0 ? "" : "|").append(id.substring(e.getKey().length() + 1));
        }
        sb.append(e.getKey()).append("/{").append(names).append("}\n");
      }
    }
    return sb.toString();
  }
}
