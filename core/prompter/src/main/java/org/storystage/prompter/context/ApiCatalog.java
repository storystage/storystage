/*
 * StoryStage, based on Alice 3. See NOTICE and License.txt in the repository root.
 */
package org.storystage.prompter.context;

import org.lgna.project.annotations.ClassTemplate;
import org.lgna.project.annotations.MethodTemplate;
import org.lgna.project.annotations.Visibility;
import org.lgna.project.reflect.ClassInfoManager;
import org.lgna.project.reflect.MethodInfo;
import org.lgna.story.SAxes;
import org.lgna.story.SBillboard;
import org.lgna.story.SBiped;
import org.lgna.story.SBox;
import org.lgna.story.SCamera;
import org.lgna.story.SCone;
import org.lgna.story.SCylinder;
import org.lgna.story.SDisc;
import org.lgna.story.SFlyer;
import org.lgna.story.SGround;
import org.lgna.story.SProp;
import org.lgna.story.SQuadruped;
import org.lgna.story.SScene;
import org.lgna.story.SSlitherer;
import org.lgna.story.SSphere;
import org.lgna.story.SSwimmer;
import org.lgna.story.STextModel;
import org.lgna.story.SThing;
import org.lgna.story.STorus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The blocks the Prompter may use, as compact text for the system prompt: every PRIME_TIME
 * procedure and function of the story API classes that learners work with, with parameter names,
 * optional keyword details, and the constant values arguments can take. Built by reflection over
 * the same annotations the IDE uses for its block palette. See docs/spec/01-prompter-protocol.md
 * §2.1.
 */
public final class ApiCatalog {
  /** The classes learners meet in the gallery and scene, from most general to most specific. */
  static final List<Class<?>> ROOTS = Arrays.asList(
      SScene.class, SCamera.class, SGround.class, SProp.class, SBiped.class, SQuadruped.class, SFlyer.class,
      SSwimmer.class, SSlitherer.class, SBox.class, SSphere.class, SCylinder.class, SCone.class, SDisc.class,
      STorus.class, SBillboard.class, STextModel.class, SAxes.class);

  private ApiCatalog() {
    throw new AssertionError();
  }

  /** One method in the catalog. */
  public static final class Entry {
    public final Class<?> declaringClass;
    public final String name;
    public final List<String> parameterNames;
    public final List<Class<?>> parameterTypes;
    public final Map<String, Class<?>> details;
    public final Class<?> returnType;

    Entry(Class<?> declaringClass, String name, List<String> parameterNames, List<Class<?>> parameterTypes, Map<String, Class<?>> details, Class<?> returnType) {
      this.declaringClass = declaringClass;
      this.name = name;
      this.parameterNames = parameterNames;
      this.parameterTypes = parameterTypes;
      this.details = details;
      this.returnType = returnType;
    }

    public boolean isProcedure() {
      return this.returnType == Void.TYPE;
    }
  }

  /** All catalog entries, grouped by declaring class from SThing downwards. */
  public static List<Entry> entries() {
    List<Entry> rv = new ArrayList<>();
    for (Class<?> cls : classesInOrder()) {
      Map<Method, String[]> names = parameterNamesFor(cls);
      Method[] methods = cls.getDeclaredMethods();
      Arrays.sort(methods, Comparator.comparing(Method::getName).thenComparing(m -> m.getParameterCount()));
      for (Method m : methods) {
        if (isPrimeTime(m)) {
          rv.add(toEntry(cls, m, names.get(m)));
        }
      }
    }
    return rv;
  }

  /** The catalog as system-prompt text. */
  public static String render() {
    StringBuilder sb = new StringBuilder();
    sb.append("## Blocks you may use\n");
    sb.append("Each line is Class.block(parameters; optional details) and, for functions, -> result type.\n");
    sb.append("A class has its own blocks plus those of the classes it extends.\n");
    Class<?> current = null;
    Set<Class<?>> valueTypes = new TreeSet<>(Comparator.comparing(Class::getSimpleName));
    for (Entry e : entries()) {
      if (e.declaringClass != current) {
        current = e.declaringClass;
        sb.append("\nclass ").append(current.getSimpleName());
        Class<?> sup = current.getSuperclass();
        if (sup != null && sup != Object.class) {
          sb.append(" extends ").append(sup.getSimpleName());
        }
        sb.append('\n');
      }
      sb.append(e.isProcedure() ? "  proc " : "  func ").append(e.name).append('(');
      for (int i = 0; i < e.parameterNames.size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }
        Class<?> t = e.parameterTypes.get(i);
        sb.append(e.parameterNames.get(i)).append(": ").append(typeName(t));
        noteValueType(t, valueTypes);
      }
      if (!e.details.isEmpty()) {
        sb.append("; ");
        boolean first = true;
        for (Map.Entry<String, Class<?>> d : e.details.entrySet()) {
          if (!first) {
            sb.append(", ");
          }
          first = false;
          sb.append(d.getKey()).append("?: ").append(typeName(d.getValue()));
          noteValueType(d.getValue(), valueTypes);
        }
      }
      sb.append(')');
      if (!e.isProcedure()) {
        sb.append(" -> ").append(typeName(e.returnType));
      }
      sb.append('\n');
    }
    sb.append("\n## Values\n");
    for (Class<?> t : valueTypes) {
      List<String> constants = constantsOf(t);
      if (!constants.isEmpty()) {
        sb.append(t.getSimpleName()).append(": ").append(String.join(", ", constants)).append('\n');
      }
    }
    return sb.toString();
  }

  /** Every class from SThing down to the roots, each once, parents first. */
  static List<Class<?>> classesInOrder() {
    Set<Class<?>> ordered = new LinkedHashSet<>();
    for (Class<?> root : ROOTS) {
      List<Class<?>> chain = new ArrayList<>();
      for (Class<?> c = root; c != null && SThing.class.isAssignableFrom(c); c = c.getSuperclass()) {
        chain.add(0, c);
      }
      ordered.addAll(chain);
    }
    return new ArrayList<>(ordered);
  }

  /** True for public instance methods the block palette shows (PRIME_TIME). */
  public static boolean isPrimeTime(Method m) {
    if (!Modifier.isPublic(m.getModifiers()) || Modifier.isStatic(m.getModifiers()) || m.isBridge() || m.isSynthetic()) {
      return false;
    }
    MethodTemplate template = m.getAnnotation(MethodTemplate.class);
    return template != null && template.visibility() == Visibility.PRIME_TIME;
  }

  /** The catalog entry for a method, with parameter names from Alice's class info when known. */
  public static Entry entryOf(Method m) {
    return toEntry(m.getDeclaringClass(), m, parameterNamesFor(m.getDeclaringClass()).get(m));
  }

  private static Entry toEntry(Class<?> cls, Method m, String[] knownNames) {
    Class<?>[] types = m.getParameterTypes();
    List<String> names = new ArrayList<>();
    List<Class<?>> required = new ArrayList<>();
    Map<String, Class<?>> details = new LinkedHashMap<>();
    for (int i = 0; i < types.length; i++) {
      Class<?> t = types[i];
      boolean isDetails = m.isVarArgs() && i == types.length - 1 && t.isArray();
      if (isDetails) {
        details.putAll(keywordDetails(t.getComponentType()));
      } else {
        required.add(t);
        names.add(knownNames != null && i < knownNames.length ? knownNames[i] : guessName(t));
      }
    }
    return new Entry(cls, m.getName(), names, required, details, m.getReturnType());
  }

  /** Keyword arguments allowed for a Detail interface: the static factory methods of its keyword class. */
  static Map<String, Class<?>> keywordDetails(Class<?> detailInterface) {
    Map<String, Class<?>> rv = new LinkedHashMap<>();
    ClassTemplate template = detailInterface.getAnnotation(ClassTemplate.class);
    if (template == null || template.keywordFactoryCls() == Void.class) {
      return rv;
    }
    Method[] factories = template.keywordFactoryCls().getMethods();
    Arrays.sort(factories, Comparator.comparing(Method::getName));
    for (Method f : factories) {
      if (Modifier.isStatic(f.getModifiers()) && f.getParameterCount() == 1 && detailInterface.isAssignableFrom(f.getReturnType())) {
        rv.put(f.getName(), f.getParameterTypes()[0]);
      }
    }
    return rv;
  }

  private static Map<Method, String[]> parameterNamesFor(Class<?> cls) {
    Map<Method, String[]> rv = new HashMap<>();
    List<MethodInfo> infos = ClassInfoManager.getMethodInfos(cls);
    if (infos != null) {
      for (MethodInfo info : infos) {
        Method m = info.getMthd();
        if (m != null) {
          rv.put(m, info.getParameterNames());
        }
      }
    }
    return rv;
  }

  /** A readable name when Alice's class info has none: MoveDirection becomes direction. */
  static String guessName(Class<?> t) {
    String s = t.getSimpleName();
    if (s.endsWith("Direction")) {
      return "direction";
    }
    if (Number.class.isAssignableFrom(t) || t.isPrimitive()) {
      return "amount";
    }
    if (t == String.class) {
      return "text";
    }
    if (SThing.class.isAssignableFrom(t)) {
      return "target";
    }
    return Character.toLowerCase(s.charAt(0)) + s.substring(1);
  }

  static String typeName(Class<?> t) {
    if (t.isArray()) {
      return typeName(t.getComponentType()) + "[]";
    }
    if (t == Double.class || t == Double.TYPE || t == Number.class || t == Float.class) {
      return "Number";
    }
    if (t == Integer.class || t == Integer.TYPE) {
      return "WholeNumber";
    }
    if (t == Boolean.class || t == Boolean.TYPE) {
      return "Boolean";
    }
    if (t == String.class) {
      return "Text";
    }
    return t.getSimpleName();
  }

  private static void noteValueType(Class<?> t, Set<Class<?>> valueTypes) {
    Class<?> c = t.isArray() ? t.getComponentType() : t;
    if (c.isEnum() || !constantsOf(c).isEmpty()) {
      valueTypes.add(c);
    }
  }

  /** Enum constants, or public static final fields of a class's own type (Color.RED). */
  static List<String> constantsOf(Class<?> t) {
    List<String> rv = new ArrayList<>();
    if (t.isPrimitive() || t.getPackage() == null || !t.getPackage().getName().startsWith("org.lgna.story")) {
      return rv;
    }
    if (t.isEnum()) {
      for (Object c : t.getEnumConstants()) {
        rv.add(((Enum<?>) c).name());
      }
      return rv;
    }
    for (Field f : t.getFields()) {
      int mod = f.getModifiers();
      if (Modifier.isStatic(mod) && Modifier.isFinal(mod) && t.isAssignableFrom(f.getType())) {
        rv.add(f.getName());
      }
    }
    return rv;
  }
}
